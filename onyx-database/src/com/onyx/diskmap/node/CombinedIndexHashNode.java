package com.onyx.diskmap.node;

import com.onyx.buffer.BufferStream;
import com.onyx.buffer.BufferStreamable;
import com.onyx.exception.BufferingException;

/**
 * Created by tosborn1 on 2/16/17.
 *
 * This indicates a head of a data structor for a Hash table with a child index of a skip list.
 */
public class CombinedIndexHashNode implements BufferStreamable {

    public SkipListHeadNode head;
    public int mapId;

    public CombinedIndexHashNode(final SkipListHeadNode base, final int mapId) {
        this.head = base;
        this.mapId = mapId;
    }

    @Override
    public int hashCode() {
        return mapId;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof CombinedIndexHashNode && ((CombinedIndexHashNode) o).mapId == mapId);
    }

    @Override
    public void read(BufferStream buffer) throws BufferingException {
        head = (SkipListHeadNode) buffer.getObject();
        mapId = buffer.getInt();
    }

    @Override
    public void write(BufferStream buffer) throws BufferingException {
        buffer.putObject(head);
        buffer.putInt(mapId);
    }
}
