package com.onyx.buffer;

import java.nio.ByteBuffer;

/**
 * Created by tosborn1 on 8/2/16.
 *
 * This class is meant to encapsulate the automatic growing and shrinking of a byte expandableByteBuffer.  Also it acts as a reference
 * to a expandableByteBuffer as the underlying ByteBuffer can change.
 *
 */
class ExpandableByteBuffer
{

    // Default Buffer Size
    static final int BUFFER_ALLOCATION = 1024 * 5; // Initial Buffer allocation size 5kb

    public ByteBuffer buffer;
    private int maxBufferSize = 0;
    private int bufferStartingPosition = 0;

    /**
     * Constructor with max size and starting position
     * @param buffer Buffer to read and write
     * @param maxBufferSize maximum size to read fro the expandableByteBuffer
     * @param bufferStartingPosition starting index of the expandableByteBuffer
     */
    ExpandableByteBuffer(ByteBuffer buffer, int maxBufferSize, int bufferStartingPosition)
    {
        this.buffer = buffer;
        this.maxBufferSize = maxBufferSize;
        this.bufferStartingPosition = bufferStartingPosition;
    }

    /**
     * Default Constructor with expandableByteBuffer.  This defaults the max expandableByteBuffer size to the maximum of an integer
     * and the starting position to 0
     *
     * @param buffer ByteBuffer to initialize with
     */
    @SuppressWarnings("unused")
    ExpandableByteBuffer(ByteBuffer buffer)
    {
        this.buffer = buffer;
        this.maxBufferSize = Integer.MAX_VALUE;
        this.bufferStartingPosition = 0;
    }

    /**
     * Check to see if the buffer need additional bytes
     * @param required Number of additional required bytes
     * @return Whether the buffer already has enough bytes remaining
     */
    boolean ensureRequiredSize(int required)
    {
        return (buffer.position() + required) < (maxBufferSize + bufferStartingPosition);
    }

    /**
     * Check size and ensure the expandableByteBuffer has enough space to accommodate
     *
     * @param needs How many more bytes to allocate if the buffer does not have enough
     */
    void ensureSize(int needs) {
        if(buffer.limit() < needs + buffer.position()
                && buffer.capacity() >= needs + buffer.position())
        {
            buffer.limit(buffer.capacity());
        }
        else if (buffer.limit() < (needs + buffer.position())) {
            ByteBuffer tempBuffer = BufferStream.allocate(buffer.limit() + needs + BUFFER_ALLOCATION);
            buffer.limit(buffer.position());
            buffer.rewind();
            tempBuffer.put(buffer);
            buffer = tempBuffer;
        }
    }

}
