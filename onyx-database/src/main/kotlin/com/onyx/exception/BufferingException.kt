package com.onyx.exception

/**
 * Created by Tim Osborn on 8/1/16.
 *
 * This class indicates an issue when trying to serialize and de-serialize using the buffering mechanism
 */
open class BufferingException : OnyxException {

    /**
     * Default Constructor with message
     *
     * @param message Error message
     */
    @JvmOverloads
    constructor(message: String? = "") : super(message)

    /**
     * Constructor with message and class attempted to expandableByteBuffer
     * @param message error message
     * @param clazz class to add to error message
     */
    constructor(message: String, clazz: Class<*>?) : super(message + if (clazz != null) clazz.name else "null")

    /**
     * Constructor with message and class attempted to expandableByteBuffer
     * @param message error message
     * @param clazz class to add to error message
     */
    constructor(message: String, clazz: Class<*>?, cause:Exception) : super(message + if (clazz != null) clazz.name else "null", cause)

    companion object {

        @JvmField val UNKNOWN_DESERIALIZE = "Unknown exception occurred while de-serializing "
        @JvmField val CANNOT_INSTANTIATE = "Cannot instantiate class "
        @JvmField val UNKNOWN_CLASS = "Unknown class "
        @JvmField val ILLEGAL_ACCESS_EXCEPTION = "Illegal Access Exception "
    }
}

