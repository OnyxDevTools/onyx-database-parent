package com.onyx.persistence.query;

import com.onyx.util.map.CompatHashMap;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.LastRecentlyUsedMap;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This query build on top of the last recently used.  It also stores hard references that do not get removed
 */
public class CachedQueryMap<K, V> extends LastRecentlyUsedMap<K, V> {

    private final CompatMap hardReferenceSet = new CompatHashMap();

    /**
     * Constructor with max capacity
     *
     * @param maxCapacity The maximum number of items in the list
     * @param timeToLive  This indicates the amount of time to live within the cache as seconds
     *                    So, if you specify 60, it will live in the map's cache for 1 minute
     * @since 1.3.0 Used for Query caching
     */
    public CachedQueryMap(@SuppressWarnings("SameParameterValue") int maxCapacity, int timeToLive) {
        super(maxCapacity, timeToLive);
    }

    /**
     * Put an object.  This will wrap it in a Expiration value to track when it was last recently referenced
     *
     * @param key   Map entry key
     * @param value Map entry value
     * @return the value just entered
     * @since 1.3.0
     */
    @SuppressWarnings({"unchecked unused", "UnusedReturnValue"})
    public V putStrongReference(K key, V value) {
        synchronized (this) {
            return (V)hardReferenceSet.put(key, value);
        }
    }

    /**
     * Get the object for key.  Also indicate it as touched so it marks the record as recently used
     *
     * @param key Map entry key
     * @return Value null if it doesnt exist
     */
    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        synchronized (this) {
            Object value = hardReferenceSet.get(key);
            if (value == null)
                return super.get(key);
            return (V) value;
        }
    }

    /**
     * Override to not return the expiration value but the actual value
     *
     * @param action The action to be performed for each entry
     */
    @SuppressWarnings("unchecked")
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        synchronized (this) {
            Set<Map.Entry<K, V>> entrySet = hardReferenceSet.entrySet();
            for (Map.Entry entry : entrySet) {
                action.accept((K) entry.getKey(), (V) entry.getValue());
            }
            super.forEach(action);
        }
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>.
     *         (A <tt>null</tt> return can also indicate that the map
     *         previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        synchronized (this) {
            V value = (V) hardReferenceSet.remove(key);
            if (value == null)
                value = super.remove(key);
            return value;
        }
    }

    /**
     * The compute method only applies to hard references
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return Value that was returned in the remapping function
     */
    @SuppressWarnings("unchecked")
    public V compute(K key,
                      BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        synchronized (this)
        {
            V before = (V)hardReferenceSet.get(key);
            boolean existed = before != null;

            V value = remappingFunction.apply(key, before);
            if(!existed
                    || value != before) {
                hardReferenceSet.put(key, value);
            }

            return value;
        }
    }
}
