package com.onyx.diskmap.impl.base.skiplist

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipListHeadNode
import com.onyx.diskmap.data.SkipListNode
import com.onyx.diskmap.store.Store
import com.onyx.lang.map.OptimisticLockingMap
import java.util.*

/**
 * Created by tosborn1 on 1/7/17.
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
    protected var keyCache: MutableMap<K, SkipListNode<K>> = OptimisticLockingMap(WeakHashMap())
    protected var valueByPositionCache: MutableMap<Long, V?> = OptimisticLockingMap(WeakHashMap())

    /**
     * Creates and caches the new data as well as its value.
     *
     * @param key   Key Identifier
     * @param value Record value
     * @param level What level it exists within the skip list
     * @param next  The next value in the skip list
     * @param down  Reference to the next level
     * @return The instantiated and configured data.
     * @since 1.2.0
     */
    override fun createNewNode(key: K, value: V?, level: Byte, next: Long, down: Long, cache: Boolean, recordId: Long): SkipListNode<K> {
        val newNode = super.createNewNode(key, value, level, next, down, cache, recordId)
        nodeCache.put(newNode.position, newNode)
        if (cache) {
            keyCache.put(key, newNode)
            valueByPositionCache.put(newNode.recordPosition, value)
        }
        return newNode
    }

    /**
     * Creates and caches the new data as well as its value.
     *
     * @param level What level it exists within the skip list
     * @param next  The next value in the skip list
     * @param down  Reference to the next level
     * @return The instantiated and configured data.
     * @since 1.2.0
     */
    override fun createHeadNode(level: Byte, next: Long, down: Long): SkipListHeadNode {
        val newNode = super.createHeadNode(level, next, down)
        nodeCache.put(newNode.position, newNode)
        return newNode
    }

    /**
     * Find the value at a position.
     *
     * @param position   The position within the file structure to pull it from
     * @param recordSize How many bytes we must read to get the object
     * @return The value as long as it serialized ok.
     * @since 1.2.0
     */
    override fun findValueAtPosition(position: Long, recordSize: Int): V? {
        var value: V? = valueByPositionCache[position]
        if (value == null) {
            value = super.findValueAtPosition(position, recordSize)
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
    override fun findNodeAtPosition(position: Long): SkipListHeadNode? {
        if (position == 0L)
            return null

        var node: SkipListHeadNode? = nodeCache[position]

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
    override fun find(key: K): SkipListNode<K>? {
        var node: SkipListNode<*>? = keyCache[key]
        if (node == null) {
            node = super.find(key)
            if(node != null)
                keyCache[key] = node
        }
        @Suppress("UNCHECKED_CAST")
        return node as SkipListNode<K>?
    }

    /**
     * Remove the data from the cache before updating.  Apparantly the reference of the data is not sufficient and
     * it needs to be removed so that it can be refreshed from disk.
     *
     * @param node Node to update
     * @param position position to set the data.down to
     */
    override fun updateNodeNext(node: SkipListHeadNode, position: Long) {
        nodeCache.remove(node.position)
        super.updateNodeNext(node, position)
    }

    /**
     * Update the Node value.  This is only done upon update to the map.
     * This is intended to do a cleanup of the cache
     *
     * @param node Node that's reference was cleaned up
     * @param value The value of the reference
     * @since 1.2.0
     */
    override fun updateNodeValue(node: SkipListNode<K>, value: V, cache: Boolean) {
        // Remove the old value before updating
        if (cache) {
            valueByPositionCache.remove(node.recordPosition)
        }
        // Update and cache the new value
        nodeCache.put(node.position, node)
        super.updateNodeValue(node, value, cache)

        if (cache) {
            keyCache.put(node.key, node)
            valueByPositionCache.put(node.recordPosition, value)
        }
    }

    /**
     * Perform cleanup once the data has been removed
     * @param node Node that is to be removed
     *
     * @since 1.2.0
     */
    override fun removeNode(node: SkipListHeadNode) {
        nodeCache.remove(node.position)
        if (node is SkipListNode<*>) {
            keyCache.remove(node.key)
            valueByPositionCache.remove(node.recordPosition)
        }
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
