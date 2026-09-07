package transaction

import com.onyx.exception.TransactionException
import com.onyx.extension.common.decompressLz77
import com.onyx.interactors.transaction.impl.DefaultTransactionStore
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultTransactionStoreTest {

    @Test
    fun rotatingWalCompressesSealedFileAndLeavesCurrentFileRegular() {
        val tempDirectory = Files.createTempDirectory("onyx-default-transaction-rotation")
        val firstWalBytes = serializedWalRecord(6, "x".repeat(4096))
        val currentWalBytes = serializedWalRecord(7)
        val appendedWalBytes = serializedWalRecord(10)
        val transactionStore = SmallJournalDefaultTransactionStore(tempDirectory.toString())

        try {
            val firstWalFile = transactionStore.getTransactionFile()
            firstWalFile.write(ByteBuffer.wrap(firstWalBytes))

            val currentWalFile = transactionStore.getTransactionFile()
            assertTrue(firstWalFile !== currentWalFile)
            assertFalse(firstWalFile.isOpen)
            currentWalFile.write(ByteBuffer.wrap(currentWalBytes))
            transactionStore.close()

            assertContentEquals(
                firstWalBytes,
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("0.wal")).decompressLz77()
            )
            assertContentEquals(
                currentWalBytes,
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("1.wal"))
            )

            val reopenedStore = SmallJournalDefaultTransactionStore(tempDirectory.toString())
            try {
                reopenedStore.getTransactionFile().write(ByteBuffer.wrap(appendedWalBytes))
            } finally {
                reopenedStore.close()
            }
            assertContentEquals(
                currentWalBytes + appendedWalBytes,
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("1.wal"))
            )
        } finally {
            transactionStore.close()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun toppedOffWalIsCompressedWhenStoreCloses() {
        val tempDirectory = Files.createTempDirectory("onyx-default-transaction-close-full")
        val walBytes = serializedWalRecord(11, "x".repeat(4096))
        val appendedWalBytes = serializedWalRecord(12)
        val transactionStore = SmallJournalDefaultTransactionStore(tempDirectory.toString())

        try {
            transactionStore.getTransactionFile().write(ByteBuffer.wrap(walBytes))
            transactionStore.close()

            assertContentEquals(
                walBytes,
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("0.wal")).decompressLz77()
            )

            val reopenedStore = SmallJournalDefaultTransactionStore(tempDirectory.toString())
            try {
                reopenedStore.getTransactionFile().write(ByteBuffer.wrap(appendedWalBytes))
            } finally {
                reopenedStore.close()
            }
            assertContentEquals(
                appendedWalBytes,
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("1.wal"))
            )
        } finally {
            transactionStore.close()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun failedCompressionIsRetriedBeforeWalAdvances() {
        val tempDirectory = Files.createTempDirectory("onyx-default-transaction-compression-retry")
        val walBytes = serializedWalRecord(13, "x".repeat(4096))
        val appendedWalBytes = serializedWalRecord(14)
        val failFirstCompression = AtomicBoolean(true)
        val transactionStore = object : SmallJournalDefaultTransactionStore(tempDirectory.toString()) {
            override fun compressWalFile(walFile: Path) {
                if (failFirstCompression.compareAndSet(true, false)) {
                    throw IOException("Injected WAL compression failure")
                }
                super.compressWalFile(walFile)
            }
        }

        try {
            transactionStore.getTransactionFile().write(ByteBuffer.wrap(walBytes))

            val failure = assertFailsWith<TransactionException> {
                transactionStore.getTransactionFile()
            }
            assertEquals(TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE, failure.message)
            assertContentEquals(walBytes, Files.readAllBytes(tempDirectory.resolve("wal").resolve("0.wal")))
            assertFalse(Files.exists(tempDirectory.resolve("wal").resolve("1.wal")))

            transactionStore.getTransactionFile().write(ByteBuffer.wrap(appendedWalBytes))
            transactionStore.close()

            assertContentEquals(
                walBytes,
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("0.wal")).decompressLz77()
            )
            assertContentEquals(
                appendedWalBytes,
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("1.wal"))
            )
        } finally {
            transactionStore.close()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun reopeningCompressesSealedRegularWalLeftByInterruptedRotation() {
        val tempDirectory = Files.createTempDirectory("onyx-default-transaction-interrupted-rotation")
        val walDirectory = Files.createDirectories(tempDirectory.resolve("wal"))
        val sealedWalBytes = serializedWalRecord(15, "x".repeat(4096))
        val currentWalBytes = serializedWalRecord(16)
        val appendedWalBytes = serializedWalRecord(17)
        Files.write(walDirectory.resolve("0.wal"), sealedWalBytes)
        Files.write(walDirectory.resolve("1.wal"), currentWalBytes)
        val transactionStore = SmallJournalDefaultTransactionStore(tempDirectory.toString())

        try {
            transactionStore.getTransactionFile().write(ByteBuffer.wrap(appendedWalBytes))
            transactionStore.close()

            assertContentEquals(
                sealedWalBytes,
                Files.readAllBytes(walDirectory.resolve("0.wal")).decompressLz77()
            )
            assertContentEquals(
                currentWalBytes + appendedWalBytes,
                Files.readAllBytes(walDirectory.resolve("1.wal"))
            )
        } finally {
            transactionStore.close()
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun reopeningAfterMemoryMappedCrashResumesPastCompleteTransactionsAndRemovesPadding() {
        val tempDirectory = Files.createTempDirectory("onyx-default-transaction-reopen-padding")
        val walPath = Files.createDirectories(tempDirectory.resolve("wal")).resolve("47.wal")
        val firstRecord = serializedWalRecord(21)
        val secondRecord = serializedWalRecord(22)
        val appendedRecord = serializedWalRecord(23)
        Files.write(walPath, firstRecord + ByteArray(128 * 1024) + secondRecord + ByteArray(64 * 1024))
        val transactionStore = DefaultTransactionStore(tempDirectory.toString())

        try {
            val transactionFile = transactionStore.getTransactionFile()
            assertEquals((firstRecord.size + secondRecord.size).toLong(), transactionFile.position())
            transactionFile.write(ByteBuffer.wrap(appendedRecord))
            transactionStore.close()

            assertContentEquals(firstRecord + secondRecord + appendedRecord, Files.readAllBytes(walPath))
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun reopeningNormalizesSealedWalGapsBeforeCompression() {
        val tempDirectory = Files.createTempDirectory("onyx-default-transaction-sealed-padding")
        val walDirectory = Files.createDirectories(tempDirectory.resolve("wal"))
        val sealedFirstRecord = serializedWalRecord(24)
        val sealedSecondRecord = serializedWalRecord(25)
        val currentRecord = serializedWalRecord(26)
        val appendedRecord = serializedWalRecord(27)
        Files.write(
            walDirectory.resolve("46.wal"),
            sealedFirstRecord + ByteArray(64 * 1024) + sealedSecondRecord + ByteArray(32)
        )
        Files.write(walDirectory.resolve("47.wal"), currentRecord)
        val transactionStore = DefaultTransactionStore(tempDirectory.toString())

        try {
            transactionStore.getTransactionFile().write(ByteBuffer.wrap(appendedRecord))
            transactionStore.close()

            assertContentEquals(
                sealedFirstRecord + sealedSecondRecord,
                Files.readAllBytes(walDirectory.resolve("46.wal")).decompressLz77()
            )
            assertContentEquals(
                currentRecord + appendedRecord,
                Files.readAllBytes(walDirectory.resolve("47.wal"))
            )
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    @Test
    fun reopeningRejectsInvalidDataAfterPaddingWithoutChangingWal() {
        val tempDirectory = Files.createTempDirectory("onyx-default-transaction-invalid-after-padding")
        val walPath = Files.createDirectories(tempDirectory.resolve("wal")).resolve("47.wal")
        val malformedWal = serializedWalRecord(28) + ByteArray(64 * 1024) + byteArrayOf(99, 0, 0, 0, 1, 42)
        Files.write(walPath, malformedWal)
        val transactionStore = DefaultTransactionStore(tempDirectory.toString())

        try {
            val failure = assertFailsWith<TransactionException> {
                transactionStore.getTransactionFile()
            }
            assertTrue(failure.cause?.message.orEmpty().contains("invalid transaction header"))
            assertContentEquals(malformedWal, Files.readAllBytes(walPath))
        } finally {
            runCatching { transactionStore.close() }
            deleteDirectory(tempDirectory)
        }
    }

    private open class SmallJournalDefaultTransactionStore(location: String) :
        DefaultTransactionStore(location) {
        override val maxJournalSize: Long = 4096L
    }

    private fun deleteDirectory(path: Path) {
        Files.walk(path).use { files ->
            files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
