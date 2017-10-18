package com.onyx.client

import com.onyx.buffer.BufferStreamable
import java.nio.ByteBuffer

class Packet (var isMessagePacket:Byte, var messageId:Short, var packetSize:Short, var packetBuffer: ByteBuffer):BufferStreamable {
    constructor(buffer: ByteBuffer) :this(buffer.get(), buffer.short, buffer.short, buffer)
}

fun Packet.write(buffer: ByteBuffer) {
    buffer.position(0)
    buffer.put(this.isMessagePacket)
    buffer.putShort(messageId)
    buffer.putShort(packetSize)
}

