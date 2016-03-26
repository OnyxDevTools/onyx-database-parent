package com.onyx.exception;

/**
 * Created by timothy.osborn on 12/11/14.
 */
public class InitializationException extends EntityException
{
    public static final String DATABASE_FILE_PERMISSION_ERROR = "Exception occurred when initializing the database.  The data file may not have valid permissions.";
    public static final String DATABASE_SHUTDOWN = "The database instance is in the process of shutting down";
    public static final String DATABASE_LOCKED = "The database instance is locked by another process.";
    public static final String INVALID_CREDENTIALS = "Cannot connect to database, invalid credentials";
    public static final String CONNECTION_TIMEOUT = "Cannot connect to database, connection timeout";
    public static final String UNKNOWN_EXCEPTION = "Exception occurred when initializing the database.";
    public static final String DATABASE_ALREADY_INITIALIZED = "The database connection has already been initialized";
    public static final String CONNECTION_EXCEPTION = "Cannot connect to database, endpoint is not reachable";
    public static final String INVALID_URI = "Invalid URI when attempting to connect to a cluster entry";
    public static final String SYNCHRONIZATION = "Cannot synchronize server with replicated node";

    /**
     * Constructor
     *
     * @param message
     * @param cause
     */
    public InitializationException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructor
     *
     * @param message
     */
    public InitializationException(String message)
    {
        super(message);
    }

    public InitializationException()
    {
        super();
    }
}
