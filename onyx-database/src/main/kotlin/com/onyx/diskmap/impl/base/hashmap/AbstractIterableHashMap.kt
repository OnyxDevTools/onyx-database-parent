package com.onyx.diskmap.impl.base.hashmap

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.store.Store

import java.util.AbstractCollection

/**
 * This class maintains the iterator capabilities for the hash table.
 *
 * @param <K> Key
 * @param <V> Value
 */
@Suppress("UNCHECKED_CAST")
abstract class AbstractIterableHashMap<K, V>(fileStore: Store, header: Header, headless: Boolean, loadFactor: Int) : AbstractCachedHashMap<K, V>(fileStore, header, headless, loadFactor) {

    /**
     * Collection of skip list references
     * @return Collection of skip list references
     */
    internal val skipListMaps: Collection<Long>
        get() = SkipListMapSet()

    /**
     * Iterates through the skip list references.
     */
    private inner class SkipListMapIterator : MutableIterator<Long> {
        override fun remove() {}

        internal var index = 0

        override fun hasNext(): Boolean = index < mapCount.get()

        override fun next(): Long {
            try {
                val mapId = getSkipListIdentifier(index)
                return getSkipListReference(mapId)
            } finally {
                index++
            }
        }
    }

    private inner class SkipListMapSet<T> : AbstractCollection<T>() {
        override fun iterator(): MutableIterator<T> = SkipListMapIterator() as MutableIterator<T>
        override val size: Int
            get() = mapCount.get()
    }
}
