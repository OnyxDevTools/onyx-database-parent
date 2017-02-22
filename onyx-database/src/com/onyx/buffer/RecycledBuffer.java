package com.onyx.buffer;

import java.nio.ByteBuffer;

/**
 * Created by tosborn1 on 8/2/16.
 *
 * Wrapper to sort a buffer by the capacity.  This is used to grab the smallest available buffer that fits our needs.
 *
 */
class RecycledBuffer implements Comparable<RecycledBuffer> {

    private ByteBuffer buffer;
    private final int capacity;

    /**
     * Constructor with ByteBuffer
     *
     * @param buffer ByteBuffer to sort
     */
    @SuppressWarnings("unused")
    RecycledBuffer(ByteBuffer buffer) {
        buffer.limit(buffer.capacity());
        buffer.rewind();
        this.buffer = buffer;
        this.capacity = buffer.capacity();
    }

    /**
     * Getter for buffer's capacity
     * @return The buffer's original capacity.
     */
    int capacity()
    {
        return capacity;
    }

    /**
     * Constructor with capacity.  This is used when just wanting to compare capacity and not the bytebuffer.
     * Used by the higher method.
     * @param capacity The size of the buffer required.
     */
    @SuppressWarnings("unused")
    RecycledBuffer(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Hash Code of the buffer
     * @return The buffer hash code
     */
    @Override
    public int hashCode() {
        return buffer.hashCode();
    }

    /**
     * Buffer comparison
     *
     * @param obj buffer to compare
     * @return if the 2 buffers are the same
     */
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object obj) {
        return buffer instanceof ByteBuffer && buffer.equals(obj);
    }

    /**
     * Comparison method to sort the byte buffers based on capacity
     *
     * @param o Other ReclaimedBuffer to compare
     * @return The sort order
     */
    @Override
    public int compareTo(RecycledBuffer o) {
        return new Integer(capacity).compareTo(o.capacity);
    }

    /**
     * Getter for byte buffer
     * @return The byte buffer initialized with
     */
    public ByteBuffer getBuffer()
    {
        return this.buffer;
    }
}
