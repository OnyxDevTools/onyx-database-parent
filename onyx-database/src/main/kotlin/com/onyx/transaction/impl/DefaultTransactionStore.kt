package com.onyx.transaction.impl

import com.onyx.exception.TransactionException
import com.onyx.extension.Block
import com.onyx.extension.catchAll
import com.onyx.transaction.TransactionStore
import com.onyx.util.FileUtil
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by tosborn1 on 9/6/17.
 *
 * Implementation for getting the file channel for a transaction wal
 *
 * @since 2.0.0 Extracted from SchemaContext implementation
 */
open class DefaultTransactionStore(val location:String): TransactionStore {

    private val journalFileIndex = AtomicLong(0L)
    private var lastWalFileChannel: FileChannel? = null
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

                // Grab the last used WAL File
                val directoryListing = File(directory).list()
                Arrays.sort(directoryListing!!)

                val lastWalFile: File

                if (directoryListing.isNotEmpty()) {
                    var fileName = directoryListing[directoryListing.size - 1]
                    fileName = fileName.replace(".wal", "")

                    journalFileIndex.addAndGet(Integer.valueOf(fileName)!!.toLong())
                }

                lastWalFile = File(directory + journalFileIndex.get() + ".wal")

                if (!lastWalFile.exists()) {
                    lastWalFile.createNewFile()
                }

                // Open file channel
                lastWalFileChannel = FileUtil.openFileChannel(lastWalFile.path)
            }

            // If the last wal file exceeds longSize limit threshold, create a new one
            if (lastWalFileChannel!!.size() > MAX_JOURNAL_SIZE) {

                // Close the previous
                lastWalFileChannel!!.force(true)
                lastWalFileChannel!!.close()

                val directory = walDirectory
                val lastWalFile = File(directory + journalFileIndex.addAndGet(1) + ".wal")
                lastWalFile.createNewFile()

                lastWalFileChannel = FileUtil.openFileChannel(lastWalFile.path)
            }

            return lastWalFileChannel!!

        } catch (e: IOException) {
            throw TransactionException(TransactionException.TRANSACTION_FAILED_TO_OPEN_FILE)
        }

    }

    /**
     * Close the open file channel
     *
     * @since 2.0.0
     */
    override fun close() {
        if (lastWalFileChannel != null) {
            catchAll {
                FileUtil.closeFileChannel(lastWalFileChannel)
            }
        }
    }

    companion object {
        // Maximum WAL File longSize
        private val MAX_JOURNAL_SIZE = 1024 * 1024 * 20
    }
}
