package com.onyx.buffer

import java.nio.Buffer
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Created by Tim Osborn on 8/2/16.
 *
 * This class is meant to encapsulate the automatic growing and shrinking of a byte expandableByteBuffer.  Also it acts as a reference
 * to a expandableByteBuffer as the underlying ByteBuffer can change.
 *
 */
class ExpandableByteBuffer {

    var buffer: ByteBuffer
    private var maxBufferSize = 0
    private var bufferStartingPosition = 0

    /**
     * Constructor with max size and starting position
     * @param buffer Buffer to read and write
     * @param maxBufferSize maximum size to read fro the expandableByteBuffer
     * @param bufferStartingPosition starting index of the expandableByteBuffer
     */
    constructor(buffer: ByteBuffer, maxBufferSize: Int, bufferStartingPosition: Int) {
        this.buffer = buffer
        this.maxBufferSize = maxBufferSize
        this.bufferStartingPosition = bufferStartingPosition
    }

    /**
     * Default Constructor with expandableByteBuffer.  This defaults the max expandableByteBuffer size to the maximum of an integer
     * and the starting position to 0
     *
     * @param buffer ByteBuffer to initialize with
     */
    constructor(buffer: ByteBuffer) {
        this.buffer = buffer
        this.maxBufferSize = Integer.MAX_VALUE
        this.bufferStartingPosition = 0
    }

    /**
     * Check to see if the buffer need additional bytes
     * @param required Number of additional required bytes
     * @return Whether the buffer already has enough bytes remaining
     */
    fun ensureRequiredSize(required: Int): Boolean = buffer.position() + required < maxBufferSize + bufferStartingPosition

    /**
     * Check size and ensure the expandableByteBuffer has enough space to accommodate
     *
     * @param needs How many more bytes to allocate if the buffer does not have enough
     */
    fun ensureSize(needs: Int) {
        require(needs >= 0) { "Required buffer size must not be negative: $needs" }

        val requiredCapacity = Math.addExact(buffer.position(), needs)
        if (buffer.limit() >= requiredCapacity) {
            return
        }

        if (buffer.capacity() >= requiredCapacity) {
            buffer.limit(buffer.capacity())
            return
        }

        val currentCapacity = buffer.capacity()
        val geometricCapacity = if (currentCapacity <= Int.MAX_VALUE / 2) {
            currentCapacity * 2
        } else {
            Int.MAX_VALUE
        }
        val tempBuffer = BufferPool.allocate(max(requiredCapacity, geometricCapacity))
        buffer.flip()
        tempBuffer.put(buffer)
        BufferPool.recycle(buffer)
        buffer = tempBuffer
    }

    /**
     * Append a buffer to this expandable buffer
     *
     * @since 2.0.0
     */
    fun put(other: ByteBuffer) {
        ensureSize(other.remaining())
        buffer.put(other)
    }

    /**
     * Flip the underlying buffer
     */
    fun flip(): Buffer = buffer.flip()

    companion object {
        const val BUFFER_ALLOCATION = BufferPool.SMALL_BUFFER_SIZE
    }

}
