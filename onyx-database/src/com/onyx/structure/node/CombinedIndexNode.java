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

    public CombinedIndexNode(final SkipListHeadNode base, final BitMapNode node, int hashDigit) {
        this.head = base;
        this.bitMapNode = node;
        this.hashDigit = hashDigit;
    }

    @Override
    public int hashCode()
    {
        return Long.hashCode(this.bitMapNode.position);
    }

    @Override
    public boolean equals(Object object)
    {
        return (object instanceof CombinedIndexNode && ((CombinedIndexNode) object).bitMapNode.position == bitMapNode.position);
    }

}
