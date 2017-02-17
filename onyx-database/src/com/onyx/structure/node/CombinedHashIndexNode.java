package com.onyx.structure.node;

/**
 * Created by tosborn1 on 2/16/17.
 */
public class CombinedHashIndexNode {

    public int hashDigit;
    public volatile SkipListHeadNode head;
    public int mapId;

    public CombinedHashIndexNode(final SkipListHeadNode base, final int mapId, int hashDigit) {
        this.head = base;
        this.mapId = mapId;
        this.hashDigit = hashDigit;
    }

    @Override
    public int hashCode() {
        return mapId;
    }

}
