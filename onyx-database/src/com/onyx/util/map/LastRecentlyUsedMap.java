package com.onyx.util.map;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Created by tosborn1 on 3/24/17.
 *
 * This map will retain object that are most recently used.  If an object has expired, it will
 * eject the last recently used object.
 */
public class LastRecentlyUsedMap<K,V> extends LinkedHashMap<K,V> implements CompatMap<K,V> {

    @SuppressWarnings({"FieldCanBeLocal", "WeakerAccess"})
    protected final int maxCapacity = 100;
    @SuppressWarnings("WeakerAccess")
    protected int timeToLive = 60*5*1000; // 5 minuts

    /**
     * Constructor with max capacity
     * @param maxCapacity The maximum number of items in the list
     * @param timeToLive This indicates the amount of time to live within the cache as seconds
     *                   So, if you specify 60, it will live in the map's cache for 1 minute
     *
     * @since 1.3.0 Used for Query caching
     */
    @SuppressWarnings("WeakerAccess")
    public LastRecentlyUsedMap(@SuppressWarnings("SameParameterValue") int maxCapacity, int timeToLive)
    {
        super(maxCapacity+1, 1.0f);
        this.timeToLive = timeToLive * 1000;
    }


    /**
     * The eldest entry should be removed when we reached the maximum cache size.
     *
     * Also if the object has expired.
     *
     * @since 1.3.0
     */
    @SuppressWarnings("unchecked")
    @Override
    protected boolean removeEldestEntry(final Map.Entry<K, V> eldest) {
        return size() >= maxCapacity
                || (((ExpirationValue)eldest.getValue()).lastTouched + timeToLive) < System.currentTimeMillis();
    }

    /**
     * Put an object.  This will wrap it in a Expiration value to track when it was last recently referenced
     * @param key Map entry key
     * @param value Map entry value
     * @return the value just entered
     *
     * @since 1.3.0
     */
    @Override
    @SuppressWarnings("unchecked")
    public V put(K key, V value)
    {
        synchronized (this) {
            super.put(key, (V) new ExpirationValue(System.currentTimeMillis(), value));
            return value;
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
            final ExpirationValue expirationValue = (ExpirationValue) super.remove(key);
            if (expirationValue != null) {
                expirationValue.lastTouched = System.currentTimeMillis();
                super.put((K) key, (V) expirationValue);
            } else {
                return null;
            }

            return (V) expirationValue.value;
        }
    }

    /**
     * POJO for tracking last touched
     */
    public static class ExpirationValue
    {
        long lastTouched;
        final Object value;

        /**
         * Constructor with timestamp of touched and underlying value
         * @param lastTouched Record last touched
         * @param value Object value
         */
        ExpirationValue(long lastTouched, Object value)
        {
            this.lastTouched = lastTouched;
            this.value = value;
        }

        /**
         * Hash Code
         * @return The value's hash code
         */
        @Override
        public int hashCode()
        {
            return value.hashCode();
        }

        /**
         * Equals compares the values equals
         * @param o Object to compare
         * @return Whether the values are equal
         */
        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object o)
        {
            return (o instanceof ExpirationValue && ((ExpirationValue) o).value.equals(value));
        }
    }

    /**
     * Override to not return the expiration value but the actual value
     * @param action The action to be performed for each entry
     */
    @SuppressWarnings("unchecked")
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        synchronized (this) {
            Set<Map.Entry<K, V>> entrySet = entrySet();
            for (Map.Entry entry : entrySet) {
                action.accept((K) entry.getKey(), (V)((ExpirationValue) entry.getValue()).value);
            }
        }
    }
}
