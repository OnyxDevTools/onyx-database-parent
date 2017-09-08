package com.onyx.exception

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Error trying to start database or connection
 */
class InitializationException : OnyxException {

    /**
     * Constructor
     *
     * @param message Error message
     * @param cause Root cause
     */
    constructor(message: String, cause: Throwable) : super(message, cause)

    /**
     * Constructor
     *
     * @param message Error messsage
     */
    @JvmOverloads
    constructor(message: String = "") : super(message)

    companion object {
        @JvmField val DATABASE_FILE_PERMISSION_ERROR = "Exception occurred when initializing the database.  The data file may not have valid permissions."
        @JvmField val DATABASE_SHUTDOWN = "The database instance is in the process of shutting down"
        @JvmField val DATABASE_LOCKED = "The database instance is locked by another process."
        @JvmField val INVALID_CREDENTIALS = "Cannot connect to database, invalid credentials"
        @JvmField val UNKNOWN_EXCEPTION = "Exception occurred when initializing the database."
        @JvmField val CONNECTION_EXCEPTION = "Cannot connect to database, endpoint is not reachable"
    }
}
