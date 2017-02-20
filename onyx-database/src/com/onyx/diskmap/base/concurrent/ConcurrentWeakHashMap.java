package com.onyx.diskmap.base.concurrent;

import java.util.WeakHashMap;

/**
 * Created by tosborn1 on 1/12/17.
 *
 * This map is designed only to be blocking on updates.  Since it is primarily wrapped behind another
 * locking mechanism and updates are typically done on other areas of the cache, there is no need to
 * block upon reads.
 */
public class ConcurrentWeakHashMap<K,V> extends WeakHashMap<K,V> {

    @Override
    public V put(K key, V value) {
        synchronized (this)
        {
            return super.put(key, value);
        }
    }

    @Override
    public V remove(Object key) {
        synchronized (this)
        {
            return super.remove(key);
        }
    }
}
