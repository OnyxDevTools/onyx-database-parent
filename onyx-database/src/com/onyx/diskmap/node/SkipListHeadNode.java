package com.onyx.diskmap.node;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;
import com.onyx.exception.BufferingException;

import java.io.IOException;

/**
 * Created by tosborn1 on 1/9/17.
 *
 * This is a head of a skip list level.
 */
public class SkipListHeadNode implements ObjectSerializable, BufferStreamable {

    public static final int HEAD_SKIP_LIST_NODE_SIZE = Long.BYTES * 2 + Byte.BYTES;

    public byte level;
    public long next;
    public long down;
    public long position;

    public SkipListHeadNode()
    {

    }

    public SkipListHeadNode(byte level, long next, long down)
    {
        this.level = level;
        this.next = next;
        this.down = down;
    }

    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException {
        buffer.writeLong(next);
        buffer.writeLong(down);
        buffer.writeByte(level);
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException {
        next = buffer.readLong();
        down = buffer.readLong();
        level = buffer.readByte();
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException {
        readObject(buffer);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {
        readObject(buffer);
    }

    @Override
    public void read(BufferStream buffer) throws BufferingException {
        next = buffer.getLong();
        down = buffer.getLong();
        level = buffer.getByte();
        position = buffer.getLong();
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putLong(next);
        buffer.putLong(down);
        buffer.putByte(level);
        buffer.putLong(position);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof SkipListHeadNode && ((SkipListHeadNode) o).position == position);
    }

    @Override
    public int hashCode() {
        return (int)(position ^ (position >>> 32));
    }
}
