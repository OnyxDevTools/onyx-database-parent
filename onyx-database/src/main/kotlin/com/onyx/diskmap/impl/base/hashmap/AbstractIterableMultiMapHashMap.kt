package com.onyx.diskmap.impl.base.hashmap

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.store.Store
import java.util.*

/**
 * This class allows iterating through a nested index.  The first level of indexing is using the Hash Table.  The second
 * maintains another cursor.  In this case, it is most likely a skip list.
 *
 * @param <K> Key
 * @param <V> Value
 */
@Suppress("UNCHECKED_CAST")
abstract class AbstractIterableMultiMapHashMap<K, V> protected constructor(store: Store, header: Header, isHeadless: Boolean, loadFactor: Int) : AbstractIterableHashMap<K, V>(store, header, isHeadless, loadFactor) {

    // region Iterable Sets

    /**
     * Values as dictionaryValues / hash map
     * @return Set of maps
     * @since 1.2.0
     */
    override val dictionaryValues: Set<Map<String, Any?>>
        get() = DictionarySet() as Set<Map<String, Any?>>

    /**
     * Set of references.  In this case, the record pointer which is a skip list data.
     * @return Set of skip list nodes
     * @since 1.2.0
     */
    override val references: Set<SkipNode>
        get() = ReferenceSet() as Set<SkipNode>

    /**
     * Set of keys within the disk map
     * @return Set of key types
     * @since 1.2.0
     */
    override val keys: MutableSet<K>
        get() = KeySet() as MutableSet<K>

    /**
     * Set of all the skip lists within the hash table
     * @return Set implementation that iterates through the top level index structure
     * @since 1.2.0
     */
    protected val maps: Set<SkipNode>
        get() = MapSet() as Set<SkipNode>

    /**
     * Iterator of the values within the entire map.
     * @return Collection of values for the map's key value pair
     * @since 1.2.0
     */
    override val values: MutableCollection<V>
        get() = ValueSet()

    /**
     * Set of entries including the key and value
     * @return Set implementation with custom iterator
     * @since 1.2.0
     */
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = EntrySet() as MutableSet<MutableMap.MutableEntry<K, V>>

    // endregion

    //region Iterator Implementations

    open inner class MultiMapIterator : MutableIterator<Any> {

        override fun remove() {
            val node:MutableMap.MutableEntry<K,V> = cursorIterator!!.next() as MutableMap.MutableEntry<K,V>
            remove(node.key)
        }

        private val otherMapIterator: Iterator<Long>
        private var cursorIterator: Iterator<Any>? = null
        private var isDictionary = false
        private var isReference = false

        constructor() {
            otherMapIterator = this@AbstractIterableMultiMapHashMap.skipListMaps.iterator()
        }

        constructor(isDictionary: Boolean, isReference: Boolean) {
            this.isDictionary = isDictionary
            this.isReference = isReference
            otherMapIterator = this@AbstractIterableMultiMapHashMap.skipListMaps.iterator()
        }

        override fun hasNext(): Boolean {
            var hasNext = cursorIterator != null && cursorIterator!!.hasNext()
            while (!hasNext) {
                hasNext = otherMapIterator.hasNext()
                if (!hasNext)
                    return false
                val mapReference = otherMapIterator.next()
                if (mapReference == 0L)
                    continue
                val node = findNodeAtPosition(mapReference)
                head = node
                cursorIterator = when {
                    isDictionary -> super@AbstractIterableMultiMapHashMap.entries.iterator()
                    isReference -> super@AbstractIterableMultiMapHashMap.references.iterator()
                    else -> super@AbstractIterableMultiMapHashMap.entries.iterator()
                }
                hasNext = cursorIterator!!.hasNext()
            }

            return hasNext
        }

        override fun next(): Any = cursorIterator!!.next()
    }

    private inner class MultiMapSkipListIterator<out V> : MutableIterator<V> {
        internal val otherMapIterator: Iterator<Long> =  this@AbstractIterableMultiMapHashMap.skipListMaps.iterator()

        override fun remove() = Unit
        override fun hasNext(): Boolean = otherMapIterator.hasNext()
        override fun next(): V = findNodeAtPosition(otherMapIterator.next()) as V
    }

    inner class MultiMapKeyIterator : MultiMapIterator() {
        override fun next(): Any = (super.next() as MutableMap.MutableEntry<K, V>).key as Any
    }

    // endregion

    //region Set Implementations

    inner class KeySet : EntrySet() {
        override fun iterator(): MutableIterator<V> = MultiMapKeyIterator() as MutableIterator<V>
    }

    inner class MapSet : EntrySet() {
        override fun iterator(): MutableIterator<V> = MultiMapSkipListIterator()
    }

    inner class DictionarySet: EntrySet() {
        override fun iterator():MutableIterator<V> = MultiMapIterator(true, false) as MutableIterator<V>
    }

    inner class ReferenceSet : EntrySet() {
        override fun iterator(): MutableIterator<V> = MultiMapIterator(false, true) as MutableIterator<V>
    }

    inner class ValueSet : EntrySet() {
        override fun iterator(): MutableIterator<V> = MultiMapIterator() as MutableIterator<V>
    }

    open inner class EntrySet : AbstractSet<V>(), MutableSet<V> {
        override fun iterator(): MutableIterator<V> = MultiMapIterator() as MutableIterator<V>

        override val size:Int
            get() = this@AbstractIterableMultiMapHashMap.longSize().toInt()
    }

    // endregion
}
