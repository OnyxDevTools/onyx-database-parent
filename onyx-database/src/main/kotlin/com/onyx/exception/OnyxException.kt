package com.onyx.exception

/**
 * Created by timothy.osborn on 11/3/14.
 *
 *
 * Base exception for an entity
 */
open class OnyxException : Exception {

    @Transient internal var rootCause: Throwable? = null

    /**
     * Constructor with cause
     *
     * @param cause Root cause
     */
    constructor(cause: Throwable) : super(cause.localizedMessage) {
        this.rootCause = cause
    }

    /**
     * Constructor with error message
     *
     * @param message Exception message
     */
    @JvmOverloads
    constructor(message: String? = UNKNOWN_EXCEPTION) : super(message)

    /**
     * Constructor with message and cause
     *
     * @param message Exception message
     * @param cause Root cause
     */
    constructor(message: String? = UNKNOWN_EXCEPTION, cause: Throwable?) : super(message, cause)

    companion object {
        @JvmField val UNKNOWN_EXCEPTION = "Unknown exception occurred"
        @JvmField val CONNECTION_TIMEOUT = "Connection Timeout Occurred"
    }

}
