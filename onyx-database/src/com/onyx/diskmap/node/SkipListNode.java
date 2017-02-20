package com.onyx.diskmap.node;

import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;

import java.io.IOException;

/**
 * Created by tosborn1 on 1/6/17.
 */
public class SkipListNode<K> extends SkipListHeadNode implements ObjectSerializable {

    public static final int BASE_SKIP_LIST_NODE_SIZE = Long.BYTES * 3 + Byte.BYTES + Integer.BYTES * 2;
    public long recordPosition;
    public int recordSize;
    public int serializerId;
    public K key;

    @SuppressWarnings("unused")
    public SkipListNode()
    {

    }

    public SkipListNode(K key, long position, long recordPosition, byte level, long next, long down, int recordSize, int serializerId) {
        this.position = position;
        this.recordPosition = recordPosition;
        this.level = level;
        this.next = next;
        this.down = down;
        this.key = key;
        this.recordSize = recordSize;
        this.serializerId = serializerId;
    }

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException {
        buffer.writeLong(recordPosition);
        buffer.writeInt(recordSize);
        super.writeObject(buffer);
        buffer.writeInt(serializerId);
        buffer.writeObject(key);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException {
        recordPosition = buffer.readLong();
        recordSize = buffer.readInt();
        super.readObject(buffer);
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
