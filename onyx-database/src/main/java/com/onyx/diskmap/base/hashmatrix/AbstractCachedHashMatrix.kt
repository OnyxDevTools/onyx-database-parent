package com.onyx.diskmap.base.hashmatrix

import com.onyx.diskmap.data.HashMatrixNode
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.store.Store
import java.util.*

/**
 * This class implements the caching of a hash matrix data.  It is a thin wrapper that first checks
 * the data cache.
 *
 * @param <K> Key
 * @param <V> Value
 *
 * @since 1.2.0
 */
abstract class AbstractCachedHashMatrix<K, V>(fileStore: Store, header: Header, detached: Boolean) : AbstractHashMatrix<K, V>(fileStore, header, detached) {

    protected var hashMatrixNodeCache: MutableMap<Long, HashMatrixNode> = Collections.synchronizedMap(WeakHashMap<Long, HashMatrixNode>())

    /**
     * Get Hash Matrix Node.  This will check the cache first.  If it does not exist in the cache, check the volume
     * and put the result into the cache.
     *
     * @param position Position to find the data
     * @return Node retrieved from the cache or the volume
     *
     * @since 1.2.0
     */
    override fun getHashMatrixNode(position: Long): HashMatrixNode {
        var node: HashMatrixNode? = hashMatrixNodeCache[position]

        if (node == null) {
            node = super.getHashMatrixNode(position)
            hashMatrixNodeCache[position] = node
        }

        return node
    }

    /**
     * Write the hash matrix data to the cache and then the store.
     *
     * @param position  Position to write the data to
     * @param node Node to write to disk
     *
     * @since 1.2.0
     */
    override fun writeHashMatrixNode(position: Long, node: HashMatrixNode) {
        hashMatrixNodeCache[position] = node
        super.writeHashMatrixNode(position, node)
    }

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
    override fun updateHashMatrixReference(node: HashMatrixNode, index: Int, value: Long) {
        super.updateHashMatrixReference(node, index, value)
        hashMatrixNodeCache[node.position] = node
    }

    /**
     * Clear the hash matrix data cache
     *
     * @since 1.2.0
     */
    override fun clear() {
        super.clear()
        this.hashMatrixNodeCache.clear()
    }

}
