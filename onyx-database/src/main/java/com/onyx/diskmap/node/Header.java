package com.onyx.diskmap.node;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by timothy.osborn on 3/21/15.
 *
 * This class is to represent the starting place for a structure implementation.  This is serialized first
 *
 */
public class Header implements BufferStreamable
{
    public static final int HEADER_SIZE = (Long.BYTES * 3);

    public long firstNode;
    public long position;

    public AtomicLong recordCount = new AtomicLong(0);

    /**
     * Override equals key to compare all values
     *
     * @param val Object to compare agains
     * @return Whether the header = the parameter value
     */
    @Override
    public boolean equals(Object val) {
        return val == this || val instanceof Header && position == ((Header) val).position;
    }

    /**
     * Add hash code for use within maps to help identify
     *
     * @return hash code of the header position
     */
    @Override
    public int hashCode() {
        return (int)(position ^ (position >>> 32));
    }

    @Override
    public void read(BufferStream buffer) throws BufferingException {
        firstNode = buffer.getLong();

        if (recordCount == null)
            recordCount = new AtomicLong(buffer.getLong());
        else
            recordCount.set(buffer.getLong());

        position = buffer.getLong();
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putLong(firstNode);
        buffer.putLong(recordCount.get());
        buffer.putLong(position);
    }
}
