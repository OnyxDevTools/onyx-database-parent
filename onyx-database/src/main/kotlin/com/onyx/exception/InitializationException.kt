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
     * @param message Error message
     */
    @JvmOverloads
    constructor(message: String = "") : super(message)

    companion object {
        const val DATABASE_FILE_PERMISSION_ERROR = "Exception occurred when initializing the database.  The data file may not have valid permissions."
        const val DATABASE_SHUTDOWN = "The database instance is in the process of shutting down"
        const val DATABASE_LOCKED = "The database instance is locked by another process."
        const val INVALID_CREDENTIALS = "Cannot connect to database, invalid credentials"
        const val UNKNOWN_EXCEPTION = "Exception occurred when initializing the database."
        const val CONNECTION_EXCEPTION = "Cannot connect to database, endpoint is not reachable"
        const val INVALID_ENTITY_LOCATION = "Text Searchable entities must have a fileName with a directory format ending in `/`"
    }
}
