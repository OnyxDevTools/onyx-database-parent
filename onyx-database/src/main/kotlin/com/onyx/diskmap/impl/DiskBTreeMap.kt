package com.onyx.diskmap.impl

import com.onyx.diskmap.SortedDiskMap
import com.onyx.diskmap.data.BTreeEntry
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
open class DiskBTreeMap<K, V>(
    fileStore: WeakReference<Store>,
    recordStore: WeakReference<Store>,
    header: Header,
    keyType: Class<*>
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

    override fun containsValue(value: V): Boolean = mapReadWriteLock.readLock {
        values.any { it == value }
    }

    override fun putAll(from: Map<out K, V>) = from.forEach { this[it.key] = it.value }

    override fun clear() = mapReadWriteLock.writeLock {
        super.clear()
    }

    override fun clearCache() = mapReadWriteLock.writeLock {
        super.clearCache()
    }

    override fun forEachReference(action: (Long, V) -> Unit) = mapReadWriteLock.readLock {
        super.forEachReference(action)
    }

    override fun visitReferencesWhile(visitor: (Long, V) -> Boolean): Int =
        mapReadWriteLock.readLock { super.visitReferencesWhile(visitor) }

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

    override fun above(index: K, includeFirst: Boolean): Set<Long> = mapReadWriteLock.readLock {
        HashSet<Long>().also { addPositionsAscending(index.castTo(keyType) as K, includeFirst, it) }
    }

    override fun below(index: K, includeFirst: Boolean): Set<Long> = mapReadWriteLock.readLock {
        HashSet<Long>().also { addPositionsDescending(index.castTo(keyType) as K, includeFirst, it) }
    }

    override fun between(fromValue: K?, includeFrom: Boolean, toValue: K?, includeTo: Boolean): Set<Long> =
        mapReadWriteLock.readLock {
            val from = requireNotNull(fromValue?.castTo(keyType) as K?)
            val to = requireNotNull(toValue?.castTo(keyType) as K?)
            HashSet<Long>().also { addPositionsBetween(from, includeFrom, to, includeTo, it) }
        }
}
