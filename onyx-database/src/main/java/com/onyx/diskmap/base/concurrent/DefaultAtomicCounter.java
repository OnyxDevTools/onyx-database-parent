package com.onyx.diskmap.base.concurrent;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;
import com.onyx.persistence.context.SchemaContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by tosborn1 on 3/2/17.
 * <p>
 * Default iplementation of the atomic counter
 *
 * @since 1.3.0
 */
public class DefaultAtomicCounter implements AtomicCounter, BufferStreamable {

    private AtomicLong aLong;

    /**
     * Constructor
     */
    @SuppressWarnings("unused")
    public DefaultAtomicCounter() {
        aLong = new AtomicLong(0);
    }

    /**
     * Constructor with initial value
     *
     * @param initialValue value to set counter to
     */
    public DefaultAtomicCounter(@SuppressWarnings("SameParameterValue") long initialValue) {
        this.aLong = new AtomicLong(initialValue);
    }

    /**
     * Set long value
     *
     * @param value count
     */
    @Override
    public void set(long value) {
        this.aLong.set(value);
    }

    /**
     * Get counter value
     *
     * @return current value
     */
    @Override
    public long get() {
        return this.aLong.get();
    }

    /**
     * Add value and add more
     *
     * @param more How many more bytes
     * @return The current value
     */
    @Override
    public long getAndAdd(int more) {
        return this.aLong.getAndAdd(more);
    }

    /**
     * Read from buffer
     *
     * @param buffer Buffer Stream to read from
     * @throws BufferingException General exception
     */
    @Override
    public void read(BufferStream buffer) throws BufferingException {
        this.aLong = new AtomicLong(buffer.getLong());
    }

    /**
     * Write to a buffer
     *
     * @param buffer Buffer IO Stream to write to
     * @throws BufferingException cannot write
     */
    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putLong(aLong.get());
    }

    @Override
    public void write(@NotNull BufferStream buffer, @Nullable SchemaContext context) throws BufferingException {
        write(buffer);
    }

    @Override
    public void read(@NotNull BufferStream buffer, @Nullable SchemaContext context) throws BufferingException {
        read(buffer);
    }
}
