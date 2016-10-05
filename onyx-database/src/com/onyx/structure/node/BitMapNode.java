package com.onyx.structure.node;

import com.onyx.structure.exception.SerializationException;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by timothy.osborn on 3/25/15.
 */
public class BitMapNode implements ObjectSerializable, Serializable
{
    public static final int DEFAULT_BITMAP_ITERATIONS = 10;

    public long[] next = new long[DEFAULT_BITMAP_ITERATIONS];
    public long position;

    public BitMapNode()
    {

    }
    /**
     * Write Object
     *
     * @param buffer
     * @throws java.io.IOException
     */
    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeLong(position);
        buffer.writeLongArray(next);
    }

    /**
     * Read Object
     * @param buffer
     * @throws java.io.IOException
     */
    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        position = buffer.readLong();
        next = buffer.readLongArray(DEFAULT_BITMAP_ITERATIONS);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long checksum) throws IOException
    {
        readObject(buffer);
        if(this.position != checksum)
            throw new SerializationException();
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

    }

    @Override
    public int hashCode()
    {
        return new Long(position).hashCode();
    }

    @Override
    public boolean equals(Object val)
    {
        return (val instanceof BitMapNode && ((BitMapNode) val).position == position);
    }
}
