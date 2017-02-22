package com.onyx.diskmap;

import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.diskmap.base.concurrent.LevelReadWriteLock;
import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.node.SkipListNode;
import com.onyx.diskmap.store.Store;
import com.onyx.util.OffsetField;

import java.util.Map;
import java.util.Set;

/**
 * Created by tosborn1 on 7/30/15.
 *
 * This is the an interface that extends the functionality of a Map.  It also contains methods that assist in finding
 * records by references within a store / volume.
 */
public interface DiskMap<K,V> extends Map<K,V> {

    /**
     * Get the record id for a key
     *
     * @param key Record Key
     * @return The position within a store/volume
     */
    long getRecID(Object key);

    /**
     * Get key with record id
     *
     * @param recordId Position within store
     * @return Record value
     */
    V getWithRecID(long recordId);

    /**
     * Get structure key with record id
     *
     * @param recordId Position within Store
     * @return Record in format of a Map
     */
    Map getMapWithRecID(long recordId);

    /**
     * Get Attribute with record id
     *
     * @param attribute attribute name to gather
     * @param reference record reference where the record is stored
     *
     * @return Attribute key of record
     */
    Object getAttributeWithRecID(String attribute, long reference) throws AttributeTypeMismatchException;

    /**
     * Get Attribute with record id
     *
     * @param field Reflection Field metadata
     * @param reference record reference where the record is stored
     *
     * @return Attribute key of record
     */
    Object getAttributeWithRecID(OffsetField field, SkipListNode reference) throws AttributeTypeMismatchException;

    /**
     * Get Storage mechanism for a dismap
     *
     * @return The physical file store
     */
    @SuppressWarnings("unused")
    Store getFileStore();

    /**
     * Gets the reference of where the disk structure is located within the storage
     * @since 1.0.2
     * @return Header reference item
     */
    Header getReference();

    /**
     * Get the set of references.  Not values nor keys
     * @return Set of references.
     */
    Set referenceSet();

    /**
     * Get the level read write lock implementation.
     *
     * @since 1.2.0
     * @return Null if it does not apply.
     */
    LevelReadWriteLock getReadWriteLock();

}
