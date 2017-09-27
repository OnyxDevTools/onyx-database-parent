package com.onyx.buffer;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by tosborn1 on 8/2/16.
 *
 * Wrapper to sort a buffer by the capacity.  This is used to grab the smallest available buffer that fits our needs.
 *
 */
class RecycledBuffer implements Comparable<RecycledBuffer> {

    private ByteBuffer buffer;
    private long bufferId;
    private static final AtomicLong bufferIdGenerator = new AtomicLong(0L);
    private int capacity;

    /**
     * Constructor with ByteBuffer
     *
     * @param buffer ByteBuffer to sort
     */
    @SuppressWarnings("unused")
    RecycledBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
        this.bufferId = bufferIdGenerator.getAndIncrement();
        this.capacity = buffer.capacity();
    }

    RecycledBuffer(int capacity) {
        this.capacity = capacity;
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
     * Hash Code of the buffer
     * @return The buffer hash code
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(bufferId);
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
        return obj instanceof RecycledBuffer && ((RecycledBuffer) obj).bufferId == this.bufferId;
    }

    /**
     * Comparison method to sort the byte buffers based on capacity
     *
     * @param o Other ReclaimedBuffer to compare
     * @return The sort order
     */
    @Override
    public int compareTo(RecycledBuffer o) {
        if(o.bufferId == bufferId) return 0;
        return (capacity >= o.capacity) ? 1 : -1;
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
