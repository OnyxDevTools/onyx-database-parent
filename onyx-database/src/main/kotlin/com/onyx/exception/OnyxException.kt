package com.onyx.exception

/**
 * Created by timothy.osborn on 11/3/14.
 *
 *
 * Base exception for an entity
 */
@Suppress("LeakingThis")
open class OnyxException : Exception {

    @Transient internal var rootCause: Throwable? = null

    override var message:String?

    /**
     * Constructor with cause
     *
     * @param cause Root cause
     */
    constructor(cause: Throwable) : this(cause.localizedMessage) {
        this.rootCause = cause
    }

    /**
     * Constructor with error message
     *
     * @param message Exception message
     */
    @JvmOverloads
    constructor(message: String? = UNKNOWN_EXCEPTION) : super(message) { this.message = message }

    /**
     * Constructor with message and cause
     *
     * @param message Exception message
     * @param cause Root cause
     */
    constructor(message: String? = UNKNOWN_EXCEPTION, cause: Throwable?) : super(message, cause) { this.message = message }

    companion object {
        const val UNKNOWN_EXCEPTION = "Unknown exception occurred"
    }

}
