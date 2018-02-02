package com.onyx.buffer

import com.onyx.extension.withBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.concurrent.getOrSet

/**
 * Responsible for allocating and de-allocating byte buffers
 *
 * @since 2.0.0
 */
object BufferPool {

    const private val NUMBER_SMALL_BUFFERS = 50
    const private val NUMBER_MEDIUM_BUFFERS = 20
    const private val NUMBER_LARGE_BUFFERS = 10

    const private val SMALL_BUFFER_SIZE = 512
    const val MEDIUM_BUFFER_SIZE = 1024 * 6
    const private val LARGE_BUFFER_SIZE = 18 * 1024

    private val SMALL_BUFFER_POOL = ArrayDeque<ByteBuffer>(NUMBER_SMALL_BUFFERS + 1)
    private val MEDIUM_BUFFER_POOL = ArrayDeque<ByteBuffer>(NUMBER_MEDIUM_BUFFERS + 1)
    private val LARGE_BUFFER_POOL = ArrayDeque<ByteBuffer>(NUMBER_LARGE_BUFFERS + 1)

    /**
     * Pre Allocate buffers.
     */
    init {
        for (i in 1..NUMBER_SMALL_BUFFERS)
            SMALL_BUFFER_POOL.add(allocateExact(SMALL_BUFFER_SIZE))
        for (i in 1..NUMBER_MEDIUM_BUFFERS)
            MEDIUM_BUFFER_POOL.add(allocateExact(MEDIUM_BUFFER_SIZE))
        for (i in 1..NUMBER_LARGE_BUFFERS)
            LARGE_BUFFER_POOL.add(allocateExact(LARGE_BUFFER_SIZE))
    }

    /**
     * Recycle a byte buffer to be reused
     *
     * @param buffer byte buffer to recycle and reuse
     */
    fun recycle(buffer: ByteBuffer) {
        buffer.clear()
        val capacity = buffer.capacity()
        when {
            capacity >= LARGE_BUFFER_SIZE && LARGE_BUFFER_POOL.size < NUMBER_LARGE_BUFFERS -> synchronized(LARGE_BUFFER_POOL) { LARGE_BUFFER_POOL.addLast(buffer) }
            capacity >= MEDIUM_BUFFER_SIZE && MEDIUM_BUFFER_POOL.size < NUMBER_MEDIUM_BUFFERS -> synchronized(MEDIUM_BUFFER_POOL) { MEDIUM_BUFFER_POOL.addLast(buffer) }
            capacity >= SMALL_BUFFER_SIZE && SMALL_BUFFER_POOL.size < NUMBER_SMALL_BUFFERS -> synchronized(SMALL_BUFFER_POOL) { SMALL_BUFFER_POOL.addLast(buffer) }
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
            count <= SMALL_BUFFER_SIZE && !SMALL_BUFFER_POOL.isEmpty() -> synchronized(SMALL_BUFFER_POOL) { SMALL_BUFFER_POOL.pollFirst() }
            count <= MEDIUM_BUFFER_SIZE && !MEDIUM_BUFFER_POOL.isEmpty() -> synchronized(MEDIUM_BUFFER_POOL) { MEDIUM_BUFFER_POOL.pollFirst() }
            count <= LARGE_BUFFER_SIZE && !LARGE_BUFFER_POOL.isEmpty() -> synchronized(LARGE_BUFFER_POOL) { LARGE_BUFFER_POOL.pollFirst() }
            else -> allocateExactHeap(count)
        }
    } catch (e:Exception) {
        allocateExactHeap(count)
    }

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     *
     * @param count Size to allocate
     * @return An Allocated ByteBuffer and limit to the amount of bytes
     */
    fun allocateExact(count: Int): ByteBuffer = ByteBuffer.allocateDirect(count).order(ByteOrder.BIG_ENDIAN)

    /**
     * Allocation that will encapsulate the endian as well as the allocation method
     *
     * @param count Size to allocate
     * @return An Allocated ByteBuffer and limit to the amount of bytes
     */
    private fun allocateExactHeap(count: Int): ByteBuffer = ByteBuffer.allocate(count).order(ByteOrder.BIG_ENDIAN)

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
    inline fun <T> allocateAndLimit(count: Int, body:(ByteBuffer)->T ): T {
        val buffer = allocate(count)
        buffer.limit(count)
        return withBuffer(buffer, body)
    }

    @Suppress("MemberVisibilityCanPrivate")
    val longBuffer:ThreadLocal<ByteBuffer> = ThreadLocal()
    @Suppress("MemberVisibilityCanPrivate")
    val intBuffer:ThreadLocal<ByteBuffer> = ThreadLocal()

    inline fun <T> withIntBuffer(block:(ByteBuffer) -> T):T {
        val buffer = intBuffer.getOrSet { allocateExact(java.lang.Integer.BYTES)}
        buffer.rewind()
        return block(buffer)
    }

    inline fun <T> withLongBuffer(block:(ByteBuffer) -> T):T {
        val buffer = longBuffer.getOrSet { allocateExact(java.lang.Long.BYTES) }
        buffer.rewind()
        return block(buffer)
    }

}
