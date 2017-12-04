package com.onyx.diskmap.impl.base.hashmap

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.store.Store
import com.onyx.lang.map.OptimisticLockingMap

import java.nio.ByteBuffer
import java.util.*

/**
 * Created by Tim Osborn on 2/15/17.
 *
 * This class controls the caching of a Hash Map within a disk store
 *
 * @since 1.2.0
 */
abstract class AbstractCachedHashMap<K, V>(fileStore: Store, header: Header, headless: Boolean, loadFactor: Int) : AbstractHashMap<K, V>(fileStore, header, headless, loadFactor) {

    protected var cache: MutableMap<Int, Long> = OptimisticLockingMap(WeakHashMap())
    protected var mapCache: MutableMap<Int, Int> = OptimisticLockingMap(WeakHashMap())

    /**
     * Insert nodeReference into the hash array.  This will add it to a cache before writing it to the store.
     *
     * @param hash The maximum hash value can only contain as many digits as the size of the loadFactor
     * @param nodeReference Reference of the sub data structure to put it into.
     * @return The nodeReference that was inserted
     *
     * @since 1.2.0
     */
    override fun insertSkipListReference(hash: Int, nodeReference: Long): Long {
        cache.put(hash, nodeReference)
        super.insertSkipListReference(hash, nodeReference)
        return nodeReference
    }

    /**
     * Update the referenceNode of the hash.
     *
     * @param hash Identifier of the data structure
     * @param referenceNode Reference of the sub data structure to update to.
     * @since 1.2.0
     * @return The referenceNode that was sent in.
     */
    override fun updateSkipListReference(hash: Int, referenceNode: Long): Long {
        cache.put(hash, referenceNode)
        super.updateSkipListReference(hash, referenceNode)
        return referenceNode
    }

    /**
     * Get the sub data structure reference for the hash id.
     * @param hash Identifier of the data structure
     * @return Location of the data structure within the volume/store
     *
     * @since 1.2.0
     */
    override fun getSkipListReference(hash: Int): Long = cache.getOrPut(hash) { super@AbstractCachedHashMap.getSkipListReference(hash) }

    /**
     * Add iteration list.  This method adds a reference so that the iterator knows what to iterate through without
     * guessing which element within the hash as a sub data structure reference.
     *
     * @param hash Identifier of the sub data structure
     * @param count The current size of the hash table
     *
     * @since 1.2.0
     */
    override fun addSkipListIterationReference(hash: Int, count: Int) {
        mapCache.put(count, hash)
        super.addSkipListIterationReference(hash, count)
    }

    /**
     * Get Map ID within the iteration index
     * @param index Index within the list of maps
     * @return The hash identifier of the sub data structure
     * @since 1.2.0
     */
    override fun getSkipListIdentifier(index: Int): Int = mapCache.getOrPut(index) { super@AbstractCachedHashMap.getSkipListIdentifier(index) }

    /**
     * Clear the cache
     * @since 1.2.0
     */
    override fun clear() {
        super.clear()
        this.cache.clear()
        this.mapCache.clear()
    }

}
