package com.onyx.diskmap.base.hashmatrix;

import com.onyx.diskmap.data.HashMatrixNode;
import com.onyx.diskmap.data.Header;
import com.onyx.diskmap.store.Store;
import com.onyx.util.map.CompatWeakHashMap;
import com.onyx.util.map.SynchronizedMap;

import java.util.Map;

/**
 * This class implements the caching of a hash matrix data.  It is a thin wrapper that first checks
 * the data cache.
 *
 * @param <K> Key
 * @param <V> Value
 *
 * @since 1.2.0
 */
abstract class AbstractCachedHashMatrix<K, V> extends AbstractHashMatrix<K, V> {

    protected Map<Long, HashMatrixNode> nodeCache;

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
        nodeCache = new SynchronizedMap<>(new CompatWeakHashMap<>());
    }

    /**
     * Get Hash Matrix Node.  This will check the cache first.  If it does not exist in the cache, check the volume
     * and put the result into the cache.
     *
     * @param position Position to find the data
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
     * Write the hash matrix data to the cache and then the store.
     *
     * @param position  Position to write the data to
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
    @Override
    public void updateHashMatrixReference(final HashMatrixNode node, final int index, final long value) {
        super.updateHashMatrixReference(node, index, value);
        nodeCache.put(node.getPosition(), node);
    }

    /**
     * Clear the hash matrix data cache
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
