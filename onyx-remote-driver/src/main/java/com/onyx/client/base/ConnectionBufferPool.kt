package com.onyx.client.base

import com.onyx.buffer.BufferPool
import com.onyx.lang.concurrent.ClosureLock
import com.onyx.lang.concurrent.impl.DefaultClosureLock
import kotlinx.coroutines.experimental.newSingleThreadContext
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.CoroutineContext


/**
 * Created by tosborn1 on 2/12/17.
 *
 * This class is a reference to the connection's buffer and threading mechanisms.
 *
 * @since 1.2.0
 */
open class ConnectionBufferPool {

    var readThread:CoroutineContext = newSingleThreadContext("Network Read Thread " + threadCount.incrementAndGet())
    var writeThread:CoroutineContext = newSingleThreadContext("Network Write Thread " + threadCount.get())
    var writeApplicationData: ByteBuffer = ByteBuffer.allocate(0)
    var writeNetworkData: ByteBuffer = ByteBuffer.allocate(0)
    var readApplicationData: ByteBuffer = ByteBuffer.allocate(0)
    var readNetworkData: ByteBuffer = ByteBuffer.allocate(0)

    /**
     * Default Constructor
     * @since 1.2.0
     */
    internal constructor()

    /**
     * Constructor with allocation sizes
     * @param applicationBufferSize Size of application buffer
     * @param packetSize Size of network buffer
     *
     * @since 1.2.0
     */
    constructor(applicationBufferSize: Int, packetSize: Int) {
        writeApplicationData = BufferPool.allocate(applicationBufferSize)
        writeNetworkData = BufferPool.allocate(packetSize)
        readApplicationData = BufferPool.allocate(applicationBufferSize)
        readNetworkData = BufferPool.allocate(packetSize)
    }

    companion object {
        val threadCount = AtomicInteger(0)
    }

}
