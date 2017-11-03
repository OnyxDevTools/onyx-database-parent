package com.onyx.buffer

import java.nio.Buffer
import java.nio.ByteBuffer

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
        if (buffer.limit() < needs + buffer.position() && buffer.capacity() >= needs + buffer.position()) {
            buffer.limit(buffer.capacity())
        } else if (buffer.limit() < needs + buffer.position()) {
            val tempBuffer = BufferPool.allocate(buffer.limit() + needs + BUFFER_ALLOCATION)
            buffer.flip()
            tempBuffer.put(buffer)
            BufferPool.recycle(buffer)
            buffer = tempBuffer
        }
    }

    /**
     * Append a buffer to this expandable buffer
     *
     * @since 2.0.0
     */
    fun put(other: ByteBuffer) {
        ensureSize(other.limit())
        buffer.put(other)
    }

    /**
     * Flip the underlying buffer
     */
    fun flip(): Buffer = buffer.flip()

    companion object {
        val BUFFER_ALLOCATION = BufferPool.MEDIUM_BUFFER_SIZE // Initial Buffer allocation size 6KB
    }

}
