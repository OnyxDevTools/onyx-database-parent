package com.onyx.structure.node;

import com.onyx.structure.exception.SerializationException;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

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
     * @param val
     * @return
     */
    @Override
    public boolean equals(Object val)
    {
        if(val == this)
        {
            return true;
        }
        if(val instanceof Header)
        {
            return position == ((Header) val).position;
        }
        return false;
    }

    /**
     * ObjectSerializable Interface
     *
     * Write to an Object Output Stream
     *
     * @param buffer
     * @throws java.io.IOException
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
     * @param buffer
     * @throws java.io.IOException
     * @throws ClassNotFoundException
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
