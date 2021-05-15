package com.onyx.diskmap.impl

import com.onyx.diskmap.SortedDiskMap
import com.onyx.diskmap.impl.base.skiplist.AbstractIterableSkipList
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.PutResult
import com.onyx.diskmap.data.SkipNode
import com.onyx.diskmap.store.Store
import com.onyx.exception.AttributeTypeMismatchException
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.extension.common.forceCompare
import com.onyx.extension.common.getAny
import com.onyx.lang.concurrent.ClosureReadWriteLock
import com.onyx.lang.concurrent.impl.DefaultClosureReadWriteLock
import java.lang.reflect.Field
import java.util.*

/**
 * Created by Tim Osborn on 1/7/17.
 *
 *
 * This class was added to enhance the existing index within Onyx Database.  The bitmap was very efficient but, it was a hog
 * as far as how much space it took over.  As far as in-memory data structures, this will be the go-to algorithm.  The
 * data structure is based on a SkipList.
 *
 * @param <K> Key Object Type
 * @param <V> Value Object Type
 * @since 1.2.0
 */
open class DiskSkipListMap<K, V>(fileStore:Store, header: Header, keyType:Class<*>, canStoreKeyWithinNode:Boolean) : AbstractIterableSkipList<K, V>(fileStore, header, keyType, canStoreKeyWithinNode), SortedDiskMap<K,V> {

    override val size: Int
        get() = reference.recordCount.get()

    private var mapReadWriteLock: ClosureReadWriteLock = DefaultClosureReadWriteLock()

    /**
     * Remove an item within the map
     *
     * @param key Key Identifier
     * @return The value that was removed
     */
    override fun remove(key: K): V? = mapReadWriteLock.writeLock { super.remove(key) }

    /**
     * Put a value into a map based on its key.
     *
     * @param key   Key identifier of the value
     * @param value Underlying value
     * @return The value of the object that was just put into the map
     */
    override fun put(key: K, value: V): V = mapReadWriteLock.writeLock { super.put(key, value) }

    /**
     * Put key value.  This is the same as map.put(K,V) except
     * rather than the value you just put into the map, it will
     * return the record id.  The purpose of this is so you
     * do not have to fetch the record id and search the skip list
     * again after inserting the record.
     *
     * @param key Primary Key
     * @param value Value to insert or update
     * @since 2.1.3
     * @return Value for previous record ID and if the value is been updated or inserted
     */
    override fun putAndGet(key: K, value: V, preUpdate:((Int) -> Unit)?): PutResult = mapReadWriteLock.writeLock { super.internalPutAndGet(key, value, preUpdate) }

    /**
     * Iterates through the entire skip list to see if it contains the value you are looking for.
     *
     *
     * Note, this is not efficient.  It will basically do a bubble search.
     *
     * @param value Value you are looking for
     * @return Whether the value was found
     */
    override fun containsValue(value: V): Boolean = mapReadWriteLock.readLock{
        values.forEach { next ->
            if (next == value)
                return@readLock true
        }
        return@readLock false
    }

    /**
     * Put all the elements from the map into the skip list map
     *
     * @param from Map to convert from
     */
    override fun putAll(from: Map<out K, V>) = from.forEach { this[it.key] = it.value }

    /**
     * Clear all the elements of the array.  If it is not detached we must handle
     * the head of teh data structure
     *
     * @since 1.2.0
     */
    override fun clear() = mapReadWriteLock.writeLock {
        super.clear()

        head = SkipNode.create(fileStore)
        this.reference.firstNode = head!!.position
        updateHeaderFirstNode(reference, this.reference.firstNode)
        reference.recordCount.set(0)
        updateHeaderRecordCount(0)
    }

    /**
     * Get the record id of a corresponding data.  Note, this points to the SkipListNode position.  Not the actual
     * record position.
     *
     * @param key Identifier
     * @return The position of the record reference if it exists.  Otherwise -1
     * @since 1.2.0
     */
    override fun getRecID(key: K): Int = mapReadWriteLock.readLock { find(key)?.position ?: -1  }

    /**
     * Hydrate a record with its record ID.  If the record value exists it will be returned
     *
     * @param recordId Position to find the record reference
     * @return The value within the map
     * @since 1.2.0
     */
    override fun getWithRecID(recordId: Int): V? {
        if (recordId <= 0)
            return null
        val node:SkipNode = findNodeAtPosition(recordId) ?: return null
        return node.getRecord<V>(fileStore)
    }

    /**
     * Get Map representation of key object
     *
     * @param recordId Record reference within storage structure
     * @return Map of key values
     * @since 1.2.0
     */
    override fun getMapWithRecID(recordId: Int): Map<String, Any?>? = mapReadWriteLock.readLock{
        val node:SkipNode = findNodeAtPosition(recordId) ?: return@readLock null
        return@readLock getRecordValueAsDictionary(node.record)
    }

    /**
     * Get Map representation of key object.  If it is in the cache, use reflection to get it from the cache.  Otherwise,
     * just hydrate the value within the store
     *
     * @param attribute Attribute name to fetch
     * @param reference  Record reference within storage structure
     * @return Map of key values
     * @since 1.2.0
     * @since 1.3.0 Optimized to require the reflection field so it does not have to re-instantiate one.
     */
    @Throws(AttributeTypeMismatchException::class)
    override fun <T : Any?> getAttributeWithRecID(attribute: Field, reference: Int): T = mapReadWriteLock.readLock {
        @Suppress("UNCHECKED_CAST")
        val node:SkipNode = findNodeAtPosition(reference) ?: return@readLock  null as T
        return@readLock node.getRecord<Any>(fileStore).getAny(attribute)
    }

    @Throws(AttributeTypeMismatchException::class)
    override fun <T : Any?> getAttributeWithRecID(field: Field, reference: SkipNode): T = reference.getRecord<Any>(fileStore).getAny(field)

    /**
     * Find all references above and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index        The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    override fun above(index: K, includeFirst: Boolean): Set<Int> {
        val results = HashSet<Int>()
        var node:SkipNode? = nearest(index)

        if(node != null && !node.isRecord && node.right> 0)
            node = findNodeAtPosition(node.right)

        while(node != null && node.isRecord) {
            val nodeKey:K = node.getKey(fileStore, storeKeyWithinNode, keyType)
            when {
                index.forceCompare(nodeKey) && includeFirst -> results.add(node.position)
                index.forceCompare(nodeKey, QueryCriteriaOperator.GREATER_THAN) -> results.add(node.position)
            }
            node = if (node.right > 0) findNodeAtPosition(node.right) else null
        }

        return results
    }

    /**
     * Find all references below and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index        The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    override fun below(index: K, includeFirst: Boolean): Set<Int> {
        val results = HashSet<Int>()
        var node:SkipNode? = nearest(index)

        if(node != null && !node.isRecord && node.right> 0)
            node = findNodeAtPosition(node.right)

        while(node != null && node.isRecord) {
            val nodeKey:K = node.getKey(fileStore, storeKeyWithinNode, keyType)

            when {
                index.forceCompare(nodeKey) && includeFirst -> results.add(node.position)
                index.forceCompare(nodeKey, QueryCriteriaOperator.LESS_THAN) -> results.add(node.position)
            }
            node = if (node.left > 0) findNodeAtPosition(node.left) else null
        }

        return results
    }

    /**
     * Find all references between from and to value.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param fromValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeFrom Whether to compare above and equal or not.
     * @param toValue Key to end range to
     * @param includeTo Whether to compare equal or not.
     *
     * @since 2.1.3
     */
    override fun between(fromValue: K?, includeFrom: Boolean, toValue: K?, includeTo: Boolean): Set<Int> {
        val results = HashSet<Int>()
        var node:SkipNode? = nearest(fromValue!!)

        if(node != null && !node.isRecord && node.right> 0)
            node = findNodeAtPosition(node.right)

        node@while(node != null && node.isRecord) {
            val nodeKey:K = node.getKey(fileStore, storeKeyWithinNode, keyType)
            when {
                toValue.forceCompare(nodeKey) && includeTo -> results.add(node.position)
                !toValue.forceCompare(nodeKey, QueryCriteriaOperator.LESS_THAN) -> break@node
                fromValue.forceCompare(nodeKey) && includeFrom -> results.add(node.position)
                fromValue.forceCompare(nodeKey, QueryCriteriaOperator.GREATER_THAN) -> results.add(node.position)
            }
            node = if (node.right > 0) findNodeAtPosition(node.right) else null
        }

        return results
    }

}
