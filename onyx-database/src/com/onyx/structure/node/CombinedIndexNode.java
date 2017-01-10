package com.onyx.structure.node;

/**
 * Created by tosborn1 on 1/10/17.
 *
 * This class indicates both a bitmap and skip list combined node
 *
 * @since 1.2.0
 */
public class CombinedIndexNode {

    public BitMapNode bitMapNode;
    public int hashDigit;
    public SkipListHeadNode head;

    public CombinedIndexNode(SkipListHeadNode base, BitMapNode node, int hashDigit) {
        this.head = base;
        this.bitMapNode = node;
        this.hashDigit = hashDigit;
    }

}
