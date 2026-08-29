package transaction

import com.onyx.diskmap.store.StoreType
import com.onyx.exception.TransactionException
import com.onyx.extension.common.decompressLz77
import com.onyx.interactors.transaction.TransactionStore
import com.onyx.interactors.transaction.impl.DefaultTransactionStore
import com.onyx.interactors.transaction.impl.MemoryMappedTransactionStore
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import entities.AllAttributeEntity
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryMappedTransactionStoreTest {

    @Test
    fun memoryMappedStoreTypeRebuildsTransactionStore() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-context")
        val context = InspectableSchemaContext(
            "memory-mapped-transaction-${System.nanoTime()}",
            tempDirectory.toString()
        )

        try {
            assertEquals(StoreType.MEMORY_MAPPED_FILE, context.storeType)
            assertTrue(context.currentTransactionStore() is MemoryMappedTransactionStore)

            context.storeType = StoreType.FILE

            assertTrue(context.currentTransactionStore() is DefaultTransactionStore)

            context.storeType = StoreType.MEMORY_MAPPED_FILE

            assertTrue(context.currentTransactionStore() is MemoryMappedTransactionStore)
        } finally {
            context.shutdown()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun transactionWalUsesWholeFileMappingAndTruncatesOnClose() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-whole-file")
        val transactionStore = MemoryMappedTransactionStore(tempDirectory.toString())
        val walPath = tempDirectory.resolve("wal").resolve("0.wal")

        try {
            val transactionFile = transactionStore.getTransactionFile()
            transactionFile.write(ByteBuffer.wrap(ByteArray(128) { 1 }))

            assertTrue(Files.size(walPath) > 128L)

            transactionFile.position(0)
            val walRead = ByteBuffer.allocate(128)
            assertEquals(128, transactionFile.read(walRead))
            assertContentEquals(ByteArray(128) { 1 }, walRead.array())

            transactionFile.force(false)

            transactionFile.position(0)
            transactionFile.write(ByteBuffer.wrap(ByteArray(128) { 3 }))

            transactionStore.close()

            assertEquals(128L, Files.size(walPath))
            assertContentEquals(
                ByteArray(128) { 3 },
                Files.readAllBytes(walPath)
            )
        } finally {
            transactionStore.close()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun rotatingWalFileClosesWholeFileMappings() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-rotation")
        val transactionStore = SmallJournalMemoryMappedTransactionStore(tempDirectory.toString())

        try {
            val transactionFile = transactionStore.getTransactionFile()
            transactionFile.write(ByteBuffer.wrap(ByteArray(256) { 3 }))

            val rotatedTransactionFile = transactionStore.getTransactionFile()

            assertTrue(transactionFile !== rotatedTransactionFile)
            transactionStore.close()

            assertFalse(transactionFile.isOpen)
            assertFalse(rotatedTransactionFile.isOpen)
            assertContentEquals(
                ByteArray(256) { 3 },
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("0.wal")).decompressLz77()
            )
        } finally {
            transactionStore.close()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun truncatingWalReplacesWholeFileMappingAndAllowsRegrowth() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-truncate")
        val transactionStore = MemoryMappedTransactionStore(tempDirectory.toString())
        val walPath = tempDirectory.resolve("wal").resolve("0.wal")

        try {
            val transactionFile = transactionStore.getTransactionFile()
            transactionFile.write(ByteBuffer.wrap(ByteArray(256) { 1 }))
            transactionFile.truncate(64)
            assertEquals(64L, transactionFile.size())

            transactionFile.position(64)
            transactionFile.write(ByteBuffer.wrap(ByteArray(16) { 2 }))
            assertEquals(80L, transactionFile.size())

            transactionStore.close()

            assertEquals(80L, Files.size(walPath))
            assertContentEquals(
                ByteArray(64) { 1 } + ByteArray(16) { 2 },
                Files.readAllBytes(walPath)
            )
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun toppedOffWalIsCompressedWhenMemoryMappedStoreCloses() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-close-full")
        val walBytes = ByteArray(256) { 10 }
        val transactionStore = SmallJournalMemoryMappedTransactionStore(tempDirectory.toString())

        try {
            transactionStore.getTransactionFile().write(ByteBuffer.wrap(walBytes))
            transactionStore.close()

            assertContentEquals(
                walBytes,
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("0.wal")).decompressLz77()
            )

            val reopenedStore = SmallJournalMemoryMappedTransactionStore(tempDirectory.toString())
            try {
                reopenedStore.getTransactionFile().write(ByteBuffer.wrap(byteArrayOf(11)))
            } finally {
                reopenedStore.close()
            }
            assertContentEquals(
                byteArrayOf(11),
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("1.wal"))
            )
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun reopeningPaddedActiveWalResumesAtLogicalTransactionEnd() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-reopen-padding")
        val walDirectory = Files.createDirectories(tempDirectory.resolve("wal"))
        val walPath = walDirectory.resolve("0.wal")
        val firstRecord = walRecord(ByteArray(31) { 3 })
        val secondRecord = walRecord(ByteArray(17) { 4 })
        val mappedCapacity = 8 * 1024 * 1024
        val paddedWal = ByteArray(mappedCapacity)
        firstRecord.copyInto(paddedWal)
        Files.write(walPath, paddedWal)
        val transactionStore = MemoryMappedTransactionStore(tempDirectory.toString())

        try {
            val transactionFile = transactionStore.getTransactionFile()

            assertEquals(firstRecord.size.toLong(), transactionFile.position())
            transactionFile.write(ByteBuffer.wrap(secondRecord))
            transactionStore.close()

            assertContentEquals(firstRecord + secondRecord, Files.readAllBytes(walPath))
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun reopeningNormalizesPaddedSealedWalBeforeCompression() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-sealed-padding")
        val walDirectory = Files.createDirectories(tempDirectory.resolve("wal"))
        val sealedRecord = walRecord(ByteArray(23) { 5 })
        val currentRecord = walRecord(ByteArray(19) { 6 })
        val paddedSealedWal = ByteArray(4 * 1024 * 1024)
        sealedRecord.copyInto(paddedSealedWal)
        Files.write(walDirectory.resolve("0.wal"), paddedSealedWal)
        Files.write(walDirectory.resolve("1.wal"), currentRecord)
        val transactionStore = MemoryMappedTransactionStore(tempDirectory.toString())

        try {
            transactionStore.getTransactionFile()
            transactionStore.close()

            assertContentEquals(
                sealedRecord,
                Files.readAllBytes(walDirectory.resolve("0.wal")).decompressLz77()
            )
            assertContentEquals(currentRecord, Files.readAllBytes(walDirectory.resolve("1.wal")))
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun reopeningDropsIncompleteFinalTransactionBeforeAppending() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-incomplete-tail")
        val walDirectory = Files.createDirectories(tempDirectory.resolve("wal"))
        val walPath = walDirectory.resolve("0.wal")
        val completeRecord = walRecord(ByteArray(13) { 7 })
        val interruptedRecord = ByteBuffer.allocate(8)
            .put(1.toByte())
            .putInt(20)
            .put(byteArrayOf(8, 8, 8))
            .array()
        val appendedRecord = walRecord(ByteArray(11) { 9 })
        Files.write(walPath, completeRecord + interruptedRecord)
        val transactionStore = MemoryMappedTransactionStore(tempDirectory.toString())

        try {
            val transactionFile = transactionStore.getTransactionFile()

            assertEquals(completeRecord.size.toLong(), transactionFile.position())
            transactionFile.write(ByteBuffer.wrap(appendedRecord))
            transactionStore.close()

            assertContentEquals(completeRecord + appendedRecord, Files.readAllBytes(walPath))
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun reopeningRefusesToDiscardTransactionsAfterInternalPadding() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-internal-padding")
        val walDirectory = Files.createDirectories(tempDirectory.resolve("wal"))
        val walPath = walDirectory.resolve("0.wal")
        val firstRecord = walRecord(ByteArray(29) { 10 })
        val laterRecord = walRecord(ByteArray(27) { 11 })
        val laterRecordPosition = 128 * 1024
        val malformedWal = ByteArray(laterRecordPosition + laterRecord.size)
        firstRecord.copyInto(malformedWal)
        laterRecord.copyInto(malformedWal, laterRecordPosition)
        Files.write(walPath, malformedWal)
        val transactionStore = MemoryMappedTransactionStore(tempDirectory.toString())

        try {
            val failure = assertFailsWith<TransactionException> {
                transactionStore.getTransactionFile()
            }

            assertTrue(failure.cause?.message.orEmpty().contains("non-zero data after padding"))
            assertContentEquals(malformedWal, Files.readAllBytes(walPath))
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun rotatingWalFileIsSealedAndFinalizedWithoutBlockingTheWriter() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-async-rotation")
        val finalizationStarted = CountDownLatch(1)
        val allowFinalization = CountDownLatch(1)
        val rotationExecutor = Executors.newSingleThreadExecutor()
        val transactionStore = object : SmallJournalMemoryMappedTransactionStore(tempDirectory.toString()) {
            override fun finalizeWalFile(walFile: FileChannel) {
                finalizationStarted.countDown()
                check(allowFinalization.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to finalize the rotated WAL file"
                }
                super.finalizeWalFile(walFile)
            }
        }

        try {
            val firstWalFile = transactionStore.getTransactionFile()
            firstWalFile.write(ByteBuffer.wrap(ByteArray(256) { 4 }))

            val rotation = rotationExecutor.submit<FileChannel> {
                transactionStore.getTransactionFile()
            }
            val secondWalFile = rotation.get(5, TimeUnit.SECONDS)

            assertTrue(firstWalFile !== secondWalFile)
            assertTrue(finalizationStarted.await(5, TimeUnit.SECONDS))
            assertTrue(firstWalFile.isOpen)
            assertFailsWith<ClosedChannelException> {
                firstWalFile.write(ByteBuffer.wrap(byteArrayOf(9)))
            }

            secondWalFile.write(ByteBuffer.wrap(byteArrayOf(5, 6, 7)))
            allowFinalization.countDown()
            transactionStore.close()

            assertFalse(firstWalFile.isOpen)
            assertFalse(secondWalFile.isOpen)
            assertContentEquals(
                ByteArray(256) { 4 },
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("0.wal")).decompressLz77()
            )
            assertEquals(3L, Files.size(tempDirectory.resolve("wal").resolve("1.wal")))
            assertContentEquals(
                byteArrayOf(5, 6, 7),
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("1.wal"))
            )
        } finally {
            allowFinalization.countDown()
            runCatching { transactionStore.close() }
            rotationExecutor.shutdownNow()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun asynchronousWalFinalizationFailureIsReported() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-async-failure")
        val finalizationFailed = CountDownLatch(1)
        val failNextFinalization = AtomicBoolean(true)
        val transactionStore = object : SmallJournalMemoryMappedTransactionStore(tempDirectory.toString()) {
            override fun finalizeWalFile(walFile: FileChannel) {
                super.finalizeWalFile(walFile)
                if (failNextFinalization.compareAndSet(true, false)) {
                    finalizationFailed.countDown()
                    throw IOException("Injected asynchronous WAL finalization failure")
                }
            }
        }

        try {
            transactionStore.getTransactionFile().write(ByteBuffer.wrap(ByteArray(256) { 8 }))
            transactionStore.getTransactionFile()

            assertTrue(finalizationFailed.await(5, TimeUnit.SECONDS))
            assertFailsWith<com.onyx.exception.TransactionException> {
                transactionStore.close()
            }
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun asynchronousWalCompressionFailureIsReported() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-compression-failure")
        val compressionFailed = CountDownLatch(1)
        val transactionStore = object : SmallJournalMemoryMappedTransactionStore(tempDirectory.toString()) {
            override fun compressWalFile(walFile: Path) {
                compressionFailed.countDown()
                throw IOException("Injected asynchronous WAL compression failure")
            }
        }

        try {
            transactionStore.getTransactionFile().write(ByteBuffer.wrap(ByteArray(256) { 12 }))
            transactionStore.getTransactionFile()

            assertTrue(compressionFailed.await(5, TimeUnit.SECONDS))
            assertFailsWith<com.onyx.exception.TransactionException> {
                transactionStore.close()
            }
            assertContentEquals(
                ByteArray(256) { 12 },
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("0.wal"))
            )
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun recoveryIgnoresOpenWalMappedPadding() {
        val tempDirectory = Files.createTempDirectory("onyx-memory-mapped-transaction-recovery")
        val sourceLocation = tempDirectory.resolve("source.oxd")
        val recoveredLocation = tempDirectory.resolve("recovered.oxd")
        val contextSuffix = System.nanoTime()
        var sourceFactory: EmbeddedPersistenceManagerFactory? = null
        var recoveredFactory: EmbeddedPersistenceManagerFactory? = null

        try {
            sourceFactory = EmbeddedPersistenceManagerFactory(
                sourceLocation.toString(),
                "source-$contextSuffix",
                addShutdownHook = false
            )
            sourceFactory.storeType = StoreType.MEMORY_MAPPED_FILE
            sourceFactory.isEnableJournaling = true
            sourceFactory.initialize()

            val savedEntity = AllAttributeEntity()
            savedEntity.id = "memory-mapped-recovery"
            savedEntity.intValue = 7
            sourceFactory.persistenceManager.saveEntity<IManagedEntity>(savedEntity)

            recoveredFactory = EmbeddedPersistenceManagerFactory(
                recoveredLocation.toString(),
                "recovered-$contextSuffix",
                addShutdownHook = false
            )
            recoveredFactory.storeType = StoreType.MEMORY_MAPPED_FILE
            recoveredFactory.initialize()

            recoveredFactory.schemaContext.transactionInteractor.recoverDatabase(sourceLocation.resolve("wal").toString()) { true }

            val recovered = recoveredFactory.persistenceManager.findById<AllAttributeEntity>(
                AllAttributeEntity::class.java,
                savedEntity.id!!
            )

            assertEquals(savedEntity.id, recovered?.id)
        } finally {
            sourceFactory?.close()
            recoveredFactory?.close()
            deleteDirectory(tempDirectory)
        }
    }

    private class InspectableSchemaContext(contextId: String, location: String) : DefaultSchemaContext(contextId, location) {
        fun currentTransactionStore(): TransactionStore? = transactionStore
    }

    private open class SmallJournalMemoryMappedTransactionStore(location: String) :
        MemoryMappedTransactionStore(location) {
        protected override val maxJournalSize: Long = 128L
    }

    private fun walRecord(payload: ByteArray): ByteArray =
        ByteBuffer.allocate(5 + payload.size)
            .put(1.toByte())
            .putInt(payload.size)
            .put(payload)
            .array()

    companion object {
        private fun deleteDirectory(path: Path) {
            val files = Files.walk(path)
            try {
                files.sorted(Comparator.reverseOrder()).forEach {
                    Files.deleteIfExists(it)
                }
            } finally {
                files.close()
            }
        }
    }
}
