package com.onyx.interactors.transaction.impl

import com.onyx.exception.TransactionException
import com.onyx.extension.common.Block
import com.onyx.extension.common.openFileChannel
import com.onyx.interactors.transaction.TransactionStore
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by Tim Osborn on 9/6/17.
 *
 * Implementation for getting the file channel for a transaction wal
 *
 * @since 2.0.0 Extracted from SchemaContext implementation
 */
open class DefaultTransactionStore(val location:String): TransactionStore {

    private val journalFileIndex = AtomicLong(0L)
    private var lastWalFileChannel: FileChannel? = null
    private var lastWalFile: File? = null
    private val transactionFileLock = Block()

    private val walDirectory: String
        get() = this.location + File.separator + "wal" + File.separator

    /**
     * Get WAL Transaction File. This will get the appropriate file channel and return it
     *
     * @return Open File Channel
     * @throws TransactionException Cannot write transaction
     */
    @Throws(TransactionException::class)
    override fun getTransactionFile(): FileChannel = synchronized(transactionFileLock) {
        try {
            if (lastWalFileChannel == null) {

                // Create the journaling directory if it does'nt exist
                val directory = walDirectory

                val journalingDirector = File(directory)
                if (!journalingDirector.exists()) {
                    journalingDirector.mkdirs()
                }

                val walFiles = journalingDirector.listWalFiles()
                walFiles.dropLast(1).forEach { sealedWalFile ->
                    if (!sealedWalFile.file.toPath().isCompressedWal()) {
                        compressWalFileOrThrow(sealedWalFile.file.toPath())
                    }
                }

                val latestWalFile = walFiles.lastOrNull()
                if (latestWalFile != null) {
                    journalFileIndex.set(
                        if (latestWalFile.file.toPath().isCompressedWal()) {
                            latestWalFile.index + 1L
                        } else {
                            latestWalFile.index
                        }
                    )
                }

                lastWalFile = File(directory + journalFileIndex.get() + WAL_FILE_EXTENSION)
                lastWalFileChannel = lastWalFile!!.path.openFileChannel()
                    ?: throw IOException("Unable to open WAL file channel")
            }

            // If the last wal file exceeds longSize limit threshold, create a new one
            if (lastWalFileChannel!!.size() >= maxJournalSize) {
                val toppedOffWalFile = lastWalFile!!

                // Close the previous
                lastWalFileChannel!!.force(true)
                lastWalFileChannel!!.close()
                lastWalFileChannel = null
                lastWalFile = null

                compressWalFileOrThrow(toppedOffWalFile.toPath())

                val nextJournalFileIndex = journalFileIndex.get() + 1L
                val nextWalFile = File(walDirectory + nextJournalFileIndex + WAL_FILE_EXTENSION)
                val nextWalFileChannel = nextWalFile.path.openFileChannel()
                    ?: throw IOException("Unable to open WAL file channel")
                journalFileIndex.set(nextJournalFileIndex)
                lastWalFile = nextWalFile
                lastWalFileChannel = nextWalFileChannel
            }

            return lastWalFileChannel!!

        } catch (cause: IOException) {
            throw TransactionException(
                TransactionException.TRANSACTION_FAILED_TO_OPEN_FILE,
                null,
                cause
            )
        }

    }

    /**
     * Close the open file channel
     *
     * @since 2.0.0
     */
    override fun close() {
        synchronized(transactionFileLock) {
            val walFileChannel = lastWalFileChannel ?: return@synchronized
            val walFile = lastWalFile
            var failure: Throwable? = null
            fun recordFailure(cause: Throwable) {
                val currentFailure = failure
                if (currentFailure == null) {
                    failure = cause
                } else {
                    currentFailure.addSuppressed(cause)
                }
            }

            val toppedOff = try {
                walFileChannel.size() >= maxJournalSize
            } catch (cause: Throwable) {
                recordFailure(cause)
                false
            }
            try {
                walFileChannel.force(true)
            } catch (cause: Throwable) {
                recordFailure(cause)
            }
            try {
                walFileChannel.close()
            } catch (cause: Throwable) {
                recordFailure(cause)
            }

            if (failure == null && toppedOff && walFile != null) {
                try {
                    compressWalFileOrThrow(walFile.toPath())
                } catch (cause: Throwable) {
                    recordFailure(cause)
                }
            }

            lastWalFileChannel = null
            lastWalFile = null

            failure?.let { cause ->
                if (cause is TransactionException) throw cause
                throw TransactionException(
                    TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE,
                    null,
                    cause
                )
            }
        }
    }

    protected open val maxJournalSize: Long
        get() = MAX_JOURNAL_SIZE

    protected open fun compressWalFile(walFile: Path) {
        replaceWithCompressedWal(walFile)
    }

    private fun compressWalFileOrThrow(walFile: Path) {
        try {
            compressWalFile(walFile)
        } catch (cause: Exception) {
            throw TransactionException(
                TransactionException.TRANSACTION_FAILED_TO_WRITE_FILE,
                null,
                cause
            )
        }
    }

    companion object {
        // Maximum WAL File longSize
        private const val MAX_JOURNAL_SIZE = 1024L * 1024L * 20L
    }
}
