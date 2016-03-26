package com.onyx.map;

import com.onyx.map.store.Store;

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
     * Get value with record id
     *
     * @param recordId
     * @return
     */
    V getWithRecID(long recordId);

    /**
     * Get Storage mechanism for a dismap
     *
     * @return
     */
    Store getFileStore();
}
