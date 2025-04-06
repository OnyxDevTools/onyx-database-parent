package com.onyx.diskmap.impl.base.skiplist

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.store.Store
import java.lang.ref.WeakReference

import java.util.*

/**
 * Created by Tim Osborn on 1/7/17.
 *
 * This class was added to enhance the existing index within Onyx Database.  The bitmap was very efficient but, it was a hog
 * as far as how much space it took over.  As far as in-memory data structures, this will be the go-to algorithm.  The
 * data structure is based on a SkipList.  This contains the iteration part of a map.  This is abstracted out just so we
 * can isolate only the stuff that makes pertains to looping.
 *
 * @since 1.2.0
 *
 * @param <K> Key Object Type
 * @param <V> Value Object Type
 */
@Suppress("UNCHECKED_CAST")
abstract class AbstractIterableSkipList<K, V>(store: WeakReference<Store>, recordStore: WeakReference<Store>, header: Header, keyType:Class<*>) : AbstractCachedSkipList<K, V>(store, recordStore, header, keyType) {

    // region Iterable Collections

    override val references:Set<SkipNode>
        get() = ReferenceCollection<SkipNode>()

    open val dictionaryValues: Set<Map<String, Any?>>
        get() = DictionaryCollection()

    override val values: MutableCollection<V>
        get() = ValueCollection()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = EntryCollection()

    override val keys: MutableSet<K>
        get() = KeyCollection()

    // endregion

    // region Collection Types

    /**
     * Class for sifting through values
     *
     * @see AbstractNodeCollection
     */
    inner class ValueCollection<V> : AbstractNodeCollection<V>() {
        override fun iterator(): MutableIterator<V?> = ValueIterator()
    }

    /**
     * Class for sifting through values
     *
     * @see AbstractNodeCollection
     */
    inner class DictionaryCollection<D> : AbstractNodeCollection<D>() {
        override fun iterator(): MutableIterator<D> = DictionaryIterator()
    }

    /**
     * Key Collection
     *
     * @see AbstractNodeCollection
     */
    inner class KeyCollection<K> : AbstractNodeCollection<K>() {
        override fun iterator(): MutableIterator<K?> = KeyIterator()
    }

    /**
     * Key Collection
     *
     * @see AbstractNodeCollection
     */
    inner class ReferenceCollection<R> : AbstractNodeCollection<R>() {
        override fun iterator(): MutableIterator<R> = NodeIterator() as MutableIterator<R>
    }

    /**
     * Entry Collection.  Much like KeyCollection except iterates through entries
     *
     */
    inner class EntryCollection<V> : AbstractNodeCollection<V>() {
        override fun iterator(): MutableIterator<V> = EntryIterator() as MutableIterator<V>
    }

    /**
     * Abstract SkipListNode Collection.  Holds onto references to all the nodes and fills the data values
     * based on the Bitmap nodes
     *
     */
    abstract inner class AbstractNodeCollection<T> : AbstractSet<T>() {
        /**
         * Size, mirrors disk structure
         *
         * @return Int value of the size.  Only works if its small enough to be an int.  Also note, this is not concurrent
         * It sends the size at the time of method invocation not creating the sub collection.
         */
        override val size: Int
            get() = this@AbstractIterableSkipList.longSize().toInt()
    }


    // endregion

    // region Iterators

    /**
     * Value Iterator.
     *
     *
     * Iterates through and hydrates the values in an DiskMap
     */
    inner class ValueIterator<out V> : MutableIterator<V?> {

        private val nodeIterator = NodeIterator()

        override fun hasNext(): Boolean = nodeIterator.hasNext()

        /**
         * Next Dictionary Object
         *
         * @return The next dictionary object
         */
        override fun next(): V? = nodeIterator.next()?.getRecord<V>(records)

        override fun remove() = Unit

    }

    /**
     * Key Iterator.  Same as Value iterator except returns just the keys
     */
    inner class KeyIterator<out V> : MutableIterator<V?> {
        private val nodeIterator = NodeIterator()

        override fun remove() {
            if(hasNext())
            {
                remove(next() as K)
            }
        }
        override fun hasNext(): Boolean = nodeIterator.hasNext()

        /**
         * Next Dictionary Object
         *
         * @return The next dictionary object
         */
        override fun next(): V? {
            val node = nodeIterator.next()
            return node?.key as V?
        }
    }

    /**
     * Entry.  Similar to the Key and Value iterator except it returns a custom entry that will lazy load the keys and values
     */
    inner class EntryIterator : MutableIterator<SkipListEntry<K, V>> {
        private val nodeIterator = NodeIterator()

        override fun remove() {
            if(hasNext()) {
                val entry = next()
                remove((entry as SkipListEntry<*, *>).key)
            }
        }

        override fun hasNext(): Boolean = nodeIterator.hasNext()

        /**
         * Next Dictionary Object
         *
         * @return The next dictionary object
         */
        override fun next(): SkipListEntry<K, V> {
            val node = nodeIterator.next()
            return SkipListEntry(node)
        }
    }

    /**
     * Dictionary Iterator.
     *
     *
     * Iterates through and hydrates the values in an DiskMap
     */
    inner class DictionaryIterator<out T> : MutableIterator<T> {

        override fun remove() {}

        private val nodeIterator = NodeIterator()

        override fun hasNext(): Boolean = nodeIterator.hasNext()

        /**
         * Next Dictionary Object
         *
         * @return The next dictionary object
         */
        override fun next(): T {
            val next = nodeIterator.next()
            @Suppress("UNCHECKED_CAST")
            return if(next != null) getRecordValueAsDictionary(next.record) as T else null as T
        }
    }


    /**
     * Abstract SkipListNode iterator
     *
     * Iterates through nodes and gets the left, right, next values
     */
    inner class NodeIterator : MutableIterator<SkipNode?> {
        override fun remove() {}

        private var current: SkipNode? = null

        init {
            current = head
            while (current?.down ?: 0L > 0)
                current = findNodeAtPosition(current!!.down)

            current = if(current?.right ?: 0 > 0)
                findNodeAtPosition(current!!.right)
            else
                null
        }

        /**
         * Has next.  Only if there are remaining objects
         *
         * @return Whether the data has a record or not
         */
        override fun hasNext(): Boolean = current != null

        /**
         * Next, find the next data with a record associated to it.
         *
         * @return Nex data with a record value.
         */
        override fun next(): SkipNode? {

            val previous = current
            current = if (current != null && current!!.right > 0)
                findNodeAtPosition(current!!.right)
            else
                null

            return previous
        }
    }

    // endregion

    // region Map Entry

    /**
     * Disk Map Entry
     *
     */
    @Suppress("UNCHECKED_CAST")
    inner class SkipListEntry<A,B> internal constructor(var node: SkipNode?) : MutableMap.MutableEntry<A?, B?> {

        /**
         * Get Key
         *
         * @return Key from the data
         */
        override val key: A?
            get() = node?.getKey(records, storeKeyWithinNode, keyType)

        /**
         * Get Value
         *
         * @return Value from the data position
         */
        override val value: B? = node?.getRecord<B>(records)

        override fun setValue(newValue: B?): B? = value
    }

    // endregion
}
