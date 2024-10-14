package com.onyx.network.rmi.data

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException
import com.onyx.persistence.context.SchemaContext

/**
 * Created by Tim Osborn on 7/1/16.
 *
 * This is the main packet to send to the server for remote method invocation.
 * @since 1.2.0
 */
class RMIRequest @JvmOverloads constructor(var instance:String? = null, var method:Byte = 0.toByte(), var params:Array<Any?>? = null) : BufferStreamable {

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        instance = buffer.string
        method = buffer.byte
        @Suppress("UNCHECKED_CAST")
        params = buffer.value as Array<Any?>?
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putString(instance!!)
        buffer.putByte(method)
        buffer.putObject(params)
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream, context: SchemaContext?) {
        this.read(buffer)
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream, context: SchemaContext?) {
        this.write(buffer)
    }
}
