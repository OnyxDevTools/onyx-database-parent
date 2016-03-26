package com.onyx.map.node;

import com.onyx.map.exception.SerializationException;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.ObjectSerializable;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by timothy.osborn on 3/25/15.
 */
public class BitMapNode implements ObjectSerializable, Serializable
{
    public static int BITMAP_NODE_SIZE = 11*Long.BYTES;
    public static final int RECORD_REFERENCE_INDEX = 10;

    public long[] next = new long[RECORD_REFERENCE_INDEX];
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
        next = buffer.readLongArray(RECORD_REFERENCE_INDEX);
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
