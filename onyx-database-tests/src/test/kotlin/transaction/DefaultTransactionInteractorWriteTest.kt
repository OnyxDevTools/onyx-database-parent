package transaction

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.exception.TransactionException
import com.onyx.interactors.transaction.TransactionStore
import com.onyx.interactors.transaction.data.DeleteQueryTransaction
import com.onyx.interactors.transaction.impl.DefaultTransactionInteractor
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultTransactionInteractorWriteTest {

    @Test
    fun gatheringWriteHandlesPartialProgressAndPreservesWalBytesAndRecovery() {
        val walFile = Files.createTempFile("onyx-gathering-write", ".wal")
        val delegate = FileChannel.open(
            walFile,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
        val channel = PartialGatheringFileChannel(delegate, maxBytesPerWrite = 3)
        val transactionStore = SingleChannelTransactionStore(channel)
        val interactor = DefaultTransactionInteractor(transactionStore, noOpPersistenceManager())
        val query = Query().apply {
            selections = listOf("id", "name")
            firstRow = 7
            maxResults = 19
            partition = "test-partition"
        }

        try {
            val expectedPayload = BufferStream.toBuffer(query)
            val expectedPayloadBytes = try {
                ByteArray(expectedPayload.remaining()).also(expectedPayload::get)
            } finally {
                BufferPool.recycle(expectedPayload)
            }
            val expectedWalBytes = ByteBuffer.allocate(WAL_HEADER_SIZE + expectedPayloadBytes.size)
                .put(DELETE_QUERY)
                .putInt(expectedPayloadBytes.size)
                .put(expectedPayloadBytes)
                .array()

            interactor.writeDeleteQuery(query)
            transactionStore.close()

            assertEquals(0, channel.singleBufferWriteCalls)
            assertTrue(channel.gatheringWriteCalls > 2)
            assertContentEquals(expectedWalBytes, Files.readAllBytes(walFile))

            var recoveredTransaction: DeleteQueryTransaction? = null
            assertTrue(interactor.applyTransactionLog(walFile.toString()) { transaction ->
                recoveredTransaction = assertIs<DeleteQueryTransaction>(transaction)
                false
            })

            val recoveredQuery = requireNotNull(recoveredTransaction).query
            assertEquals(query.selections, recoveredQuery.selections)
            assertEquals(query.firstRow, recoveredQuery.firstRow)
            assertEquals(query.maxResults, recoveredQuery.maxResults)
            assertEquals(query.partition, recoveredQuery.partition)
        } finally {
            transactionStore.close()
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun concurrentGatheringWritesRemainWholeWalRecords() {
        val walFile = Files.createTempFile("onyx-concurrent-gathering-write", ".wal")
        val delegate = FileChannel.open(
            walFile,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
        val channel = PartialGatheringFileChannel(delegate, maxBytesPerWrite = 2)
        val transactionStore = SingleChannelTransactionStore(channel)
        val interactor = DefaultTransactionInteractor(transactionStore, noOpPersistenceManager())
        val executor = Executors.newFixedThreadPool(4)

        try {
            val start = CountDownLatch(1)
            val futures = (0 until CONCURRENT_TRANSACTION_COUNT).map { ordinal ->
                executor.submit {
                    start.await()
                    interactor.writeDeleteQuery(Query().apply {
                        firstRow = ordinal
                        maxResults = ordinal + 1
                    })
                }
            }
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
            transactionStore.close()

            assertEquals(1, channel.gatheringBufferArrayCount)
            val buffersAfterWrite = requireNotNull(channel.lastGatheringBuffers)
            assertTrue(buffersAfterWrite[0] === buffersAfterWrite[1])

            val recoveredOrdinals = HashSet<Int>()
            assertTrue(interactor.applyTransactionLog(walFile.toString()) { transaction ->
                recoveredOrdinals.add(assertIs<DeleteQueryTransaction>(transaction).query.firstRow)
                false
            })

            assertEquals((0 until CONCURRENT_TRANSACTION_COUNT).toSet(), recoveredOrdinals)
        } finally {
            executor.shutdownNow()
            transactionStore.close()
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun failedGatheringWriteDoesNotRetainSerializedPayload() {
        val walFile = Files.createTempFile("onyx-failed-gathering-write", ".wal")
        val delegate = FileChannel.open(
            walFile,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
        val channel = PartialGatheringFileChannel(
            delegate,
            maxBytesPerWrite = 3,
            failFirstGatheringWrite = true
        )
        val transactionStore = SingleChannelTransactionStore(channel)
        val interactor = DefaultTransactionInteractor(transactionStore, noOpPersistenceManager())

        try {
            assertFailsWith<TransactionException> {
                interactor.writeDeleteQuery(Query().apply { maxResults = 100 })
            }

            val buffersAfterFailure = requireNotNull(channel.lastGatheringBuffers)
            assertTrue(buffersAfterFailure[0] === buffersAfterFailure[1])
        } finally {
            transactionStore.close()
            Files.deleteIfExists(walFile)
        }
    }

    private class SingleChannelTransactionStore(
        private val channel: FileChannel
    ) : TransactionStore {
        override fun getTransactionFile(): FileChannel = channel

        override fun close() {
            if (channel.isOpen) channel.close()
        }
    }

    /**
     * Restricts each gathering write and reports zero progress once. This exercises both conditions
     * permitted by [FileChannel.write] without allowing the single-buffer overload as a fallback.
     */
    private class PartialGatheringFileChannel(
        private val delegate: FileChannel,
        private val maxBytesPerWrite: Int,
        private val failFirstGatheringWrite: Boolean = false
    ) : FileChannel() {
        var gatheringWriteCalls = 0
            private set
        var singleBufferWriteCalls = 0
            private set
        private val gatheringBufferArrays = IdentityHashMap<Array<out ByteBuffer>, Unit>()
        var lastGatheringBuffers: Array<out ByteBuffer>? = null
            private set
        val gatheringBufferArrayCount: Int
            get() = gatheringBufferArrays.size

        override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long {
            gatheringWriteCalls++
            gatheringBufferArrays[srcs] = Unit
            lastGatheringBuffers = srcs
            if (failFirstGatheringWrite && gatheringWriteCalls == 1) {
                throw IOException("simulated gathering-write failure")
            }
            if (gatheringWriteCalls == 1) return 0

            var totalWritten = 0L
            var available = maxBytesPerWrite
            for (index in offset until offset + length) {
                if (available == 0) break

                val source = srcs[index]
                if (!source.hasRemaining()) continue

                val originalLimit = source.limit()
                val requested = min(source.remaining(), available)
                source.limit(source.position() + requested)
                val written = try {
                    delegate.write(source)
                } finally {
                    source.limit(originalLimit)
                }
                totalWritten += written
                available -= written
                if (written < requested) break
            }
            Thread.yield()
            return totalWritten
        }

        override fun write(src: ByteBuffer): Int {
            singleBufferWriteCalls++
            error("The WAL writer should use the gathering-write overload")
        }

        override fun read(dst: ByteBuffer): Int = delegate.read(dst)

        override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long =
            delegate.read(dsts, offset, length)

        override fun position(): Long = delegate.position()

        override fun position(newPosition: Long): FileChannel {
            delegate.position(newPosition)
            return this
        }

        override fun size(): Long = delegate.size()

        override fun truncate(size: Long): FileChannel {
            delegate.truncate(size)
            return this
        }

        override fun force(metaData: Boolean) = delegate.force(metaData)

        override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long =
            delegate.transferTo(position, count, target)

        override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long =
            delegate.transferFrom(src, position, count)

        override fun read(dst: ByteBuffer, position: Long): Int = delegate.read(dst, position)

        override fun write(src: ByteBuffer, position: Long): Int = delegate.write(src, position)

        override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer =
            delegate.map(mode, position, size)

        override fun lock(position: Long, size: Long, shared: Boolean): FileLock =
            delegate.lock(position, size, shared)

        override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? =
            delegate.tryLock(position, size, shared)

        override fun implCloseChannel() = delegate.close()
    }

    private companion object {
        const val DELETE_QUERY: Byte = 3
        const val WAL_HEADER_SIZE = 5
        const val CONCURRENT_TRANSACTION_COUNT = 64

        fun noOpPersistenceManager(): PersistenceManager = Proxy.newProxyInstance(
            PersistenceManager::class.java.classLoader,
            arrayOf(PersistenceManager::class.java)
        ) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0F
                java.lang.Double.TYPE -> 0.0
                java.lang.Character.TYPE -> 0.toChar()
                else -> null
            }
        } as PersistenceManager
    }
}
