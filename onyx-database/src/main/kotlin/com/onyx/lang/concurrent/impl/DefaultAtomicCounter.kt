package com.onyx.lang.concurrent.impl

import com.onyx.buffer.BufferStream
import com.onyx.buffer.BufferStreamable
import com.onyx.lang.concurrent.AtomicCounter
import com.onyx.exception.BufferingException
import com.onyx.persistence.context.SchemaContext

import java.util.concurrent.atomic.AtomicLong

/**
 * Created by Tim Osborn on 3/2/17.
 *
 *
 * Default implementation of the atomic counter
 *
 * @since 1.3.0
 */
class DefaultAtomicCounter(initialValue: Long = 0L) : AtomicCounter, BufferStreamable {

    private var aLong: AtomicLong = AtomicLong(initialValue)

    /**
     * Set long value
     *
     * @param value count
     */
    override fun set(value: Long) = this.aLong.set(value)

    /**
     * Get counter value
     *
     * @return current value
     */
    override fun get(): Long = this.aLong.get()

    /**
     * Add value and add more
     *
     * @param more How many more bytes
     * @return The current value
     */
    override fun getAndAdd(more: Int): Long = this.aLong.getAndAdd(more.toLong())

    /**
     * Read from buffer
     *
     * @param buffer Buffer Stream to read from
     * @throws BufferingException General exception
     */
    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream) {
        this.aLong = AtomicLong(buffer.long)
    }

    /**
     * Write to a buffer
     *
     * @param buffer Buffer IO Stream to write to
     * @throws BufferingException cannot write
     */
    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream) = buffer.putLong(aLong.get())

    /**
     * Read from buffer
     *
     * @param buffer Buffer Stream to read from
     * @param context Schema Context
     * @throws BufferingException General exception
     */
    @Throws(BufferingException::class)
    override fun read(buffer: BufferStream, context: SchemaContext?) {
        this.aLong = AtomicLong(buffer.long)
    }

    /**
     * Write to a buffer
     *
     * @param buffer Buffer IO Stream to write to
     * @param context Schema Context
     * @throws BufferingException cannot write
     */
    @Throws(BufferingException::class)
    override fun write(buffer: BufferStream, context: SchemaContext?) = buffer.putLong(aLong.get())

}
