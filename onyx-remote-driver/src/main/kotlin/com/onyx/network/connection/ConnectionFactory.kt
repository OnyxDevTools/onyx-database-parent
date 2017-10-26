package com.onyx.network.connection

import com.onyx.buffer.NetworkBufferPool
import com.onyx.network.transport.engine.PacketTransportEngine

object ConnectionFactory {

    @Synchronized
    fun recycle(buffer: Connection) {
        buffer.messages.forEach { it.value.packets.forEach { NetworkBufferPool.recycle(it.packetBuffer) } }
        buffer.messages.clear()
    }

    @Synchronized
    fun create(engine: PacketTransportEngine): Connection = Connection(engine)
}

