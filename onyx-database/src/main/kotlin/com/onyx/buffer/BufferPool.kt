package com.onyx.buffer

import java.nio.ByteBuffer
import java.util.ArrayDeque

/**
 * Responsible for allocating and de-allocating byte buffers
 *
 * @since 2.0.0
 */
object BufferPool {

    private val NUMBER_SMALL_BUFFERS = 200
    private val NUMBER_MEDIUM_BUFFERS = 50
    private val NUMBER_LARGE_BUFFERS = 25

    private val SMALL_BUFFER_SIZE = 512
    val MEDIUM_BUFFER_SIZE = 1024 * 6
    private val LARGE_BUFFER_SIZE = 18 * 1024

    private val SMALL_BUFFER_POOL = ArrayDeque<ByteBuffer>(NUMBER_SMALL_BUFFERS)
    private val MEDIUM_BUFFER_POOL = ArrayDeque<ByteBuffer>(NUMBER_MEDIUM_BUFFERS)
    private val LARGE_BUFFER_POOL = ArrayDeque<ByteBuffer>(NUMBER_LARGE_BUFFERS)

    /**
     * Pre Allocate buffers.
     */
    init {
        for (i in 1..NUMBER_SMALL_BUFFERS)
            SMALL_BUFFER_POOL.add(ByteBuffer.allocateDirect(SMALL_BUFFER_SIZE))
        for (i in 1..NUMBER_MEDIUM_BUFFERS)
            MEDIUM_BUFFER_POOL.add(ByteBuffer.allocateDirect(MEDIUM_BUFFER_SIZE))
        for (i in 1..NUMBER_LARGE_BUFFERS)
            LARGE_BUFFER_POOL.add(ByteBuffer.allocateDirect(LARGE_BUFFER_SIZE))
    }

    /**
     * Recycle a byte buffer to be reused
     *
     * @param buffer byte buffer to recycle and reuse
     */
    fun recycle(buffer: ByteBuffer) {
        buffer.clear()
        when {
            buffer.capacity() >= LARGE_BUFFER_SIZE && LARGE_BUFFER_POOL.size < NUMBER_LARGE_BUFFERS -> synchronized(LARGE_BUFFER_POOL) { LARGE_BUFFER_POOL.offer(buffer) }
            buffer.capacity() >= MEDIUM_BUFFER_SIZE && MEDIUM_BUFFER_POOL.size < NUMBER_MEDIUM_BUFFERS -> synchronized(MEDIUM_BUFFER_POOL) { MEDIUM_BUFFER_POOL.offer(buffer) }
            buffer.capacity() >= SMALL_BUFFER_SIZE && SMALL_BUFFER_POOL.size < NUMBER_SMALL_BUFFERS -> synchronized(SMALL_BUFFER_POOL) { SMALL_BUFFER_POOL.offer(buffer) }
        }
    }

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     *
     * @param count Size to allocate
     * @return An Allocated ByteBuffer
     */
    fun allocate(count: Int): ByteBuffer = when {
        count <= SMALL_BUFFER_SIZE -> synchronized(SMALL_BUFFER_POOL) { SMALL_BUFFER_POOL.poll() }
        count <= MEDIUM_BUFFER_SIZE -> synchronized(MEDIUM_BUFFER_POOL) { MEDIUM_BUFFER_POOL.poll() }
        count <= LARGE_BUFFER_SIZE -> synchronized(LARGE_BUFFER_POOL) { LARGE_BUFFER_POOL.poll() }
        else -> ByteBuffer.allocateDirect(count)
    }

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     *
     * @param count Size to allocate
     * @return An Allocated ByteBuffer and limit to the amount of bytes
     */
    fun allocateAndLimit(count: Int): ByteBuffer {
        val buffer = allocate(count)
        buffer.limit(count)
        return buffer
    }
}
