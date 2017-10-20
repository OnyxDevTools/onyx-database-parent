package com.onyx.client.base

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.exception.BufferingException

import java.io.Serializable

/**
 * Created by tosborn1 on 2/10/17.
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
        packet = buffer.value as Serializable?
    }

    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) {
        buffer.putShort(token)
        buffer.putObject(packet)
    }

    override fun hashCode(): Int = token.toInt()

    override fun equals(other: Any?): Boolean = other != null && other is RequestToken && other.token == token


}