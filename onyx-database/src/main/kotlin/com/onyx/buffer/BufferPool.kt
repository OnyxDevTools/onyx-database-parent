package com.onyx.buffer

import com.onyx.extension.withBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

/**
 * Responsible for allocating and de-allocating byte buffers
 *
 * @since 2.0.0
 */
object BufferPool {

    private const val NUMBER_SMALL_BUFFERS = 50
    private const val NUMBER_MEDIUM_BUFFERS = 20
    private const val NUMBER_LARGE_BUFFERS = 10

    private const val SMALL_BUFFER_SIZE = 512
    const val MEDIUM_BUFFER_SIZE = 1024 * 6
    private const val LARGE_BUFFER_SIZE = 18 * 1024

    private val SMALL_BUFFER_POOL = ConcurrentLinkedQueue<ByteBuffer>()
    private val MEDIUM_BUFFER_POOL = ConcurrentLinkedQueue<ByteBuffer>()
    private val LARGE_BUFFER_POOL = ConcurrentLinkedQueue<ByteBuffer>()

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
        val capacity = buffer.capacity()
        if (buffer.isDirect) {
            buffer.clear()
            when (capacity) {
                LARGE_BUFFER_SIZE -> LARGE_BUFFER_POOL.add(buffer)
                MEDIUM_BUFFER_SIZE -> MEDIUM_BUFFER_POOL.add(buffer)
                SMALL_BUFFER_SIZE -> SMALL_BUFFER_POOL.add(buffer)
                else -> {}
            }
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
            count <= SMALL_BUFFER_SIZE -> SMALL_BUFFER_POOL.poll()
            count <= MEDIUM_BUFFER_SIZE -> MEDIUM_BUFFER_POOL.poll()
            count <= LARGE_BUFFER_SIZE -> LARGE_BUFFER_POOL.poll()
            else -> allocateExactHeap(count)
        }
    } catch (e: Exception) {
        allocateExactHeap(count)
    } ?: allocateExactHeap(count)

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
    inline fun <T> allocateAndLimit(count: Int, body: (ByteBuffer) -> T): T {
        val buffer = allocate(count)
        buffer.limit(count)
        return withBuffer(buffer, body)
    }

    private val longBuffer: ThreadLocal<ByteBuffer> = ThreadLocal.withInitial {
        ByteBuffer.allocate(Long.SIZE_BYTES)
    }

    private val intBuffer: ThreadLocal<ByteBuffer> = ThreadLocal.withInitial {
        ByteBuffer.allocate(Int.SIZE_BYTES)
    }

    private val bigIntBuffer: ThreadLocal<ByteBuffer> = ThreadLocal.withInitial {
        ByteBuffer.allocate(5)
    }

    fun <T> withIntBuffer(block: (ByteBuffer) -> T): T {
        val byteBuffer = intBuffer.get()
        byteBuffer.rewind()
        return block(byteBuffer)
    }

    fun <T> withLongBuffer(block: (ByteBuffer) -> T): T {
        val byteBuffer = longBuffer.get()
        byteBuffer.rewind()
        return block(byteBuffer)
    }

    fun <T> withBigIntBuffer(block: (ByteBuffer) -> T): T {
        val byteBuffer = bigIntBuffer.get()
        byteBuffer.rewind()
        return block(byteBuffer)
    }
}

fun copy(src: ByteBuffer, dst: ByteBuffer): Int {
    val bytes = min(src.remaining(), dst.remaining())
    if (bytes > 0) {
        val originalLimit = src.limit()
        src.limit(src.position() + bytes)
        dst.put(src)
        src.limit(originalLimit)
    }
    return bytes
}
