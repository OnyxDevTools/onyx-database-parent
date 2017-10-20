package com.onyx.client.base

import com.onyx.buffer.BufferPool
import com.onyx.buffer.NetworkBufferPool
import com.onyx.client.Message
import com.onyx.client.base.engine.PacketTransportEngine
import java.nio.ByteBuffer

/**
 * Created by tosborn1 on 2/12/17.
 *
 * This contains the connection information including the buffers and the thread pool
 * that each connection is assigned to.
 * @since 1.2.0
 */
class ConnectionProperties(

        var packetTransportEngine: PacketTransportEngine,
        var writeNetworkData: ByteBuffer = BufferPool.allocateAndLimit(NetworkBufferPool.bufferSize),
        var readNetworkData: ByteBuffer = BufferPool.allocateAndLimit(NetworkBufferPool.bufferSize)) {

    var isAuthenticated = false
    var messages = HashMap<Short, Message>()
}