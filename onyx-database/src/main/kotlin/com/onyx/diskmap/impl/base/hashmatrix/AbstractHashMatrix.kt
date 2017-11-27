package com.onyx.diskmap.impl.base.hashmatrix

import com.onyx.buffer.BufferPool
import com.onyx.diskmap.data.HashMatrixNode
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.impl.DiskSkipListMap
import com.onyx.diskmap.store.Store

/**
 * This class is responsible for the store i/o that writes and reads the hash matrix nodes.  It inherits all of the
 * disk skip list functionality but, should only be responsible for the hash matrix i/o
 *
 * @param <K> Key
 * @param <V> Value
 *
 * @since 1.2.0 This was migrated from the AbstractBitMap and simplified to be only a part of an index rather than have
 * record management
 */
abstract class AbstractHashMatrix<K, V>(fileStore: Store, header: Header, detached: Boolean) : DiskSkipListMap<K, V>(fileStore, header, detached) {

    /**
     * Get the size of a hash matrix data.
     *
     * @since 1.2.0
     * @return Number of levels + position
     */
    protected val hashMatrixNodeSize: Int
        get() = java.lang.Long.BYTES * (HashMatrixNode.DEFAULT_BITMAP_ITERATIONS + 1) + Integer.SIZE

    /**
     * Update hash matrix reference.  This will not write the entire data, it will only update one of the sub references.
     *
     * Pre-requisite, the value must be defined within the data.  It will update the cache.
     *
     * @param node Hash Matrix data.
     * @param index Index of the reference within the next[] property for a data.
     * @param value New reference value
     *
     * @since 1.2.0
     */
    open fun updateHashMatrixReference(node: HashMatrixNode, index: Int, value: Long) {
        node.next[index] = value
        BufferPool.allocateAndLimit(java.lang.Long.BYTES) {
            it.putLong(value)
            it.flip()
            fileStore.write(it, node.position + (java.lang.Long.BYTES * index).toLong() + java.lang.Long.BYTES.toLong() + Integer.BYTES.toLong())
        }
    }

    /**
     * Get Hash Matrix Node.  Return the de-serialized value from the store
     *
     * @param position Position to find the data
     * @return Node retrieved from the cache or the volume
     *
     * @since 1.2.0
     */
    protected open fun getHashMatrixNode(position: Long): HashMatrixNode = fileStore.read(position, hashMatrixNodeSize, HashMatrixNode::class.java) as HashMatrixNode

    /**
     * Write the hash matrix data to the store.
     *
     * @param position  Position to write the data to
     * @param node Node to write to disk
     *
     * @since 1.2.0
     */
    protected open fun writeHashMatrixNode(position: Long, node: HashMatrixNode) {
        fileStore.write(node, node.position)
    }
}
