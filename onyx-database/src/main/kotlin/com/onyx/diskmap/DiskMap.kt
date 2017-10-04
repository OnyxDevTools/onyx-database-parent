package com.onyx.diskmap

import com.onyx.concurrent.DispatchLock
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipListNode
import com.onyx.diskmap.store.Store
import com.onyx.exception.AttributeTypeMismatchException
import com.onyx.util.ReflectionField

/**
 * Created by tosborn1 on 7/30/15.
 *
 * This is the an interface that extends the functionality of a Map.  It also contains methods that assist in finding
 * records by references within a store / volume.
 */
interface DiskMap<K, V> : MutableMap<K, V> {

    /**
     * Get Storage mechanism for a DiskMap
     *
     * @return The physical file store
     */
    val fileStore: Store

    /**
     * Gets the reference of where the disk structure is located within the storage
     * @since 1.0.2
     * @return Header reference item
     */
    val reference: Header

    /**
     * Get the level read write lock implementation.
     *
     * @since 1.2.0
     * @return Null if it does not apply.
     */
    val readWriteLock: DispatchLock

    /**
     * Get the set of references.  Not values nor keys
     * @return Set of references.
     */
    val references: Set<SkipListNode<K>>

    /**
     * Get the record id for a key
     *
     * @param key Record Key
     * @return The position within a store/volume
     */
    fun getRecID(key: K): Long

    /**
     * Get key with record id
     *
     * @param recordId Position within store
     * @return Record value
     */
    fun getWithRecID(recordId: Long): V?

    /**
     * Get structure key with record id
     *
     * @param recordId Position within Store
     * @return Record in format of a Map
     */
    fun getMapWithRecID(recordId: Long): Map<String, Any?>?

    /**
     * Get Attribute with record id
     *
     * @param attribute attribute field to gather
     * @param reference record reference where the record is stored
     *
     * @return Attribute key of record
     */
    @Throws(AttributeTypeMismatchException::class)
    fun <T : Any?> getAttributeWithRecID(attribute: ReflectionField, reference: Long): T

    /**
     * Get Attribute with record id
     *
     * @param field Reflection Field metadata
     * @param reference record reference where the record is stored
     *
     * @return Attribute key of record
     */
    @Throws(AttributeTypeMismatchException::class)
    fun <T : Any?> getAttributeWithRecID(field: ReflectionField, reference: SkipListNode<*>): T

    /**
     * Returns the record count as a long rather than an integer.
     *
     * @return size in format of a long
     * @since 1.3.0
     */
    fun longSize(): Long

    /**
     * Find all references above and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index        The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    fun above(index: K, includeFirst: Boolean): Set<Long>

    /**
     * Find all references below and perhaps equal to the key you are sending in.  The underlying data structure
     * is sorted so this should be very efficient
     *
     * @param index        The index value to compare.  This must be comparable.  It does not work with hash codes.
     * @param includeFirst Whether above and equals to
     * @return A Set of references
     * @since 1.2.0
     */
    fun below(index: K, includeFirst: Boolean): Set<Long>
}
