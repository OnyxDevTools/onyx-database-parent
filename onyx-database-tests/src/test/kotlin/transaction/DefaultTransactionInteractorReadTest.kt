package transaction

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.exception.TransactionException
import com.onyx.extension.common.compressLz77
import com.onyx.interactors.transaction.TransactionStore
import com.onyx.interactors.transaction.data.DeleteQueryTransaction
import com.onyx.interactors.transaction.data.Transaction
import com.onyx.interactors.transaction.impl.DefaultTransactionInteractor
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultTransactionInteractorReadTest {

    @Test
    fun recoveryAppliesNumberedWalFilesInNumericOrder() {
        val walDirectory = Files.createTempDirectory("onyx-numbered-wal-recovery")
        val creationOrder = listOf(1, 10, 11, 2, 3, 4, 5, 6, 7, 8, 9)
        val recoveredWalFiles = ArrayList<String>()
        val interactor = object : DefaultTransactionInteractor(UNUSED_TRANSACTION_STORE, noOpPersistenceManager()) {
            override fun applyTransactionLog(
                walTransactionFile: String,
                executeTransaction: (Transaction) -> Boolean
            ): Boolean {
                recoveredWalFiles += java.io.File(walTransactionFile).name
                return true
            }
        }

        try {
            creationOrder.forEach { walNumber ->
                Files.createFile(walDirectory.resolve("$walNumber.wal"))
            }

            interactor.recoverDatabase(walDirectory.toString()) { true }

            assertEquals((1..11).map { "$it.wal" }, recoveredWalFiles)
        } finally {
            creationOrder.forEach { walNumber ->
                Files.deleteIfExists(walDirectory.resolve("$walNumber.wal"))
            }
            Files.deleteIfExists(walDirectory)
        }
    }

    @Test
    fun lenientReplayOverloadHonorsLegacyOverride() {
        val replayedWalFiles = ArrayList<String>()
        val interactor = object : DefaultTransactionInteractor(UNUSED_TRANSACTION_STORE, noOpPersistenceManager()) {
            override fun applyTransactionLog(
                walTransactionFile: String,
                executeTransaction: (Transaction) -> Boolean,
            ): Boolean {
                replayedWalFiles += walTransactionFile
                return true
            }
        }

        assertTrue(
            interactor.applyTransactionLog(
                walTransactionFile = "legacy-override.wal",
                skipFailedTransactions = true,
            ) { false },
        )
        assertEquals(listOf("legacy-override.wal"), replayedWalFiles)
    }

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
    fun transactionApplicationFailureIsReportedAndLaterRecordsStillReplay() {
        val walFile = Files.createTempFile("onyx-partial-wal-recovery", ".wal")
        val attemptedRows = ArrayList<Int>()
        val failures = ArrayList<ReplayFailure>()
        val interactor = object : DefaultTransactionInteractor(UNUSED_TRANSACTION_STORE, noOpPersistenceManager()) {
            override fun onTransactionReplayFailure(
                walTransactionFile: String,
                transactionOffset: Long,
                transaction: Transaction?,
                cause: Exception
            ) {
                failures += ReplayFailure(walTransactionFile, transactionOffset, transaction, cause)
            }
        }

        try {
            Files.write(walFile, concatenate(deleteQueryRecord(3), deleteQueryRecord(7)))

            assertTrue(interactor.applyTransactionLog(walFile.toString()) { transaction ->
                val row = assertIs<DeleteQueryTransaction>(transaction).query.firstRow
                attemptedRows += row
                if (row == 3) throw IllegalStateException("cannot apply row 3")
                false
            })

            assertEquals(listOf(3, 7), attemptedRows)
            assertEquals(1, failures.size)
            assertEquals(walFile.toString(), failures.single().walFile)
            assertEquals(0L, failures.single().offset)
            assertIs<DeleteQueryTransaction>(failures.single().transaction)
            assertEquals("cannot apply row 3", failures.single().cause.message)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun strictReplayStopsAtFirstTransactionFailure() {
        val walFile = Files.createTempFile("onyx-strict-wal-recovery", ".wal")
        val attemptedRows = ArrayList<Int>()

        try {
            Files.write(walFile, concatenate(deleteQueryRecord(3), deleteQueryRecord(7)))

            val failure = assertFailsWith<TransactionException> {
                interactor().applyTransactionLog(
                    walTransactionFile = walFile.toString(),
                    skipFailedTransactions = false,
                ) { transaction ->
                    val row = assertIs<DeleteQueryTransaction>(transaction).query.firstRow
                    attemptedRows += row
                    if (row == 3) throw IllegalStateException("cannot apply row 3")
                    false
                }
            }

            assertEquals(listOf(3), attemptedRows)
            assertTrue(failure.message.orEmpty().contains("at byte 0"))
            assertEquals("cannot apply row 3", failure.cause?.message)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun strictReplayRejectsMalformedSaveBeforeLaterTransactions() {
        val walFile = Files.createTempFile("onyx-strict-malformed-save", ".wal")
        val attemptedRows = ArrayList<Int>()

        try {
            Files.write(
                walFile,
                concatenate(
                    transactionRecord(SAVE, mapOf("value" to emptyMap<String, Any?>())),
                    deleteQueryRecord(7),
                ),
            )

            val failure = assertFailsWith<TransactionException> {
                interactor().applyTransactionLog(
                    walTransactionFile = walFile.toString(),
                    skipFailedTransactions = false,
                ) { transaction ->
                    attemptedRows += assertIs<DeleteQueryTransaction>(transaction).query.firstRow
                    false
                }
            }

            assertTrue(attemptedRows.isEmpty())
            assertTrue(failure.message.orEmpty().contains("at byte 0"))
            assertEquals(
                "SAVE transaction payload is missing a valid entity type",
                failure.cause?.message,
            )
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun strictReplayStopsAtFirstPersistenceFailure() {
        val walFile = Files.createTempFile("onyx-strict-persistence-failure", ".wal")
        val executedRows = ArrayList<Int>()
        val persistenceManager = Proxy.newProxyInstance(
            PersistenceManager::class.java.classLoader,
            arrayOf(PersistenceManager::class.java),
        ) { _, method, arguments ->
            if (method.name == "executeDelete") {
                val row = (arguments!!.single() as Query).firstRow
                executedRows += row
                if (row == 3) throw IllegalStateException("cannot persist row 3")
            }
            defaultReturnValue(method)
        } as PersistenceManager

        try {
            Files.write(walFile, concatenate(deleteQueryRecord(3), deleteQueryRecord(7)))

            val failure = assertFailsWith<TransactionException> {
                DefaultTransactionInteractor(UNUSED_TRANSACTION_STORE, persistenceManager)
                    .applyTransactionLog(
                        walTransactionFile = walFile.toString(),
                        skipFailedTransactions = false,
                    ) { true }
            }

            assertEquals(listOf(3), executedRows)
            assertTrue(failure.message.orEmpty().contains("at byte 0"))
            assertEquals("cannot persist row 3", failure.cause?.message)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun bufferedReaderDecodesCompressedWal() {
        val walFile = Files.createTempFile("onyx-compressed-wal-reader", ".wal")
        val expectedRows = listOf(13, 17, 23)

        try {
            val regularWal = concatenate(
                *expectedRows.map(::deleteQueryRecord).toTypedArray(),
                ByteArray(64)
            )
            Files.write(walFile, regularWal.compressLz77())

            val actualRows = ArrayList<Int>()
            assertTrue(interactor().applyTransactionLog(walFile.toString()) { transaction ->
                actualRows += assertIs<DeleteQueryTransaction>(transaction).query.firstRow
                false
            })

            assertEquals(expectedRows, actualRows)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun bufferedReaderDecodesRawLz77WalFrame() {
        val walFile = Files.createTempFile("onyx-raw-lz77-wal-reader", ".wal")
        val random = Random(7L)
        val partition = CharArray(64 * 1024) {
            (33 + random.nextInt(94)).toChar()
        }.concatToString()

        try {
            val framedWal = deleteQueryRecord(27, partition).compressLz77()
            assertEquals(0, framedWal[5].toInt())
            Files.write(walFile, framedWal)

            var recoveredQuery: Query? = null
            assertTrue(interactor().applyTransactionLog(walFile.toString()) { transaction ->
                recoveredQuery = assertIs<DeleteQueryTransaction>(transaction).query
                false
            })

            assertEquals(27, recoveredQuery?.firstRow)
            assertEquals(partition, recoveredQuery?.partition)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun recoveryAppliesMixedRegularAndCompressedWalFiles() {
        val walDirectory = Files.createTempDirectory("onyx-mixed-wal-recovery")
        val walFiles = listOf(
            walDirectory.resolve("1.wal") to deleteQueryRecord(1).compressLz77(),
            walDirectory.resolve("2.wal") to deleteQueryRecord(2),
            walDirectory.resolve("10.wal") to deleteQueryRecord(10).compressLz77()
        )
        val ignoredFile = walDirectory.resolve(".2.wal.replacement.tmp")

        try {
            walFiles.forEach { (path, contents) -> Files.write(path, contents) }
            Files.write(ignoredFile, deleteQueryRecord(99))

            val recoveredRows = ArrayList<Int>()
            interactor().recoverDatabase(walDirectory.toString()) { transaction ->
                recoveredRows += assertIs<DeleteQueryTransaction>(transaction).query.firstRow
                false
            }

            assertEquals(listOf(1, 2, 10), recoveredRows)
        } finally {
            walFiles.forEach { (path, _) -> Files.deleteIfExists(path) }
            Files.deleteIfExists(ignoredFile)
            Files.deleteIfExists(walDirectory)
        }
    }

    @Test
    fun compressedReaderRejectsInvalidFrame() {
        val walFile = Files.createTempFile("onyx-invalid-compressed-wal", ".wal")

        try {
            Files.write(walFile, deleteQueryRecord(31).compressLz77().dropLast(1).toByteArray())

            val failure = assertFailsWith<TransactionException> {
                interactor().applyTransactionLog(walFile.toString()) { false }
            }
            assertEquals(TransactionException.TRANSACTION_FAILED_TO_READ_FILE, failure.message)
            assertIs<IOException>(failure.cause)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun compressedReaderRejectsHugeTruncatedFrameWithoutAllocatingOutput() {
        val walFile = Files.createTempFile("onyx-oversized-compressed-wal", ".wal")
        val oversizedFrame = ByteBuffer.allocate(10)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf('L'.code.toByte(), 'Z'.code.toByte(), '7'.code.toByte(), '7'.code.toByte()))
            .put(1.toByte())
            .put(1.toByte())
            .putInt(Int.MAX_VALUE)
            .array()

        try {
            Files.write(walFile, oversizedFrame)

            val failure = assertFailsWith<TransactionException> {
                interactor().applyTransactionLog(walFile.toString()) { false }
            }
            assertEquals(TransactionException.TRANSACTION_FAILED_TO_READ_FILE, failure.message)
            assertIs<IOException>(failure.cause)
            assertEquals("Truncated WAL compression payload", failure.cause?.message)
        } finally {
            Files.deleteIfExists(walFile)
        }
    }

    @Test
    fun readerDoesNotCreateMissingWalFile() {
        val directory = Files.createTempDirectory("onyx-missing-wal-reader")
        val missingWal = directory.resolve("missing.wal")

        try {
            assertFailsWith<TransactionException> {
                interactor().applyTransactionLog(missingWal.toString()) { false }
            }
            assertFalse(Files.exists(missingWal))
        } finally {
            Files.deleteIfExists(directory)
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
    fun compressedReaderHandlesPayloadLargerThanItsReadAheadBuffer() {
        val walFile = Files.createTempFile("onyx-large-compressed-wal-payload", ".wal")
        val largePartition = "x".repeat(300 * 1024)

        try {
            Files.write(
                walFile,
                concatenate(
                    deleteQueryRecord(37, largePartition),
                    deleteQueryRecord(39)
                ).compressLz77()
            )

            val recoveredQueries = ArrayList<Query>()
            assertTrue(interactor().applyTransactionLog(walFile.toString()) { transaction ->
                recoveredQueries += assertIs<DeleteQueryTransaction>(transaction).query
                false
            })

            assertEquals(listOf(37, 39), recoveredQueries.map(Query::firstRow))
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
        return transactionRecord(DELETE_QUERY, Query().apply {
            this.firstRow = firstRow
            this.partition = partition
        })
    }

    private fun transactionRecord(transactionType: Byte, value: Any): ByteArray {
        val payload = BufferStream.toBuffer(value)
        val payloadBytes = try {
            ByteArray(payload.remaining()).also(payload::get)
        } finally {
            BufferPool.recycle(payload)
        }

        return ByteBuffer.allocate(WAL_HEADER_SIZE + payloadBytes.size)
            .put(transactionType)
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

    private data class ReplayFailure(
        val walFile: String,
        val offset: Long,
        val transaction: Transaction?,
        val cause: Exception
    )

    private companion object {
        const val SAVE: Byte = 1
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
            defaultReturnValue(method)
        } as PersistenceManager

        private fun defaultReturnValue(method: Method): Any? = when (method.returnType) {
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
    }
}
