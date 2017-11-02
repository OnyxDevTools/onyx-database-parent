package com.onyx.buffer

import java.nio.ByteBuffer
import java.util.*
import kotlin.NoSuchElementException

/**
 * This object contains a buffer pool to be used by network processes.  It differs from BufferPool because
 * it is not for specific database usages.  It typically will contain larger buffers.
 *
 * @since 2.0.0
 * @author Tim Osborn
 */
object NetworkBufferPool {

    private val bufferPool = LinkedList<ByteBuffer>()
    private var isInitialized = false
    private var numberOfBuffers = 100
    var bufferSize = 0

    /**
     * Set the buffer size and allocate buffers
     *
     * @param bufferSize of the byte buffer to be allocated
     * @param numberOfBuffers Number of buffers to be allocated
     */
    @Synchronized
    fun init(bufferSize:Int, numberOfBuffers:Int = 100) {
        if(!isInitialized) {
            this.numberOfBuffers = numberOfBuffers
            this.bufferSize = bufferSize
            bufferPool.clear()
            for (i in 0..numberOfBuffers) bufferPool.add(ByteBuffer.allocateDirect(bufferSize))
        }
        isInitialized = true
    }

    /**
     * Allocate a buffer.  If one is available return it otherwise, create a new byte buffer
     *
     * @since 2.0.0
     */
    @Synchronized
    fun allocate():ByteBuffer = try { bufferPool.removeFirst() } catch (e:NoSuchElementException) {
        ByteBuffer.allocateDirect(bufferSize)
    }

    /**
     * Recycle a buffer to be re-used
     *
     * @since 2.0.0
     */
    @Synchronized
    fun recycle(buffer: ByteBuffer) {
        buffer.clear()
        if(bufferPool.size < numberOfBuffers)
            bufferPool.addLast(buffer)
    }

    /**
     * With buffer closure.  At the end of it, recycle the buffer.
     *
     * @since 2.0.0
     */
    fun <T> withBuffer(buffer: ByteBuffer, body:(ByteBuffer) -> T):T = try {
        body.invoke(buffer)
    } finally {
        recycle(buffer)
    }

}
