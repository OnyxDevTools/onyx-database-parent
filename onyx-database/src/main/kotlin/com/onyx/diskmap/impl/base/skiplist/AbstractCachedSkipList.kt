package com.onyx.diskmap.impl.base.skiplist

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.store.Store
import com.onyx.lang.map.WriteSynchronizedMap
import java.util.*

/**
 * Created by Tim Osborn on 1/7/17.
 *
 *
 * This class was added to enhance the existing index within Onyx Database.  The bitmap was very efficient but, it was a hog
 * as far as how much space it took over.  As far as in-memory data structures, this will be the go-to algorithm.  The
 * data structure is based on a SkipList.  This contains the caching implementation of the SkipList.  It will try to
 * maintain a weak reference of all the values.  If memory consumption goes to far, it will start flushing those
 * from the elastic cache.
 *
 * @param <K> Key Object Type
 * @param <V> Value Object Type
 * @since 1.2.0
 */
abstract class AbstractCachedSkipList<K, V> @JvmOverloads constructor(fileStore: Store, header: Header, headless: Boolean = false, keyType:Class<*>, canStoreKeyWithinNode:Boolean) : AbstractSkipList<K, V>(fileStore, header, headless, keyType, canStoreKeyWithinNode) {

    // Caching maps
    protected var keyCache: MutableMap<K, SkipNode?> = WriteSynchronizedMap(WeakHashMap())

    /**
     * Find a data at a position.  First check the cache.  If it is in there great return it otherwise go the the
     * store to find it.
     *
     * @param position Position the data can be found at.
     * @return The SkipListNode at that position.
     * @since 1.2.0
     */
    override fun findNodeAtPosition(position: Long): SkipNode? = nodeCache.getOrPut(position) { super.findNodeAtPosition(position) }

    /**
     * Find the record reference based on the key
     *
     * @param key The Key identifier
     * @return Record reference data for corresponding key
     *
     * @since 1.2.0
     */
    override fun find(key: K): SkipNode? = keyCache.getOrPut(key) { super.find(key) }

    /**
     * Delete node from cache
     *
     * @since 2.0.0
     * @param node Node to delete
     * @param head Current head nodes
     */
    override fun deleteNode(node: SkipNode, head:SkipNode) {
        super.deleteNode(node, head)
        nodeCache.remove(node.position)
    }

    /**
     * Add node to cache
     *
     * @param node Node to update
     * @since 2.0.0
     */
    override fun updateNodeCache(node:SkipNode?) {
        if(node != null)
            nodeCache.put(node.position, node)
    }

    /**
     * Update cache by removing a key
     *
     * @param node to remove
     */
    override fun updateKeyCache(node: K) {
        keyCache.remove(node)
    }

    /**
     * Clear Node and key cache
     */
    override fun clearCache() {
        super.clearCache()
        keyCache.clear()
    }

    /**
     * Clear the cache
     * @since 1.2.0
     */
    override fun clear() {
        nodeCache.clear()
        keyCache.clear()
    }
}
