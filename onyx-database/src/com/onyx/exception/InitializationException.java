package com.onyx.exception;

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Error trying to start database or connection
 */
public class InitializationException extends EntityException
{
    public static final String DATABASE_FILE_PERMISSION_ERROR = "Exception occurred when initializing the database.  The data file may not have valid permissions.";
    public static final String DATABASE_SHUTDOWN = "The database instance is in the process of shutting down";
    public static final String DATABASE_LOCKED = "The database instance is locked by another process.";
    public static final String INVALID_CREDENTIALS = "Cannot connect to database, invalid credentials";
    public static final String UNKNOWN_EXCEPTION = "Exception occurred when initializing the database.";
    @SuppressWarnings("unused")
    public static final String CONNECTION_EXCEPTION = "Cannot connect to database, endpoint is not reachable";

    /**
     * Constructor
     *
     * @param message Error message
     * @param cause Root cause
     */
    public InitializationException(@SuppressWarnings("SameParameterValue") String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructor
     *
     * @param message Error messsage
     */
    public InitializationException(String message)
    {
        super(message);
    }

    @SuppressWarnings("unused")
    public InitializationException()
    {
        super();
    }
}
