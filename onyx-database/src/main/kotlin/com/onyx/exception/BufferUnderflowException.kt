package com.onyx.exception

/**
 * Created by Tim Osborn on 2/10/17.
 *
 */
class BufferUnderflowException : BufferingException {

    @JvmOverloads
    constructor(message: String? = "") : super(message)

    constructor(message: String, clazz: Class<*>) : super(message, clazz)

    companion object {

        @JvmField val BUFFER_UNDERFLOW = "Buffer Underflow exception "
    }
}
