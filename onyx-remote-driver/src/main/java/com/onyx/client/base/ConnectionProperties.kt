package com.onyx.client.base

import com.onyx.buffer.BufferPool
import com.onyx.client.base.engine.PacketTransportEngine

import java.nio.ByteBuffer
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Created by tosborn1 on 2/12/17.
 *
 * This contains the connection information including the buffers and the thread pool
 * that each connection is assigned to.
 * @since 1.2.0
 */
class ConnectionProperties(
        var packetTransportEngine: PacketTransportEngine,
        var readThread:CoroutineContext,
        var writeThread:CoroutineContext,
        var writeApplicationData: ByteBuffer = BufferPool.allocate(packetTransportEngine.applicationSize),
        var writeNetworkData: ByteBuffer = BufferPool.allocate(packetTransportEngine.packetSize),
        var readApplicationData: ByteBuffer = BufferPool.allocate(packetTransportEngine.applicationSize),
        var readNetworkData: ByteBuffer = BufferPool.allocate(packetTransportEngine.packetSize),
        var readOverflowData: ByteBuffer = BufferPool.allocate(packetTransportEngine.packetSize)) {

    @Volatile
    var isReading = false

    var isAuthenticated = false

    /**
     * Handles the remainder of the the buffer fro a read.  This is so that in the next
     * loop left over from the read, the connection retains the fail over.  If partial
     * packets are left orphaned, that would be bad.
     *
     * @since 1.2.0
     */
    fun handleConnectionRemainder() {
        // We have some left over data from the last read.  Lets use that for this next iteration
        if (readOverflowData.position() > 0) {
            readOverflowData.flip()
            readNetworkData.put(readOverflowData)
            readOverflowData.clear()
        }
    }
}