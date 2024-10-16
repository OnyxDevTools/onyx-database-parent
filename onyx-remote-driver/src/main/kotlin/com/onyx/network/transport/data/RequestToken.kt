package com.onyx.network.transport.data

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException
import com.onyx.persistence.context.SchemaContext

/**
 * Created by Tim Osborn on 2/10/17.
 *
 * This class is a token that is sent back and fourth between the client and server.
 */
class RequestToken() : BufferStreamable {

    var token: Short = 0
    var packet: Any? = null

    constructor(token: Short, packet: Any?): this() {
        this.token = token
        this.packet = packet
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        token = buffer.short
        packet = buffer.value
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putShort(token)
        buffer.putObject(packet)
    }

    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream, context: SchemaContext?) {
        this.read(buffer)
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream, context: SchemaContext?) {
        this.write(buffer)
    }

    override fun hashCode(): Int = token.toInt()

    override fun equals(other: Any?): Boolean = other != null && other is RequestToken && other.token == token

}