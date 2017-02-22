package com.onyx.diskmap.base.hashmatrix;

import com.onyx.diskmap.base.concurrent.ConcurrentWeakHashMap;
import com.onyx.diskmap.node.HashMatrixNode;
import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.store.Store;

import java.util.Map;

/**
 * This class implements the caching of a hash matrix node.  It is a thin wrapper that first checks
 * the node cache.
 *
 * @param <K> Key
 * @param <V> Value
 *
 * @since 1.2.0
 */
abstract class AbstractCachedHashMatrix<K, V> extends AbstractHashMatrix<K, V> {

    protected final Map<Long, HashMatrixNode> nodeCache;

    /**
     * Constructor, initializes cache
     *
     * @param fileStore Storage volume for hash matrix
     * @param header Head of the data structure
     * @param detached Whether the object is detached.  If it is detached, ignore writing
     *                 values to the header
     *
     * @since 1.2.0
     */
    AbstractCachedHashMatrix(Store fileStore, Header header, boolean detached) {
        super(fileStore, header, detached);
        nodeCache = new ConcurrentWeakHashMap<>();
    }

    /**
     * Get Hash Matrix Node.  This will check the cache first.  If it does not exist in the cache, check the volume
     * and put the result into the cache.
     *
     * @param position Position to find the node
     * @return Node retrieved from the cache or the volume
     *
     * @since 1.2.0
     */
    @Override
    protected HashMatrixNode getHashMatrixNode(final long position) {
        HashMatrixNode node = nodeCache.get(position);

        if (node == null) {
            node = super.getHashMatrixNode(position);
            nodeCache.put(position, node);
        }

        return node;
    }

    /**
     * Write the hash matrix node to the cache and then the store.
     *
     * @param position  Position to write the node to
     * @param node Node to write to disk
     *
     * @since 1.2.0
     */
    @Override
    protected void writeHashMatrixNode(final long position, final HashMatrixNode node) {
        nodeCache.put(position, node);
        super.writeHashMatrixNode(position, node);
    }

    /**
     * Update hash matrix reference.  This will not write the entire node, it will only update one of the sub references.
     *
     * Pre-requisite, the value must be defined within the node.  It will update the cache.
     *
     * @param node Hash Matrix node.
     * @param index Index of the reference within the next[] property for a node.
     * @param value New reference value
     *
     * @since 1.2.0
     */
    @Override
    public void updateHashMatrixReference(final HashMatrixNode node, final int index, final long value) {
        super.updateHashMatrixReference(node, index, value);
        nodeCache.put(node.position, node);
    }

    /**
     * Clear the hash matrix node cache
     *
     * @since 1.2.0
     */
    @Override
    public void clear()
    {
        super.clear();
        this.nodeCache.clear();
    }

}
