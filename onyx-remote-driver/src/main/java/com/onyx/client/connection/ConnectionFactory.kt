package com.onyx.client.connection

import com.onyx.buffer.NetworkBufferPool
import com.onyx.client.base.ConnectionProperties
import com.onyx.client.base.engine.PacketTransportEngine

object ConnectionFactory {

    @Synchronized
    fun recycle(buffer: ConnectionProperties) {
        buffer.messages.forEach { it.value.packets.forEach { NetworkBufferPool.recycle(it.packetBuffer) } }
        buffer.messages.clear()
    }

    @Synchronized
    fun create(engine: PacketTransportEngine):ConnectionProperties = ConnectionProperties(engine)
}

