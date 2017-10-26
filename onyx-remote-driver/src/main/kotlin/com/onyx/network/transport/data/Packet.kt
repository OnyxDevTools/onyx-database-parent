package com.onyx.network.transport.data

import com.onyx.buffer.BufferStreamable
import java.nio.ByteBuffer

/**
 * Individual transport packet.  This must be the same size as the socket buffer
 * so that each packet may be gobbled up in one swipe.
 *
 * @since 2.0.0
 */
class Packet (var packetSize:Int, var messageId:Short, var packetBuffer: ByteBuffer):BufferStreamable {
    constructor(buffer: ByteBuffer) :this(buffer.int, buffer.short, buffer)
}

/**
 * Write the packet to the buffer.  This will always put it at the beginning of the buffer
 *
 * @since 2.0.0
 */
fun Packet.write(buffer: ByteBuffer) {
    buffer.position(0)
    buffer.putInt(packetSize)
    buffer.putShort(messageId)
}

