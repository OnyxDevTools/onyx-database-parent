package com.onyx.exception

import com.onyx.transaction.Transaction

/**
 * Created by tosborn1 on 3/25/16.
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
        @JvmField val TRANSACTION_FAILED_TO_OPEN_FILE = "Failed to open transaction file"
        @JvmField val TRANSACTION_FAILED_TO_WRITE_FILE = "Failed to write to transaction file"
        @JvmField val TRANSACTION_FAILED_TO_READ_FILE = "Failed to read from a transaction file"
        @JvmField val TRANSACTION_FAILED_TO_RECOVER_FROM_DIRECTORY = "Failed to recover database.  The WAL directory does not exist or is not a directory"
        @JvmField val TRANSACTION_FAILED_TO_EXECUTE = "Failed to execute transaction."
    }
}
