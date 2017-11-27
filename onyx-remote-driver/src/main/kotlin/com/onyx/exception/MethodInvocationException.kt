package com.onyx.exception

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable

/**
 * Created by Tim Osborn on 7/1/16.
 *
 * This indicates a problem when invoking a remote method.
 * @since 1.2.0
 */
class MethodInvocationException : OnyxServerException, BufferStreamable {

    /**
     * Default Constructor with message
     * @since 1.2.0
     */
    constructor() {
        this.message = MethodInvocationException.NO_REGISTERED_OBJECT
    }

    /**
     * Default constructor with message and root cause
     * @param message Error message
     * @param cause Root cause
     * @since 1.2.0
     */
    constructor(message: String, cause: Throwable) : super(message, cause)

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        this.cause = buffer.value as Throwable?
        this.message = buffer.string
        this.stack = buffer.string
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putObject(cause)
        buffer.putString(message!!)
        buffer.putString(this.stack)
    }

    companion object {
        val NO_SUCH_METHOD = "No Such Method"
        val NO_REGISTERED_OBJECT = "The remote value you are request does not exist!"
        val UNHANDLED_EXCEPTION = "Unhandled exception occurred when making RMI request."
    }
}
