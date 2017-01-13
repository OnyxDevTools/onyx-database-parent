package com.onyx.structure;

import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.structure.base.LevelReadWriteLock;
import com.onyx.structure.node.Header;
import com.onyx.structure.store.Store;

import java.util.Map;

/**
 * Created by tosborn1 on 7/30/15.
 */
public interface DiskMap<K,V> extends Map<K,V> {

    /**
     * Get the record id for a key
     *
     * @param key
     * @return
     */
    long getRecID(Object key);

    /**
     * Get key with record id
     *
     * @param recordId
     * @return
     */
    V getWithRecID(long recordId);

    /**
     * Get structure key with record id
     *
     * @param recordId
     * @return
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
     * Get Storage mechanism for a dismap
     *
     * @return The physical file store
     */
    Store getFileStore();

    /**
     * Gets the reference of where the disk structure is located within the storage
     * @since 1.0.2
     * @return Header reference item
     */
    Header getReference();
}
