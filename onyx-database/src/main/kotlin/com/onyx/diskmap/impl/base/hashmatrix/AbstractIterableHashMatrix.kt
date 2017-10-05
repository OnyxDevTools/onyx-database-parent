package com.onyx.diskmap.impl.base.hashmatrix

import com.onyx.diskmap.impl.DiskSkipListMap
import com.onyx.diskmap.data.HashMatrixNode
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipListHeadNode
import com.onyx.diskmap.data.SkipListNode
import com.onyx.diskmap.store.Store

import java.util.*

/**
 * Created by tosborn1 on 1/9/17.
 *
 * This class manages the iteration behavior of a multi indexed map.  The first index being a hash matrix.  The second,
 * is a skip list.
 *
 * So, first it will iterate through the hash matrix, and grab a reference to each second index being the rerence to the
 * skip lists.
 *
 * @since 1.2.0
 */
@Suppress("UNCHECKED_CAST")
abstract class AbstractIterableHashMatrix<K, V> protected constructor(store: Store, header: Header, detached: Boolean) : AbstractCachedHashMatrix<K, V>(store, header, detached), Map<K, V> {

    // region Iterable Sets

    override val references: Set<SkipListNode<K>>
        get() = MultiMapReferenceSet<SkipListNode<K>>() as Set<SkipListNode<K>>

    override val keys: MutableSet<K>
        get() = MultiMapKeySet<K>() as MutableSet<K>

    override val values: MutableCollection<V>
        get() = MultiMapValueSet<V>() as MutableCollection<V>

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = MultiMapEntrySet<MutableMap.MutableEntry<K, V>>() as MutableSet<MutableMap.MutableEntry<K, V>>

    override val dictionaryValues: Set<Map<String, Any?>>
        get() = MultiMapDictionarySet<Map<String, Any?>>() as Set<Map<String, Any?>>

    protected val maps: Set<SkipListHeadNode>
        get() = SkipListMapSet<SkipListHeadNode>() as Set<SkipListHeadNode>

    // endregion

    // region Set Classes

    inner class MultiMapValueSet<out T> : AbstractMultiMapSet() {
        override fun iterator(): MutableIterator<T> = ValueIterator() as MutableIterator<T>
    }

    inner class MultiMapDictionarySet<out T> : AbstractMultiMapSet() {
        override fun iterator(): MutableIterator<T> = DictionaryIterator() as MutableIterator<T>
    }

    inner class SkipListMapSet<out T> : AbstractMultiMapSet() {
        override fun iterator(): MutableIterator<T> = MapIterator() as MutableIterator<T>
    }

    inner class MultiMapKeySet<out T> : AbstractMultiMapSet() {
        override fun iterator(): MutableIterator<T> = KeyIterator() as MutableIterator<T>
    }

    inner class MultiMapReferenceSet<out T> : AbstractMultiMapSet() {
        override fun iterator(): MutableIterator<T> =  ReferenceIterator() as MutableIterator<T>
    }

    inner class MultiMapEntrySet<out T> : AbstractMultiMapSet() {
        override fun iterator(): MutableIterator<T> = EntryIterator() as MutableIterator<T>
    }

    inner abstract class AbstractMultiMapSet : AbstractSet<Any>() {
        override val size: Int
            get() =  this@AbstractIterableHashMatrix.longSize().toInt()
    }

    // endregion

    // region Iterator Classes

    inner class EntryIterator : AbstractMultiMapIterator()

    inner class DictionaryIterator : AbstractMultiMapIterator(true)

    inner class ValueIterator : AbstractMultiMapIterator() {
        override fun next(): Any {
            val entry = super.next() as MutableMap.MutableEntry<K, V>
            return entry.value as Any
        }
    }

    inner class KeyIterator : AbstractMultiMapIterator() {
        override fun next(): Any {
            val entry = super.next() as MutableMap.MutableEntry<K, V>
            return entry.key as Any
        }
    }

    inner class ReferenceIterator : AbstractMultiMapIterator() {
        init {
            isReference = true
        }
    }

    inner class MapIterator : AbstractMultiMapIterator(){
        /**
         * Hash next,  only if the stack is not empty
         *
         * @return Whether there is a sub data structure
         */
        override fun hasNext(): Boolean {
            queueUpNext()
            return referenceStack.size > 0
        }

        /**
         * Next, pop it off the stack
         *
         * @return Returns the nex sub map
         */
        override fun next(): Any = findNodeAtPosition(referenceStack.pop())!!
    }

    /**
     * This is the base implementation of the iterators
     *
     * @since 1.2.0
     */
    inner abstract class AbstractMultiMapIterator @JvmOverloads constructor(isDictionary: Boolean = false) : MutableIterator<Any?> {

        private val nodeStack = Stack<NodeEntry>() // Simple stack that hold onto the nodes
        protected val referenceStack = Stack<Long>() // Simple stack that hold onto the nodes
        private var currentIterator: Iterator<*>? = null
        private var isDictionary = false
        protected var isReference = false

        init {
            this.isDictionary = isDictionary
            if (reference.firstNode > 0) {
                nodeStack.push(NodeEntry(reference.firstNode, (-1).toShort()))
            }
            queueUpNext()
        }

        /**
         * The cursor of the skip list has next.  The currentIterator indicates the iterator of the current skip list
         * set by queueUpNext.
         * @return Whether there are values left in the skip list or there are skip lists still to check that may have
         * values
         *
         * @since 1.2.0
         */
        override fun hasNext(): Boolean {
            prepareNext()
            return currentIterator != null && currentIterator!!.hasNext()
        }

        /**
         * Queue up the next reference so, we can check ahead rather than do the processing in the next() while
         * the hasNext may not know if there is a next value or not.
         *
         * @since 1.2.0
         */
        fun queueUpNext() {
            var nodeEntry: NodeEntry
            var newEntry: NodeEntry

            var node: HashMatrixNode
            var reference: Long

            while (nodeStack.size > 0) {

                nodeEntry = nodeStack.pop()
                node = this@AbstractIterableHashMatrix.getHashMatrixNode(nodeEntry.reference)

                // Add all the other related nodes in the bitmap
                for (i in 0..9) {
                    reference = node.next[i]
                    if (reference > 0) {
                        if (nodeEntry.level < loadFactor - 2) {
                            newEntry = NodeEntry(reference, (nodeEntry.level + 1).toShort())
                            if (!nodeStack.contains(newEntry))
                                nodeStack.add(newEntry)
                        } else {
                            referenceStack.push(reference)
                        }
                    }
                }
            }
        }

        /**
         * Prepare next value
         *
         * @since 1.2.0
         */
        private fun prepareNext() {
            if (currentIterator != null && currentIterator!!.hasNext())
                return

            var continueLooking = true
            while (continueLooking && referenceStack.size > 0) {
                queueUpNext()

                while (referenceStack.size > 0) {
                    val node = findNodeAtPosition(referenceStack.pop())
                    head = node
                    currentIterator = when {
                        isDictionary -> super@AbstractIterableHashMatrix.dictionaryValues.iterator()
                        isReference -> super@AbstractIterableHashMatrix.references.iterator()
                        else -> super@AbstractIterableHashMatrix.entries.iterator()
                    }
                    if (currentIterator!!.hasNext()) {
                        continueLooking = false
                        break
                    }
                }
            }
        }

        /**
         * Return the next object which is located in the current iterator.
         * @return Either a map reference, dictionary, or entry key value pair based on the parents' requirement
         */
        override fun next(): Any {
            prepareNext()
            return currentIterator!!.next()!!
        }

        override fun remove() {}

    }

    // endregion

    /**
     * Constructor.  Queue up the first reference of a skip list
     */
    data class NodeEntry constructor(val reference: Long, val level: Short)
}
