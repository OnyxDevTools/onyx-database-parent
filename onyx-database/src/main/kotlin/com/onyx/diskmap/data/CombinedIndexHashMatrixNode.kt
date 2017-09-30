package com.onyx.diskmap.data

/**
 * Created by tosborn1 on 1/10/17.
 *
 * This class indicates both a bitmap and skip list combined data
 *
 * @since 1.2.0
 */
class CombinedIndexHashMatrixNode(var head: SkipListHeadNode, val bitMapNode: HashMatrixNode, val hashDigit: Int) {

    override fun hashCode(): Int = this.bitMapNode.position.hashCode()

    override fun equals(other: Any?): Boolean = other is CombinedIndexHashMatrixNode && other.bitMapNode.position == bitMapNode.position

}
