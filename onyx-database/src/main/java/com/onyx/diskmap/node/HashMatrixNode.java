package com.onyx.diskmap.node;

import com.onyx.buffer.BufferObjectType;
import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;
import com.onyx.persistence.context.SchemaContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/**
 * Created by timothy.osborn on 3/25/15.
 *
 * This is a node for sifting through a hash matrix to find the end of the chain that points to a skip list node
 */
public class HashMatrixNode implements BufferStreamable, Serializable
{
    public static final int DEFAULT_BITMAP_ITERATIONS = 10;

    public long[] next;
    public long position;

    public HashMatrixNode()
    {
        next = new long[DEFAULT_BITMAP_ITERATIONS];
    }

    @Override
    public int hashCode()
    {
        return (int)(position ^ (position >>> 32));
    }

    @Override
    public boolean equals(Object val)
    {
        return (val instanceof HashMatrixNode && ((HashMatrixNode) val).position == position);
    }

    @Override
    public void read(BufferStream buffer) throws BufferingException {
        this.position = buffer.getLong();
        this.next = (long[])buffer.getArray(BufferObjectType.LONG_ARRAY);
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putLong(position);
        buffer.putArray(next);
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
