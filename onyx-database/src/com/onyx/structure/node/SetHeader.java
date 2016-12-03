package com.onyx.structure.node;

import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;

/**
 * Created by tosborn1 on 9/9/16.
 */
public class SetHeader extends Header implements ObjectSerializable {

    public static final int SET_HEADER_SIZE = Long.BYTES * 3;

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException {
        buffer.writeLong(firstNode);
        buffer.writeLong(position);
        buffer.writeLong(recordCount.get());
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException {
        firstNode = buffer.readLong();
        position = buffer.readLong();
        recordCount.set(buffer.readLong());
    }
}
