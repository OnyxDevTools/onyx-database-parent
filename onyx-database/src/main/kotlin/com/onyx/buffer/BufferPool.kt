package com.onyx.buffer

import com.onyx.extension.withBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * Responsible for allocating and de-allocating byte buffers
 *
 * @since 2.0.0
 */
object BufferPool {

    private val NUMBER_SMALL_BUFFERS = 100
    private val NUMBER_MEDIUM_BUFFERS = 25
    private val NUMBER_LARGE_BUFFERS = 10

    private val SMALL_BUFFER_SIZE = 512
    val MEDIUM_BUFFER_SIZE = 1024 * 6
    private val LARGE_BUFFER_SIZE = 18 * 1024

    private val SMALL_BUFFER_POOL = LinkedList<ByteBuffer>()
    private val MEDIUM_BUFFER_POOL = LinkedList<ByteBuffer>()
    private val LARGE_BUFFER_POOL = LinkedList<ByteBuffer>()

    /**
     * Pre Allocate buffers.
     */
    init {
        for (i in 1..NUMBER_SMALL_BUFFERS)
            SMALL_BUFFER_POOL.add(ByteBuffer.allocateDirect(SMALL_BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN))
        for (i in 1..NUMBER_MEDIUM_BUFFERS)
            MEDIUM_BUFFER_POOL.add(ByteBuffer.allocateDirect(MEDIUM_BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN))
        for (i in 1..NUMBER_LARGE_BUFFERS)
            LARGE_BUFFER_POOL.add(ByteBuffer.allocateDirect(LARGE_BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN))
    }

    /**
     * Recycle a byte buffer to be reused
     *
     * @param buffer byte buffer to recycle and reuse
     */
    fun recycle(buffer: ByteBuffer) {
        buffer.clear()
        // Clean the buffer also
        while(buffer.hasRemaining())
            buffer.put(0.toByte())
        buffer.clear()
        val capacity = buffer.capacity()
        when {
            capacity >= LARGE_BUFFER_SIZE && LARGE_BUFFER_POOL.size < NUMBER_LARGE_BUFFERS -> synchronized(LARGE_BUFFER_POOL) { LARGE_BUFFER_POOL.addFirst(buffer) }
            capacity >= MEDIUM_BUFFER_SIZE && MEDIUM_BUFFER_POOL.size < NUMBER_MEDIUM_BUFFERS -> synchronized(MEDIUM_BUFFER_POOL) { MEDIUM_BUFFER_POOL.addFirst(buffer) }
            capacity >= SMALL_BUFFER_SIZE && SMALL_BUFFER_POOL.size < NUMBER_SMALL_BUFFERS -> synchronized(SMALL_BUFFER_POOL) { SMALL_BUFFER_POOL.addFirst(buffer) }
        }
    }

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     *
     * @param count Size to allocate
     * @return An Allocated ByteBuffer
     */
    fun allocate(count: Int): ByteBuffer = try {
        when {
            count <= SMALL_BUFFER_SIZE && !SMALL_BUFFER_POOL.isEmpty() -> synchronized(SMALL_BUFFER_POOL) { SMALL_BUFFER_POOL.removeLast() }
            count <= MEDIUM_BUFFER_SIZE && !MEDIUM_BUFFER_POOL.isEmpty() -> synchronized(MEDIUM_BUFFER_POOL) { MEDIUM_BUFFER_POOL.removeLast() }
            count <= LARGE_BUFFER_SIZE && !LARGE_BUFFER_POOL.isEmpty() -> synchronized(LARGE_BUFFER_POOL) { LARGE_BUFFER_POOL.removeLast() }
            else -> ByteBuffer.allocate(count).order(ByteOrder.BIG_ENDIAN)
        }
    } catch (e:Exception) {
        ByteBuffer.allocate(count).order(ByteOrder.BIG_ENDIAN)
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

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     *
     * @param count Size to allocate
     * @return An Allocated ByteBuffer and limit to the amount of bytes
     */
    fun <T> allocateAndLimit(count: Int, body:(ByteBuffer)->T ): T {
        val buffer = allocate(count)
        buffer.limit(count)
        return withBuffer(buffer, body)
    }
}
