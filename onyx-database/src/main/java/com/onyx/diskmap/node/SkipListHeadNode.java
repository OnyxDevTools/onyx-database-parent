package com.onyx.diskmap.node;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;
import com.onyx.persistence.context.SchemaContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by tosborn1 on 1/9/17.
 *
 * This is a head of a skip list level.
 */
public class SkipListHeadNode implements BufferStreamable {

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
    public void read(BufferStream buffer) throws BufferingException {
        next = buffer.getLong();
        down = buffer.getLong();
        level = buffer.getByte();
//        position = buffer.getLong();
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putLong(next);
        buffer.putLong(down);
        buffer.putByte(level);
//        buffer.putLong(position);
    }

    @Override
    public void write(@NotNull BufferStream buffer, @Nullable SchemaContext context) throws BufferingException {
        write(buffer);
    }

    @Override
    public void read(@NotNull BufferStream buffer, @Nullable SchemaContext context) throws BufferingException {
        read(buffer);
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
