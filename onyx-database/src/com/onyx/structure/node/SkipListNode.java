package com.onyx.structure.node;

import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;

/**
 * Created by tosborn1 on 1/6/17.
 */
public class SkipListNode<K> implements ObjectSerializable {

    public static final int BASE_SKIP_LIST_NODE_SIZE = Long.BYTES * 3 + Byte.BYTES + Integer.BYTES * 2;
    public volatile byte level;
    public volatile long next;
    public volatile long down;
    public volatile long recordPosition;
    public volatile int recordSize;
    public int serializerId;
    public K key;

    // transient
    public long position;

    public SkipListNode(K key, long position, long recordPosition, byte level, long next, long down, int recordSize) {
        this.position = position;
        this.recordPosition = recordPosition;
        this.level = level;
        this.next = next;
        this.down = down;
        this.key = key;
        this.recordSize = recordSize;
    }

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException {
        buffer.writeLong(recordPosition);
        buffer.writeInt(recordSize);
        buffer.writeLong(next);
        buffer.writeLong(down);
        buffer.writeByte(level);
        buffer.writeInt(serializerId);
        buffer.writeObject(key);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException {
        recordPosition = buffer.readLong();
        recordSize = buffer.readInt();
        next = buffer.readLong();
        down = buffer.readLong();
        level = buffer.readByte();
        serializerId = buffer.readInt();
        key = (K)buffer.readObject();
    }

    public void readObject(ObjectBuffer buffer, long position) throws IOException
    {
        readObject(buffer);
    }

    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException
    {
        readObject(buffer);
    }

}
