package com.onyx.lang.concurrent.impl

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.lang.concurrent.AtomicCounter
import com.onyx.exception.BufferingException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Tim Osborn on 3/2/17.
 *
 *
 * Default implementation of the atomic counter
 *
 * @since 1.3.0
 */
class DefaultAtomicCounter(initialValue: Int = 0) : AtomicCounter, BufferStreamable {

    private var aLong: AtomicInteger = AtomicInteger(initialValue)

    /**
     * Set long value
     *
     * @param value count
     */
    override fun set(value: Int) = this.aLong.set(value)

    /**
     * Get counter value
     *
     * @return current value
     */
    override fun get(): Int = this.aLong.get()

    /**
     * Add value and add more
     *
     * @param more How many more bytes
     * @return The current value
     */
    override fun getAndAdd(more: Int): Int = this.aLong.getAndAdd(more)

    /**
     * Read from buffer
     *
     * @param buffer Buffer Stream to read from
     * @throws BufferingException General exception
     */
    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        this.aLong = AtomicInteger(buffer.int)
    }

    /**
     * Write to a buffer
     *
     * @param buffer Buffer IO Stream to write to
     * @throws BufferingException cannot write
     */
    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) = buffer.putInt(aLong.get())

}
