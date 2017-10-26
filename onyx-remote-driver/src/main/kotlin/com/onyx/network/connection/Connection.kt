package com.onyx.network.connection

import com.onyx.buffer.BufferPool
import com.onyx.buffer.NetworkBufferPool
import com.onyx.network.transport.data.Message
import com.onyx.network.transport.engine.PacketTransportEngine
import java.nio.ByteBuffer

/**
 * Created by tosborn1 on 2/12/17.
 *
 * This contains the connection information including the buffers and the thread pool
 * that each connection is assigned to.
 * @since 1.2.0
 */
class Connection(

        var packetTransportEngine: PacketTransportEngine,
        var writeNetworkData: ByteBuffer = BufferPool.allocateAndLimit(NetworkBufferPool.bufferSize),
        var readNetworkData: ByteBuffer = BufferPool.allocateAndLimit(NetworkBufferPool.bufferSize)) {

    var isAuthenticated = false
    var messages = HashMap<Short, Message>()
}