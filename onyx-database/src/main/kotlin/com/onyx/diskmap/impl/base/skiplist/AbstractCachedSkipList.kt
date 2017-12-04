package com.onyx.diskmap.impl.base.skiplist

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.store.Store
import com.onyx.lang.map.OptimisticLockingMap
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
abstract class AbstractCachedSkipList<K, V> @JvmOverloads constructor(fileStore: Store, header: Header, headless: Boolean = false) : AbstractSkipList<K, V>(fileStore, header, headless) {

    // Caching maps
    protected var keyCache: MutableMap<K, SkipNode> = OptimisticLockingMap(WeakHashMap())
    protected var valueByPositionCache: MutableMap<Long, V?> = OptimisticLockingMap(WeakHashMap())

    /**
     * Find the value at a position.
     *
     * @param position   The position within the file structure to pull it from
     * @return The value as long as it serialized ok.
     * @since 1.2.0
     */
    override fun findValueAtPosition(position: Long): V? {
        var value: V? = valueByPositionCache[position]
        if (value == null) {
            value = super.findValueAtPosition(position)
            valueByPositionCache.put(position, value)
        }
        return value
    }

    /**
     * Find a data at a position.  First check the cache.  If it is in there great return it otherwise go the the
     * store to find it.
     *
     * @param position Position the data can be found at.
     * @return The SkipListNode at that position.
     * @since 1.2.0
     */
    override fun findNodeAtPosition(position: Long): SkipNode? {
        if (position == 0L)
            return null

        var node: SkipNode? = nodeCache[position]

        if (node == null) {
            node = super.findNodeAtPosition(position)
            if(node != null)
                nodeCache[position] = node
        }
        return node
    }

    /**
     * Find the record reference based on the key
     *
     * @param key The Key identifier
     * @return Record reference data for corresponding key
     *
     * @since 1.2.0
     */
    override fun find(key: K): SkipNode? {
        var node: SkipNode? = keyCache[key]
        if (node == null) {
            node = super.find(key)
            if(node != null)
                keyCache[key] = node
        }
        return node
    }

    override fun deleteNode(node: SkipNode, head:SkipNode) {
        super.deleteNode(node, head)
        valueByPositionCache.remove(node.record)
        nodeCache.remove(node.position)
    }

    override fun updateNodeCache(node:SkipNode?) {
        if(node != null)
            nodeCache.put(node.position, node)
    }

    override fun updateValueCache(node: SkipNode?) {
        if(node != null)
            valueByPositionCache.remove(node.position)
    }

    override fun updateKeyCache(key: K) {
        keyCache.remove(key)
    }

    /**
     * Clear the cache
     * @since 1.2.0
     */
    override fun clear() {
        nodeCache.clear()
        keyCache.clear()
        valueByPositionCache.clear()
    }
}
