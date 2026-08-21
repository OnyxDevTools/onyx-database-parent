package com.onyx.diskmap.impl.base.btree

import com.onyx.diskmap.DiskMapEntry
import com.onyx.diskmap.data.BTreeEntry
import com.onyx.diskmap.data.BTreePage
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.store.Store
import com.onyx.extension.common.toType
import java.lang.ref.WeakReference
import java.util.AbstractSet
import java.util.NoSuchElementException

/**
 * Exposes a B+ tree's linked leaves as lazy, live map collection views.
 *
 * Iterators walk leaf slots in key order and resolve only the key, value, dictionary, or stable
 * entry ID requested by the selected view. The views are backed by the tree rather than copied;
 * iterator removal updates the tree and then resumes from the next ordered key so page merges do
 * not invalidate its traversal position.
 */
@Suppress("UNCHECKED_CAST")
abstract class AbstractIterableBTree<K, V>(
    store: WeakReference<Store>,
    recordStore: WeakReference<Store>,
    header: Header,
    keyType: Class<*>
) : AbstractCachedBTree<K, V>(store, recordStore, header, keyType) {

    override val references: Set<Long>
        get() = ReferenceCollection()

    open val dictionaryValues: Set<Map<String, Any?>>
        get() = DictionaryCollection()

    override val values: MutableCollection<V>
        get() = ValueCollection()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = EntryCollection()

    override val keys: MutableSet<K>
        get() = KeyCollection()

    override fun forEachReference(action: (Long, V) -> Unit) {
        var page: BTreePage? = leftMostLeaf()
        while (page != null) {
            for (index in 0 until page.keyCount) action(entryIdAt(page, index), valueAt(page, index))
            page = findPageAtPositionOrNull(page.nextLeaf)
        }
    }

    abstract inner class AbstractEntryCollection<T> : AbstractSet<T>() {
        override val size: Int
            get() = this@AbstractIterableBTree.longSize().toInt()
    }

    inner class ValueCollection : AbstractEntryCollection<V>() {
        override fun iterator(): MutableIterator<V> = ValueIterator()
    }

    inner class DictionaryCollection : AbstractEntryCollection<Map<String, Any?>>() {
        override fun iterator(): MutableIterator<Map<String, Any?>> = DictionaryIterator()
    }

    inner class KeyCollection : AbstractEntryCollection<K>() {
        override fun iterator(): MutableIterator<K> = KeyIterator()
    }

    inner class ReferenceCollection : AbstractEntryCollection<Long>() {
        override fun iterator(): MutableIterator<Long> = ReferenceIterator()
    }

    inner class EntryCollection : AbstractEntryCollection<MutableMap.MutableEntry<K, V>>() {
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = EntryIterator()
    }

    private abstract inner class LeafIterator<T> : MutableIterator<T> {
        private var page: BTreePage? = leftMostLeaf()
        private var index = 0
        private var lastPage: BTreePage? = null
        private var lastIndex = -1
        private var canRemove = false

        init {
            advancePastEmptyPages()
        }

        private fun advancePastEmptyPages() {
            while (page != null && index >= page!!.keyCount) {
                page = findPageAtPositionOrNull(page!!.nextLeaf)
                index = 0
            }
        }

        final override fun hasNext(): Boolean {
            advancePastEmptyPages()
            return page != null
        }

        final override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            val currentPage = page!!
            val currentIndex = index++
            lastPage = currentPage
            lastIndex = currentIndex
            canRemove = true
            return value(currentPage, currentIndex)
        }

        abstract fun value(page: BTreePage, index: Int): T

        final override fun remove() {
            check(canRemove) { "next() must be called before remove()" }
            val key = keyAt(lastPage!!, lastIndex)
            this@AbstractIterableBTree.remove(key)
            // The current slot shifted left when no merge occurred. Re-seek by
            // the next key so iterator removal remains correct across merges.
            val currentPage = page
            if (currentPage != null && index < currentPage.keyCount) {
                val resumeKey = keyAt(currentPage, index)
                page = findLeaf(resumeKey)
                index = lowerBound(page!!, resumeKey)
            } else if (currentPage != null && currentPage.nextLeaf > 0L) {
                page = findPageAtPositionOrNull(currentPage.nextLeaf)
                index = 0
            } else {
                page = null
                index = 0
            }
            canRemove = false
        }
    }

    private inner class ValueIterator : LeafIterator<V>() {
        override fun value(page: BTreePage, index: Int): V = valueAt(page, index)
    }

    private inner class DictionaryIterator : LeafIterator<Map<String, Any?>>() {
        override fun value(page: BTreePage, index: Int): Map<String, Any?> =
            getRecordValueAsDictionary(recordPointerAt(page, index))
    }

    private inner class KeyIterator : LeafIterator<K>() {
        override fun value(page: BTreePage, index: Int): K = keyAt(page, index)
    }

    private inner class ReferenceIterator : LeafIterator<Long>() {
        override fun value(page: BTreePage, index: Int): Long = entryIdAt(page, index)
    }

    private inner class EntryIterator : LeafIterator<MutableMap.MutableEntry<K, V>>() {
        override fun value(page: BTreePage, index: Int): MutableMap.MutableEntry<K, V> =
            BTreeMapEntry(
                page.keys[index], page.decodedKeys[index], entryIdAt(page, index), recordPointerAt(page, index)
            )
    }

    inner class BTreeMapEntry internal constructor(
        private val keyToken: Long,
        private var decodedKey: Any?,
        override val recordId: Long,
        private var recordPointer: Long
    ) : DiskMapEntry<K, V> {

        override val key: K
            get() {
                decodedKey?.let { return it as K }
                val resolved = if (storeKeyWithinNode) {
                    keyToken.toType(keyType)
                } else {
                    records.getObject<Any>(keyToken)
                }
                decodedKey = resolved
                return resolved as K
            }

        override val value: V
            get() = if (recordPointer == BTreeEntry.NULL_RECORD) null as V else records.getObject(recordPointer)

        override fun setValue(newValue: V): V {
            val previous = value
            this@AbstractIterableBTree[key] = newValue
            recordPointer = BTreeEntry.readRecord(fileStore, recordId)
            return previous
        }
    }
}
