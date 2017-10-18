package com.onyx.client.connection

import com.onyx.buffer.BufferPool
import com.onyx.client.base.ConnectionProperties
import com.onyx.client.base.engine.PacketTransportEngine
import kotlinx.coroutines.experimental.cancel
import kotlinx.coroutines.experimental.cancelChildren
import kotlinx.coroutines.experimental.newSingleThreadContext
import java.util.concurrent.atomic.AtomicInteger

object ConnectionFactory {

    private val contextCounter = AtomicInteger()

    @Synchronized
    fun recycle(buffer: ConnectionProperties) {
        buffer.readThread.cancel()
        buffer.writeThread.cancel()
        buffer.readThread.cancelChildren()
        buffer.writeThread.cancelChildren()
        BufferPool.recycle(buffer.readApplicationData)
        BufferPool.recycle(buffer.writeApplicationData)
        BufferPool.recycle(buffer.readNetworkData)
        BufferPool.recycle(buffer.writeNetworkData)
    }

    @Synchronized
    fun create(engine: PacketTransportEngine):ConnectionProperties {
        val readContext = newSingleThreadContext("Network IO Context " + contextCounter.incrementAndGet())
        val writeContext = newSingleThreadContext("Network IO Context " + contextCounter.incrementAndGet())
        return ConnectionProperties(engine, readContext, writeContext)
    }
}