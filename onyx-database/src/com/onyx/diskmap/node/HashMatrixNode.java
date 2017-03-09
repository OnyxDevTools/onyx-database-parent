package com.onyx.diskmap.node;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.diskmap.exception.SerializationException;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;
import com.onyx.exception.BufferingException;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by timothy.osborn on 3/25/15.
 *
 * This is a node for sifting through a hash matrix to find the end of the chain that points to a skip list node
 */
public class HashMatrixNode implements ObjectSerializable, BufferStreamable, Serializable
{
    public static final int DEFAULT_BITMAP_ITERATIONS = 10;

    public long[] next;
    public long position;

    public HashMatrixNode()
    {
        next = new long[DEFAULT_BITMAP_ITERATIONS];
    }

    /**
     * Write Object
     *
     * @param buffer Object buffer to write to.
     * @throws java.io.IOException Serialization error
     */
    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeLong(position);
        buffer.writeLongArray(next);
    }

    /**
     * Read Object
     * @param buffer Object buffer to read from
     * @throws java.io.IOException Serialization error
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
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putLong(position);
    }
}
