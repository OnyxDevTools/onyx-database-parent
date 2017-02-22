package com.onyx.diskmap.node;

/**
 * Created by tosborn1 on 2/16/17.
 *
 * This indicates a head of a data structor for a Hash table with a child index of a skip list.
 */
public class CombinedIndexHashNode {

    public final int hashDigit;
    public SkipListHeadNode head;
    public final int mapId;

    public CombinedIndexHashNode(final SkipListHeadNode base, final int mapId, int hashDigit) {
        this.head = base;
        this.mapId = mapId;
        this.hashDigit = hashDigit;
    }

    @Override
    public int hashCode() {
        return mapId;
    }

}
