package com.onyx.interactors.transaction.impl

import com.onyx.exception.TransactionException
import com.onyx.extension.common.decompressLz77
import java.io.IOException
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Comparator
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MemoryMappedWalConcurrencyTest {

    @Test
    fun `gathering append and growth complete during mapped force and close preserves every byte`() {
        val path = Files.createTempFile("onyx-wal-concurrent-channel-force", ".wal")
        val executor = Executors.newFixedThreadPool(2)
        val forceStarted = CountDownLatch(1)
        val releaseForce = CountDownLatch(1)
        val forcedSegments = CopyOnWriteArrayList<MemorySegment>()
        val firstForce = AtomicBoolean(true)
        val channel = MemoryMappedTransactionFileChannel(
            FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE),
            reserveBytes = 1L,
            mappedForce = { segment ->
                forcedSegments.add(segment)
                if (firstForce.getAndSet(false)) {
                    forceStarted.countDown()
                    check(releaseForce.await(10, TimeUnit.SECONDS)) { "Mapped force was never released" }
                }
                segment.force()
            }
        )

        try {
            val prefix = byteArrayOf(1, 2, 3)
            val header = byteArrayOf(4, 5)
            val payload = ByteArray(Math.toIntExact(Files.size(path)) + 17) { (it * 31).toByte() }
            channel.write(ByteBuffer.wrap(prefix))
            val forcing = executor.submit { channel.forceInBackground() }
            assertTrue(forceStarted.await(5, TimeUnit.SECONDS))

            val appending = executor.submit<Long> {
                channel.write(arrayOf(ByteBuffer.wrap(header), ByteBuffer.wrap(payload)))
            }
            assertEquals((header.size + payload.size).toLong(), appending.get(5, TimeUnit.SECONDS))
            val expected = prefix + header + payload
            assertEquals(expected.size.toLong(), channel.position())
            assertEquals(expected.size.toLong(), channel.size())
            assertTrue(Files.size(path) > forcedSegments.single().byteSize())
            assertTrue(forcedSegments.single().scope().isAlive)

            val closeStarted = CountDownLatch(1)
            val closing = executor.submit {
                closeStarted.countDown()
                channel.close()
            }
            assertTrue(closeStarted.await(5, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { closing.get(100, TimeUnit.MILLISECONDS) }

            releaseForce.countDown()
            forcing.get(5, TimeUnit.SECONDS)
            closing.get(5, TimeUnit.SECONDS)

            assertFalse(channel.isOpen)
            assertTrue(forcedSegments.none { it.scope().isAlive })
            assertEquals(expected.size.toLong(), Files.size(path))
            assertContentEquals(expected, Files.readAllBytes(path))
        } finally {
            releaseForce.countDown()
            runCatching { channel.close() }
            executor.shutdownNow()
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `periodic force allows writes and ready rotation while close waits and reports its failure`() {
        val directory = Files.createTempDirectory("onyx-wal-concurrent-store-force")
        val executor = Executors.newSingleThreadExecutor()
        val forceStarted = CountDownLatch(1)
        val releaseForce = CountDownLatch(1)
        val firstForce = AtomicBoolean(true)
        val injectedFailure = IOException("Injected periodic WAL force failure")
        val store = object : MemoryMappedTransactionStore(directory.toString()) {
            override val maxJournalSize = 128L

            override fun forceWalFile(walFile: FileChannel) {
                if (firstForce.getAndSet(false)) {
                    forceStarted.countDown()
                    check(releaseForce.await(10, TimeUnit.SECONDS)) { "Periodic force was never released" }
                    throw injectedFailure
                }
                super.forceWalFile(walFile)
            }
        }

        try {
            val original = store.getTransactionFile()
            assertTrue(forceStarted.await(5, TimeUnit.SECONDS))
            assertTrue(waitUntil { store.isNextWalPrepared() })

            val originalBytes = ByteArray(256) { 7 }
            val nextBytes = byteArrayOf(8, 9, 10)
            val appendingAndRotating = executor.submit<FileChannel> {
                assertSame(original, store.getTransactionFile())
                original.write(ByteBuffer.wrap(originalBytes))
                store.getTransactionFile().also { next ->
                    assertTrue(next !== original)
                    next.write(ByteBuffer.wrap(nextBytes))
                }
            }
            val next = appendingAndRotating.get(5, TimeUnit.SECONDS)
            assertEquals(nextBytes.size.toLong(), next.size())

            val closeStarted = CountDownLatch(1)
            val closing = executor.submit {
                closeStarted.countDown()
                store.close()
            }
            assertTrue(closeStarted.await(5, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { closing.get(100, TimeUnit.MILLISECONDS) }

            releaseForce.countDown()
            val failure = assertFailsWith<ExecutionException> { closing.get(5, TimeUnit.SECONDS) }
            assertSame(injectedFailure, assertIs<TransactionException>(failure.cause).cause)
            assertFalse(original.isOpen)
            assertFalse(next.isOpen)
            assertContentEquals(originalBytes, Files.readAllBytes(directory.resolve("wal/0.wal")).decompressLz77())
            assertContentEquals(nextBytes, Files.readAllBytes(directory.resolve("wal/1.wal")))
        } finally {
            releaseForce.countDown()
            runCatching { store.close() }
            executor.shutdownNow()
            deleteDirectory(directory)
        }
    }

    @Test
    fun `slow preparation allows appends past rotation threshold and close discards pending WAL`() {
        val directory = Files.createTempDirectory("onyx-wal-concurrent-preparation")
        val executor = Executors.newSingleThreadExecutor()
        val preparationStarted = CountDownLatch(1)
        val releasePreparation = CountDownLatch(1)
        val store = object : MemoryMappedTransactionStore(directory.toString()) {
            override val maxJournalSize = 128L

            override fun prepareWalFile(walPath: Path): FileChannel {
                val prepared = super.prepareWalFile(walPath)
                preparationStarted.countDown()
                check(releasePreparation.await(10, TimeUnit.SECONDS)) { "WAL preparation was never released" }
                return prepared
            }
        }

        try {
            val original = store.getTransactionFile()
            assertTrue(preparationStarted.await(5, TimeUnit.SECONDS))
            assertFalse(store.isNextWalPrepared())
            val expected = ByteArray(768) { (it * 13).toByte() }

            val appending = executor.submit<Long> {
                repeat(3) { index ->
                    val current = store.getTransactionFile()
                    assertSame(original, current)
                    current.write(ByteBuffer.wrap(expected, index * 256, 256))
                }
                assertSame(original, store.getTransactionFile())
                original.size()
            }
            assertEquals(expected.size.toLong(), appending.get(5, TimeUnit.SECONDS))
            val walDirectory = directory.resolve("wal")
            assertEquals(listOf("0.wal"), walDirectory.toFile().listWalFiles().map { it.file.name })
            assertTrue(Files.list(walDirectory).use { files ->
                files.anyMatch { it.fileName.toString().endsWith(".pending") }
            })

            val closeStarted = CountDownLatch(1)
            val closing = executor.submit {
                closeStarted.countDown()
                store.close()
            }
            assertTrue(closeStarted.await(5, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { closing.get(100, TimeUnit.MILLISECONDS) }
            releasePreparation.countDown()
            closing.get(5, TimeUnit.SECONDS)

            assertFalse(original.isOpen)
            assertEquals(listOf("0.wal"), Files.list(walDirectory).use { files ->
                files.map { it.fileName.toString() }.toList()
            })
            assertContentEquals(expected, Files.readAllBytes(walDirectory.resolve("0.wal")).decompressLz77())
        } finally {
            releasePreparation.countDown()
            runCatching { store.close() }
            executor.shutdownNow()
            deleteDirectory(directory)
        }
    }

    @Test
    fun `preparation failure is reported by subsequent WAL requests and close after pending file cleanup`() {
        val directory = Files.createTempDirectory("onyx-wal-preparation-failure")
        val preparationStarted = CountDownLatch(1)
        val releasePreparation = CountDownLatch(1)
        val injectedFailure = IOException("Injected WAL preparation I/O failure")
        val pendingPath = directory.resolve("wal/.onyx-next-1.pending")
        val partialReservation = byteArrayOf(99, 98, 97)
        val store = object : MemoryMappedTransactionStore(directory.toString()) {
            override val maxJournalSize = 128L

            override fun prepareWalFile(walPath: Path): FileChannel {
                Files.write(walPath, partialReservation)
                preparationStarted.countDown()
                check(releasePreparation.await(10, TimeUnit.SECONDS)) { "WAL preparation failure was never released" }
                throw injectedFailure
            }
        }

        try {
            val original = store.getTransactionFile()
            assertTrue(preparationStarted.await(5, TimeUnit.SECONDS))
            assertContentEquals(partialReservation, Files.readAllBytes(pendingPath))
            val expected = walRecord(byteArrayOf(1, 2, 3, 4))
            original.write(ByteBuffer.wrap(expected))
            releasePreparation.countDown()

            var observedFailure: TransactionException? = null
            assertTrue(waitUntil {
                try {
                    store.getTransactionFile()
                    false
                } catch (failure: TransactionException) {
                    observedFailure = failure
                    true
                }
            })
            assertSame(injectedFailure, observedFailure?.cause)
            assertSame(injectedFailure, assertFailsWith<TransactionException> { store.getTransactionFile() }.cause)
            assertSame(injectedFailure, assertFailsWith<TransactionException> { store.close() }.cause)

            assertFalse(original.isOpen)
            assertFalse(Files.exists(pendingPath))
            assertContentEquals(expected, Files.readAllBytes(directory.resolve("wal/0.wal")))
            assertEquals(listOf("0.wal"), Files.list(directory.resolve("wal")).use { files ->
                files.map { it.fileName.toString() }.toList()
            })
        } finally {
            releasePreparation.countDown()
            runCatching { store.close() }
            deleteDirectory(directory)
        }
    }

    @Test
    fun `reopen removes abandoned prepared files while preserving actual WAL records`() {
        val directory = Files.createTempDirectory("onyx-wal-abandoned-preparation")
        val walDirectory = Files.createDirectories(directory.resolve("wal"))
        val walPath = walDirectory.resolve("47.wal")
        val abandonedNext = walDirectory.resolve(".onyx-next-48.pending")
        val abandonedLater = walDirectory.resolve(".onyx-next-99.pending")
        val first = walRecord(byteArrayOf(1, 2, 3))
        val second = walRecord(byteArrayOf(4, 5, 6))
        val appended = walRecord(byteArrayOf(7, 8, 9))
        Files.write(walPath, first + second + ByteArray(64))
        Files.write(abandonedNext, byteArrayOf(99, 98, 97))
        Files.write(abandonedLater, byteArrayOf(96, 95, 94))
        val store = object : MemoryMappedTransactionStore(directory.toString()) {
            override val maxJournalSize = 128L
        }

        try {
            assertEquals(listOf("47.wal"), walDirectory.toFile().listWalFiles().map { it.file.name })
            val current = store.getTransactionFile()
            assertEquals((first.size + second.size).toLong(), current.position())
            assertEquals(current.position(), current.size())
            assertFalse(Files.exists(abandonedLater))
            current.write(ByteBuffer.wrap(appended))
            store.close()

            assertFalse(current.isOpen)
            assertFalse(Files.exists(abandonedNext))
            assertContentEquals(first + second + appended, Files.readAllBytes(walPath))
            assertEquals(listOf("47.wal"), Files.list(walDirectory).use { files ->
                files.map { it.fileName.toString() }.toList()
            })
        } finally {
            runCatching { store.close() }
            deleteDirectory(directory)
        }
    }

    private fun walRecord(payload: ByteArray): ByteArray =
        ByteBuffer.allocate(5 + payload.size)
            .put(1.toByte())
            .putInt(payload.size)
            .put(payload)
            .array()

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(1)
        }
        return condition()
    }

    private fun deleteDirectory(path: Path) {
        Files.walk(path).use { files ->
            files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
