package com.onyx.structure.node;

import com.onyx.structure.exception.SerializationException;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;

/**
 * Created by tosborn1 on 9/7/16.
 */
public class LongRecordReference implements ObjectSerializable {

    public long next;
    public long position;
    public long value;

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException {
        buffer.writeLong(next);
        buffer.writeLong(position);
        buffer.writeLong(value);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException {
        next = buffer.readLong();
        position = buffer.readLong();
        value = buffer.readLong();
    }

    @Override
    public void readObject(ObjectBuffer buffer, long checksum) throws IOException{
        readObject(buffer);
        if(this.position != checksum)
            throw new SerializationException();
    }

}
