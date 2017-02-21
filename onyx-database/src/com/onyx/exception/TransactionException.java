package com.onyx.exception;

import com.onyx.transaction.Transaction;

/**
 * Created by tosborn1 on 3/25/16.
 *
 * This error denotes a failure to interact with a transaction WAL file
 */
public class TransactionException extends EntityException
{

    protected Transaction transaction;

    public static final String TRANSACTION_FAILED_TO_OPEN_FILE = "Failed to open transaction file";
    public static final String TRANSACTION_FAILED_TO_WRITE_FILE = "Failed to write to transaction file";
    public static final String TRANSACTION_FAILED_TO_READ_FILE = "Failed to read from a transaction file";
    public static final String TRANSACTION_FAILED_TO_RECOVER_FROM_DIRECTORY = "Failed to recover database.  The WAL directory does not exist or is not a directory";
    public static final String TRANSACTION_FAILED_TO_EXECUTE = "Failed to execute transaction.";

    @SuppressWarnings("unused")
    public TransactionException()
    {
        super();
    }

    public TransactionException(String message)
    {
        super(message);
    }

    /**
     * Constructor with Transaction
     *
     * @param message message
     * @param transaction transaction
     */
    public TransactionException(String message, Transaction transaction, Throwable cause)
    {
        super(message);
        this.transaction = transaction;
        this.rootCause = cause;
    }

}
