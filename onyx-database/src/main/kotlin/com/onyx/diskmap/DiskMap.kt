package com.onyx.diskmap

import com.onyx.diskmap.data.Header
import com.onyx.diskmap.data.SkipListNode
import com.onyx.diskmap.store.Store
import com.onyx.exception.AttributeTypeMismatchException
import java.lang.reflect.Field
import java.util.*

/**
 * Created by Tim Osborn on 7/30/15.
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
    fun <T : Any?> getAttributeWithRecID(attribute: Field, reference: Long): T

    /**
     * Get Attribute with record id
     *
     * @param field Reflection Field metadata
     * @param reference record reference where the record is stored
     *
     * @return Attribute key of record
     */
    @Throws(AttributeTypeMismatchException::class)
    fun <T : Any?> getAttributeWithRecID(field: Field, reference: SkipListNode<*>): T

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

    /**
     * Added in order to get around requiring Java 8.  This is a workaround
     * for Android older devices.  Works as intended for Map interface
     *
     * @since 2.0.0
     */
    fun compute(key: K, remappingFunction: (K,V?) -> V?): V? {
        Objects.requireNonNull(remappingFunction)
        val oldValue = get(key)

        val newValue = remappingFunction.invoke(key, oldValue)
        return if (newValue == null) {
            // delete mapping
            if (oldValue != null || containsKey(key)) {
                // something to remove
                remove(key)
                null
            } else {
                // nothing to do. Leave things as they were.
                null
            }
        } else {
            // add or replace old mapping
            put(key, newValue)
            newValue
        }
    }

    /**
     * Added in order to get around requiring Java 8.  This is a workaround
     * for Android older devices.  Works as intended for Map interface
     *
     * @since 2.0.0
     */
    fun computeIfPresent(key: K, remappingFunction: (K,V?) -> V?): V? {
        Objects.requireNonNull(remappingFunction)
        val oldValue: V? = get(key)
        return if (oldValue != null) {
            val newValue = remappingFunction.invoke(key, oldValue)
            if (newValue != null) {
                put(key, newValue)
                newValue
            } else {
                remove(key)
                null
            }
        } else {
            null
        }
    }
}
