package com.onyx.interactors.transaction

import com.onyx.exception.TransactionException
import java.nio.channels.FileChannel

/**
 * Created by Tim Osborn on 9/6/17.
 *
 * Contract for retrieving transaction files
 */
interface TransactionStore {

    /**
     * Get WAL Transaction File. This will get the appropriate file channel and return it
     *
     * @return Open File Channel
     * @throws TransactionException Cannot write transaction
     *
     * @since 2.0.0 Moved to Kotlin and Transaction Store rather than schema context
     */
    @Throws(TransactionException::class)
    fun getTransactionFile(): FileChannel

    /**
     * Close the current open transaction file
     */
    fun close()
}