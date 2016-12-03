package com.onyx.structure.node;

import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;

/**
 * Created by timothy.osborn on 3/25/15.
 */
public class RecordReference implements ObjectSerializable
{
    public static final int RECORD_REFERENCE_LIST_SIZE = (Integer.BYTES * 3) + Long.BYTES;

    public RecordReference()
    {

    }
    public int keySize;
    public int recordSize;
    public int serializerId;
    public long next;
    public long position;

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeInt(keySize);
        buffer.writeInt(recordSize);
        buffer.writeLong(next);
        buffer.writeInt(serializerId);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        keySize = buffer.readInt();
        recordSize = buffer.readInt();
        next = buffer.readLong();
        serializerId = buffer.readInt();
    }

    @Override
    public int hashCode()
    {
        return new Long(position).hashCode();
    }

    @Override
    public boolean equals(Object val)
    {
        if(val == null)
            return false;

        if(val instanceof RecordReference)
        {
            return (((RecordReference) val).position == position);
        }

        return false;
    }

    @Override
    public void readObject(ObjectBuffer buffer, long checksum) throws IOException
    {
        readObject(buffer);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

    }
}
