package com.onyx.util.map;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by tosborn1 on 3/24/17.
 */
public class LastRecentlyUsedMap<K,V> extends LinkedHashMap<K,V> implements CompatMap<K,V> {

    private int maxCapacity = 100;

    /**
     * Constructor with max capacity
     * @param maxCapacity The maximum number of items in the list
     */
    public LastRecentlyUsedMap(int maxCapacity)
    {
        super(maxCapacity+1, 1.0f);
    }


    /**
     * The eldest entry should be removed when we reached the maximum cache size
     */
    @Override
    protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
        return size() >= maxCapacity;
    }

    public V get(Object key) {
        V value = super.remove(key);
        if(value != null)
            super.put((K)key, value);
        return value;
    }
}
