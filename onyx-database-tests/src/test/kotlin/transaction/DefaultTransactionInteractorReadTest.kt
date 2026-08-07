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
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultTransactionInteractorReadTest {

    @Test
    fun bufferedReaderDecodesTransactionsInOrderAndStopsAtPadding() {
        val walFile = Files.createTempFile("onyx-buffered-wal-reader", ".wal")
        val expectedRows = listOf(3, 7, 11)

        try {
            Files.write(
                walFile,
                concatenate(
                    *expectedRows.map(::deleteQueryRecord).toTypedArray(),
                    ByteArray(64)
                )
            )

            val actualRows = ArrayList<Int>()
            assertTrue(interactor().applyTransactionLog(walFile.toString()) { transaction ->
                actualRows += assertIs<DeleteQueryTransaction>(transaction).query.firstRow
                false
            })

            assertEquals(expectedRows, actualRows)

            // The read channel must be released when recovery returns.
            assertTrue(Files.deleteIfExists(walFile))
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun bufferedReaderAcceptsEmptyWalAndPartialZeroTail() {
        val emptyWal = Files.createTempFile("onyx-empty-buffered-wal", ".wal")
        val paddedWal = Files.createTempFile("onyx-partially-padded-buffered-wal", ".wal")

        try {
            assertTrue(interactor().applyTransactionLog(emptyWal.toString()) { false })

            Files.write(paddedWal, concatenate(deleteQueryRecord(19), ByteArray(4)))
            val recoveredRows = ArrayList<Int>()
            assertTrue(interactor().applyTransactionLog(paddedWal.toString()) { transaction ->
                recoveredRows += assertIs<DeleteQueryTransaction>(transaction).query.firstRow
                false
            })
            assertEquals(listOf(19), recoveredRows)
        } finally {
            Files.deleteIfExists(emptyWal)
            Files.deleteIfExists(paddedWal)
        }
    }

    @Test
    fun bufferedReaderHandlesPayloadLargerThanItsReadAheadBuffer() {
        val walFile = Files.createTempFile("onyx-large-buffered-wal-payload", ".wal")
        val largePartition = "x".repeat(300 * 1024)

        try {
            Files.write(
                walFile,
                concatenate(
                    deleteQueryRecord(23, largePartition),
                    deleteQueryRecord(29)
                )
            )

            val recoveredQueries = ArrayList<Query>()
            assertTrue(interactor().applyTransactionLog(walFile.toString()) { transaction ->
                recoveredQueries += assertIs<DeleteQueryTransaction>(transaction).query
                false
            })

            assertEquals(listOf(23, 29), recoveredQueries.map(Query::firstRow))
            assertEquals(largePartition, recoveredQueries.first().partition)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun bufferedReaderPreservesHeaderAcrossARefillBoundary() {
        val walFile = Files.createTempFile("onyx-buffered-wal-boundary", ".wal")
        val firstRecordSize = READ_AHEAD_BUFFER_SIZE - 3
        val firstRecord = deleteQueryRecordOfSize(41, firstRecordSize)

        try {
            assertEquals(firstRecordSize, firstRecord.size)
            Files.write(walFile, concatenate(firstRecord, deleteQueryRecord(43)))

            val recoveredRows = ArrayList<Int>()
            assertTrue(interactor().applyTransactionLog(walFile.toString()) { transaction ->
                recoveredRows += assertIs<DeleteQueryTransaction>(transaction).query.firstRow
                false
            })
            assertEquals(listOf(41, 43), recoveredRows)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun bufferedReaderIgnoresTransactionsAppendedAfterItsSizeSnapshot() {
        val walFile = Files.createTempFile("onyx-growing-buffered-wal", ".wal")

        try {
            Files.write(walFile, deleteQueryRecord(47))

            val recoveredRows = ArrayList<Int>()
            assertTrue(interactor().applyTransactionLog(walFile.toString()) { transaction ->
                recoveredRows += assertIs<DeleteQueryTransaction>(transaction).query.firstRow
                Files.write(walFile, deleteQueryRecord(53), StandardOpenOption.APPEND)
                false
            })

            assertEquals(listOf(47), recoveredRows)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun bufferedReaderHandlesConcurrentRemovalOfWalPadding() {
        val walFile = Files.createTempFile("onyx-concurrently-truncated-wal", ".wal")
        val firstRecord = deleteQueryRecord(31, "x".repeat(300 * 1024))

        try {
            Files.write(walFile, concatenate(firstRecord, ByteArray(512 * 1024)))

            val recoveredRows = ArrayList<Int>()
            assertTrue(interactor().applyTransactionLog(walFile.toString()) { transaction ->
                recoveredRows += assertIs<DeleteQueryTransaction>(transaction).query.firstRow
                if (recoveredRows.size == 1) {
                    FileChannel.open(walFile, StandardOpenOption.WRITE).use { channel ->
                        channel.truncate(firstRecord.size.toLong())
                    }
                }
                false
            })

            assertEquals(listOf(31), recoveredRows)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun bufferedReaderRejectsPartialNonZeroHeader() {
        val walFile = Files.createTempFile("onyx-partial-buffered-wal-header", ".wal")

        try {
            Files.write(walFile, byteArrayOf(DELETE_QUERY, 0, 1))

            val failure = assertFailsWith<TransactionException> {
                interactor().applyTransactionLog(walFile.toString()) { false }
            }
            assertIs<IllegalStateException>(failure.cause)
            assertEquals("WAL transaction header is incomplete", failure.cause?.message)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun bufferedReaderRejectsOversizedPayloadBeforeAllocatingIt() {
        val walFile = Files.createTempFile("onyx-oversized-buffered-wal-payload", ".wal")

        try {
            Files.write(
                walFile,
                ByteBuffer.allocate(WAL_HEADER_SIZE)
                    .put(DELETE_QUERY)
                    .putInt(Int.MAX_VALUE)
                    .array()
            )

            val failure = assertFailsWith<TransactionException> {
                interactor().applyTransactionLog(walFile.toString()) { false }
            }
            assertIs<IllegalStateException>(failure.cause)
            assertEquals("WAL transaction data is incomplete", failure.cause?.message)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    private fun interactor() = DefaultTransactionInteractor(UNUSED_TRANSACTION_STORE, noOpPersistenceManager())

    private fun deleteQueryRecord(firstRow: Int, partition: String = ""): ByteArray {
        val payload = BufferStream.toBuffer(Query().apply {
            this.firstRow = firstRow
            this.partition = partition
        })
        val payloadBytes = try {
            ByteArray(payload.remaining()).also(payload::get)
        } finally {
            BufferPool.recycle(payload)
        }

        return ByteBuffer.allocate(WAL_HEADER_SIZE + payloadBytes.size)
            .put(DELETE_QUERY)
            .putInt(payloadBytes.size)
            .put(payloadBytes)
            .array()
    }

    private fun deleteQueryRecordOfSize(firstRow: Int, targetSize: Int): ByteArray {
        var partitionLength = targetSize - deleteQueryRecord(firstRow).size
        repeat(4) {
            require(partitionLength >= 0) { "Target WAL record size is too small" }
            val record = deleteQueryRecord(firstRow, "x".repeat(partitionLength))
            val sizeDifference = targetSize - record.size
            if (sizeDifference == 0) return record
            partitionLength += sizeDifference
        }
        error("Unable to create a WAL record of size $targetSize")
    }

    private fun concatenate(vararg values: ByteArray): ByteArray {
        val result = ByteBuffer.allocate(values.sumOf(ByteArray::size))
        values.forEach(result::put)
        return result.array()
    }

    private companion object {
        const val DELETE_QUERY: Byte = 3
        const val WAL_HEADER_SIZE = 5
        const val READ_AHEAD_BUFFER_SIZE = 256 * 1024

        val UNUSED_TRANSACTION_STORE = object : TransactionStore {
            override fun getTransactionFile(): FileChannel = error("The read tests do not write transactions")
            override fun close() = Unit
        }

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
