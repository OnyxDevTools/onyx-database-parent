package com.onyx.exception

import com.onyx.interactors.transaction.data.Transaction

/**
 * Created by Tim Osborn on 3/25/16.
 *
 * This error denotes a failure to interact with a transaction WAL file
 */
class TransactionException @JvmOverloads constructor(message: String? = "") : OnyxException(message) {

    private var transaction: Transaction? = null

    /**
     * Constructor with Transaction
     *
     * @param message message
     * @param transaction transaction
     */
    constructor(message: String, transaction: Transaction?, cause: Throwable) : this(message) {
        this.transaction = transaction
        this.rootCause = cause
    }

    companion object {
        const val TRANSACTION_FAILED_TO_OPEN_FILE = "Failed to open transaction file"
        const val TRANSACTION_FAILED_TO_WRITE_FILE = "Failed to write to transaction file"
        const val TRANSACTION_FAILED_TO_READ_FILE = "Failed to read from a transaction file"
        const val TRANSACTION_FAILED_TO_RECOVER_FROM_DIRECTORY = "Failed to recover database.  The WAL directory does not exist or is not a directory"
        const val TRANSACTION_FAILED_TO_EXECUTE = "Failed to execute transaction."
    }
}
