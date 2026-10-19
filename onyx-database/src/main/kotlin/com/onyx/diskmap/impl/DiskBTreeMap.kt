package com.onyx.diskmap.impl

import com.onyx.diskmap.SortedDiskMap
import com.onyx.diskmap.ValueUpdateMode
import com.onyx.diskmap.data.BTreeEntry
import com.onyx.diskmap.data.BTreePage
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.PutResult
import com.onyx.diskmap.impl.base.btree.AbstractIterableBTree
import com.onyx.diskmap.store.Store
import com.onyx.exception.AttributeTypeMismatchException
import com.onyx.extension.common.castTo
import com.onyx.extension.common.getAny
import com.onyx.lang.concurrent.ClosureReadWriteLock
import com.onyx.lang.concurrent.impl.DefaultClosureReadWriteLock
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.HashSet

/**
 * A persistent, sorted map backed by a page-oriented B+ tree.
 *
 * Keys are normalized to [keyType] before lookup. Primitive-compatible keys are encoded
 * directly in tree pages; other keys and all non-null values are stored in [recordStore].
 * Leaf slots refer to stable [BTreeEntry] positions, so the record IDs exposed by this map
 * remain valid when pages split, merge, or rebalance.
 *
 * Individual map operations are coordinated by [mapReadWriteLock]. Iteration is provided by
 * live collection views and therefore is not a snapshot of the tree.
 */
@Suppress("UNCHECKED_CAST")
open class DiskBTreeMap<K, V> @JvmOverloads constructor(
    fileStore: WeakReference<Store>,
    recordStore: WeakReference<Store>,
    header: Header,
    keyType: Class<*>,
    public override val valueUpdateMode: ValueUpdateMode = ValueUpdateMode.APPEND
) : AbstractIterableBTree<K, V>(fileStore, recordStore, header, keyType), SortedDiskMap<K, V> {

    override val size: Int
        get() = longSize().toInt()

    protected open var mapReadWriteLock: ClosureReadWriteLock = DefaultClosureReadWriteLock()

    override fun get(key: K): V? = mapReadWriteLock.readLock {
        super.get(key.castTo(keyType) as K)
    }

    override fun containsKey(key: K): Boolean = mapReadWriteLock.readLock {
        super.containsKey(key.castTo(keyType) as K)
    }

    override fun remove(key: K): V? = mapReadWriteLock.writeLock {
        super.remove(key.castTo(keyType) as K)
    }

    override fun put(key: K, value: V): V = mapReadWriteLock.writeLock {
        super.put(key.castTo(keyType) as K, value)
    }

    override fun putAndGet(key: K, value: V, preUpdate: ((Long) -> Unit)?): PutResult =
        putAndGet(key, value, preUpdate, capturePreviousValue = false)

    override fun putAndGet(
        key: K,
        value: V,
        preUpdate: ((Long) -> Unit)?,
        capturePreviousValue: Boolean
    ): PutResult =
        mapReadWriteLock.writeLock {
            super.internalPutAndGet(key.castTo(keyType) as K, value, preUpdate, capturePreviousValue)
        }

    override fun containsValue(value: V): Boolean {
        var found = false
        visitReferencesWhile { _, current ->
            found = current == value
            !found
        }
        return found
    }

    override fun putAll(from: Map<out K, V>) = from.forEach { this[it.key] = it.value }

    override fun clear() = mapReadWriteLock.writeLock {
        super.clear()
    }

    override fun clearCache() = mapReadWriteLock.writeLock {
        super.clearCache()
    }

    override fun forEachReference(action: (Long, V) -> Unit) {
        visitReferencesWhile { reference, value ->
            action(reference, value)
            true
        }
    }

    override fun visitReferencesWhile(visitor: (Long, V) -> Boolean): Int {
        val cursor = ReferenceCursor { page, index -> entryIdAt(page, index) to valueAt(page, index) }
        var visits = 0
        while (!cursor.finished) {
            for ((reference, value) in cursor.nextBatch()) {
                visits++
                if (!visitor(reference, value)) return visits
            }
        }
        return visits
    }

    /**
     * Visits a page of stable entry IDs in ascending key order without decoding skipped rows.
     * Count and the first batch of references are captured together. Both callbacks run without
     * the map lock, so cardinality checks and row collection cannot hold up saves. Like full scans,
     * pages spanning multiple batches are live traversals, not transaction snapshots.
     */
    internal fun visitAscendingReferencePage(
        firstRow: Int,
        maxResults: Int,
        onCount: (Long) -> Unit,
        visitor: (Long) -> Unit,
    ) {
        require(firstRow >= 0 && maxResults > 0)
        val cursor = ReferenceCursor(firstRow, maxResults.toLong(), read = ::entryIdAt)
        var batch = cursor.nextBatch()
        onCount(cursor.count)
        while (true) {
            batch.forEach(visitor)
            if (cursor.finished) return
            batch = cursor.nextBatch()
        }
    }

    /**
     * Copy bounded batches while the tree is stable, then let callers process them without a
     * lock. Only a decoded continuation key survives between batches: a leaf or slot may have
     * moved, split, merged, or been deleted by the time the next batch is requested.
     *
     * Traversal is weakly consistent. Already copied values may precede a concurrent update;
     * inserts behind the cursor are not revisited.
     */
    private inner class ReferenceCursor<T>(
        private var remainingOffset: Int = 0,
        private var remainingRows: Long = Long.MAX_VALUE,
        private val from: K? = null,
        private val includeFrom: Boolean = true,
        private val to: K? = null,
        private val includeTo: Boolean = true,
        private val read: (BTreePage, Int) -> T,
    ) {
        var count = 0L
            private set
        var finished = false
            private set
        private var initialized = false
        private var resumeKey: K? = null

        fun nextBatch(): List<T> = mapReadWriteLock.readLock {
            val batch = ArrayList<T>(minOf(REFERENCE_BATCH_SIZE.toLong(), remainingRows).toInt())
            if (finished) return@readLock batch
            if (!initialized) {
                initialized = true
                count = longSize()
                if (remainingOffset.toLong() >= count) {
                    finished = true
                    return@readLock batch
                }
            }

            val start = resumeKey ?: from
            var page = if (start == null) leftMostLeaf() else findLeaf(start)
            var index = when {
                start == null -> 0
                resumeKey != null || !includeFrom -> upperBound(page, start)
                else -> lowerBound(page, start)
            }
            var pages = 0
            while (batch.size < REFERENCE_BATCH_SIZE && pages < REFERENCE_BATCH_SIZE) {
                // Most leaves are wholly inside the bound. Decode only their final key rather
                // than every row's key; full scans previously needed only record IDs and values.
                val reachesBound = to != null && page.keyCount > 0 &&
                    compareKeys(keyAt(page, page.keyCount - 1), to) >= 0
                val end = if (!reachesBound) page.keyCount else if (includeTo) {
                    upperBound(page, to as K)
                } else {
                    lowerBound(page, to as K)
                }
                val startIndex = index
                if (remainingOffset > 0 && index < end) {
                    val skipped = minOf(remainingOffset, end - index)
                    index += skipped
                    remainingOffset -= skipped
                }
                val copied = minOf(
                    (end - index).coerceAtLeast(0).toLong(),
                    (REFERENCE_BATCH_SIZE - batch.size).toLong(),
                    remainingRows,
                ).toInt()
                repeat(copied) { batch.add(read(page, index++)) }
                if (index > startIndex) resumeKey = keyAt(page, index - 1)
                remainingRows -= copied
                if (remainingRows == 0L || reachesBound && index >= end) {
                    finished = true
                    return@readLock batch
                }
                if (index >= page.keyCount) {
                    val next = findPageAtPositionOrNull(page.nextLeaf)
                    if (next == null) {
                        finished = true
                        return@readLock batch
                    }
                    page = next
                    index = 0
                    pages++
                }
            }
            batch
        }
    }

    private companion object {
        const val REFERENCE_BATCH_SIZE = 128
    }

    override fun forEachMutableReference(
        action: (Long, MutableMap.MutableEntry<K, V>) -> Unit
    ) = mapReadWriteLock.writeLock {
        super.forEachMutableReference(action)
    }

    override fun getRecID(key: K): Long = mapReadWriteLock.readLock {
        findEntryId(key.castTo(keyType) as K)
    }

    override fun getWithRecID(recordId: Long): V? = mapReadWriteLock.readLock {
        if (recordId <= 0L) return@readLock null
        findEntryAtPosition(recordId)?.getRecord<V>(records)
    }

    override fun getMapWithRecID(recordId: Long): Map<String, Any?>? = mapReadWriteLock.readLock {
        val entry = findEntryAtPosition(recordId) ?: return@readLock null
        if (entry.record == BTreeEntry.NULL_RECORD) return@readLock null
        getRecordValueAsDictionary(entry.record)
    }

    @Throws(AttributeTypeMismatchException::class)
    override fun <T : Any?> getAttributeWithRecID(attribute: Field, reference: Long): T =
        mapReadWriteLock.readLock {
            val entry = findEntryAtPosition(reference) ?: return@readLock null as T
            entry.getRecord<Any>(records)?.getAny(attribute) as T
        }

    override fun above(index: K, includeFirst: Boolean): Set<Long> =
        collectReferences(ReferenceCursor(from = index.castTo(keyType) as K, includeFrom = includeFirst, read = ::entryIdAt))

    override fun below(index: K, includeFirst: Boolean): Set<Long> =
        collectReferences(ReferenceCursor(to = index.castTo(keyType) as K, includeTo = includeFirst, read = ::entryIdAt))

    override fun between(fromValue: K?, includeFrom: Boolean, toValue: K?, includeTo: Boolean): Set<Long> =
        collectReferences(ReferenceCursor(
            from = requireNotNull(fromValue?.castTo(keyType) as K?),
            includeFrom = includeFrom,
            to = requireNotNull(toValue?.castTo(keyType) as K?),
            includeTo = includeTo,
            read = ::entryIdAt,
        ))

    private fun collectReferences(cursor: ReferenceCursor<Long>): Set<Long> = HashSet<Long>().also { result ->
        while (!cursor.finished) {
            result.addAll(cursor.nextBatch())
        }
    }
}
