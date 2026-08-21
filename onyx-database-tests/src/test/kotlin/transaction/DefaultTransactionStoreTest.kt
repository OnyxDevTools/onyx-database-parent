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
        val firstWalBytes = ByteArray(128) { 6 }
        val currentWalBytes = byteArrayOf(7, 8, 9)
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
                reopenedStore.getTransactionFile().write(ByteBuffer.wrap(byteArrayOf(10)))
            } finally {
                reopenedStore.close()
            }
            assertContentEquals(
                byteArrayOf(7, 8, 9, 10),
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
        val walBytes = ByteArray(256) { 11 }
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
                reopenedStore.getTransactionFile().write(ByteBuffer.wrap(byteArrayOf(12)))
            } finally {
                reopenedStore.close()
            }
            assertContentEquals(
                byteArrayOf(12),
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
        val walBytes = ByteArray(256) { 13 }
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

            transactionStore.getTransactionFile().write(ByteBuffer.wrap(byteArrayOf(14)))
            transactionStore.close()

            assertContentEquals(
                walBytes,
                Files.readAllBytes(tempDirectory.resolve("wal").resolve("0.wal")).decompressLz77()
            )
            assertContentEquals(
                byteArrayOf(14),
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
        val sealedWalBytes = ByteArray(256) { 15 }
        Files.write(walDirectory.resolve("0.wal"), sealedWalBytes)
        Files.write(walDirectory.resolve("1.wal"), byteArrayOf(16))
        val transactionStore = SmallJournalDefaultTransactionStore(tempDirectory.toString())

        try {
            transactionStore.getTransactionFile().write(ByteBuffer.wrap(byteArrayOf(17)))
            transactionStore.close()

            assertContentEquals(
                sealedWalBytes,
                Files.readAllBytes(walDirectory.resolve("0.wal")).decompressLz77()
            )
            assertContentEquals(
                byteArrayOf(16, 17),
                Files.readAllBytes(walDirectory.resolve("1.wal"))
            )
        } finally {
            transactionStore.close()
            deleteDirectory(tempDirectory)
        }
    }

    private open class SmallJournalDefaultTransactionStore(location: String) :
        DefaultTransactionStore(location) {
        override val maxJournalSize: Long = 128L
    }

    private fun deleteDirectory(path: Path) {
        Files.walk(path).use { files ->
            files.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
