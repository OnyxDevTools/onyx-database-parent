package com.onyx.diskmap.node;

import com.onyx.diskmap.exception.SerializationException;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by timothy.osborn on 3/21/15.
 *
 * This class is to represent the starting place for a structure implementation.  This is serialized first
 *
 */
public class Header implements ObjectSerializable
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
        return Long.hashCode(position);
    }


    /**
     * ObjectSerializable Interface
     *
     * Write to an Object Output Stream
     *
     * @param buffer Buffer to write to
     * @throws java.io.IOException Serialization exception
     */
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeLong(firstNode);
        buffer.writeLong(recordCount.get());
        buffer.writeLong(position);
    }

    /**
     * Read from an Object Buffer
     *
     * @param buffer Object buffer to read from
     * @throws java.io.IOException Serialization exception
     */
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        firstNode = buffer.readLong();

        if(recordCount == null)
            recordCount = new AtomicLong(buffer.readLong());
        else
            recordCount.set(buffer.readLong());

        position = buffer.readLong();
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
}
