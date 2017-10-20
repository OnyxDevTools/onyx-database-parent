package com.onyx.client

import com.onyx.buffer.BufferStreamable
import java.nio.ByteBuffer

class Packet (var packetSize:Short, var messageId:Short, var packetBuffer: ByteBuffer):BufferStreamable {
    constructor(buffer: ByteBuffer) :this(buffer.short, buffer.short, buffer)
}

fun Packet.write(buffer: ByteBuffer) {
    buffer.position(0)
    buffer.putShort(packetSize)
    buffer.putShort(messageId)
}

