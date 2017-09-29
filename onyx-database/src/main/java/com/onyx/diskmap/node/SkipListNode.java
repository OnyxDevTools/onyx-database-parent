package com.onyx.diskmap.node;

import com.onyx.buffer.BufferStream;
import com.onyx.exception.BufferingException;

/**
 * Created by tosborn1 on 1/6/17.
 *
 * This is a record pointer for a skip list.
 */
public class SkipListNode<K> extends SkipListHeadNode {

    public static final int BASE_SKIP_LIST_NODE_SIZE = Long.BYTES * 4 + Byte.BYTES + Integer.BYTES;
    public long recordPosition;
    public long recordId;
    public int recordSize;
    public K key;

    @SuppressWarnings("unused")
    public SkipListNode()
    {

    }

    public SkipListNode(K key, long position, long recordPosition, byte level, long next, long down, int recordSize, long recordId) {
        this.position = position;
        this.recordPosition = recordPosition;
        this.level = level;
        this.next = next;
        this.down = down;
        this.key = key;
        this.recordSize = recordSize;
        this.recordId = recordId;
    }

    @Override
    public void read(BufferStream buffer) throws BufferingException {
        recordPosition = buffer.getLong();
        recordSize = buffer.getInt();
        super.read(buffer);
        recordId = buffer.getLong();
        key = (K)buffer.getValue();
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putLong(recordPosition);
        buffer.putInt(recordSize);
        super.write(buffer);
        buffer.putLong(recordId);
        buffer.putObject(key);
    }

}
