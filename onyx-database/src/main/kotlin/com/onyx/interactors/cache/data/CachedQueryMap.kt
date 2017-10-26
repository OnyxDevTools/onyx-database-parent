package com.onyx.interactors.cache.data
import com.onyx.lang.concurrent.impl.DefaultClosureReadWriteLock
import com.onyx.lang.map.LastRecentlyUsedMap

/**
 * Created by Tim Osborn on 3/27/17.
 *
 * This query build on top of the last recently used.  It also stores hard references that do not get removed
 */
class CachedQueryMap<K, V>(maxCapacity: Int, timeToLive: Int) : LastRecentlyUsedMap<K, V>(maxCapacity, timeToLive) {

    private val lock = DefaultClosureReadWriteLock()

    private val hardReferenceSet = HashMap<K,V?>()

    /**
     * Put an object.  This will wrap it in a Expiration value to track when it was last recently referenced
     *
     * @param key   Map entry key
     * @param value Map entry value
     * @return the value just entered
     * @since 1.3.0
     */
    fun putStrongReference(key: K, value: V): V = lock.writeLock { hardReferenceSet.put(key, value); value }

    /**
     * Get the object for key.  Also indicate it as touched so it marks the record as recently used
     *
     * @param key Map entry key
     * @return Value null if it doesn't exist
     */
    override operator fun get(key: K): V? = lock.optimisticReadLock { return@optimisticReadLock hardReferenceSet[key] ?: return@optimisticReadLock super.get(key) }

    /**
     * Override to not return the expiration value but the actual value
     *
     * @param action The action to be performed for each entry
     */
    override fun forEach(action: (K,V?) -> Unit) = lock.readLock {
        hardReferenceSet.entries.forEach { action.invoke(it.key, it.value) }
        super.forEach(action)
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * (A <tt>null</tt> return can also indicate that the map
     * previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    override fun remove(key: K): V = lock.writeLock {
        var value: V? = hardReferenceSet.remove(key)
        if (value == null)
            value = super.remove(key)
        return@writeLock value!!
    }

    /**
     * Override to ensure thread safety
     */
    override fun put(key: K, value: V): V? = lock.writeLock { return@writeLock super.put(key, value) }

    /**
     * Get or put overridden so that it first uses optimistic locking.  If it failed to return a value, check again
     * in order to account for a race condition.  Lastly put a new value by calling the unit.
     *
     * I found the Kotlin extension method was not in fact thread safe as the documentation claims with the ConcurrentHashMap
     * so this is here to account for that
     *
     * This does call the get method an extra time.  Using this map implies it is heavy on the read access and therefore
     * need a non blocking read whereas it is acceptable to have slower write times since it is not write heavy.
     *
     * @since 2.0.0
     */
    fun getOrPut(key: K, body: () -> V): V {
        val value: V? = lock.optimisticReadLock { hardReferenceSet[key] }
        return value ?: lock.writeLock {
            var newValue = hardReferenceSet[key]
            if(newValue == null) {
                newValue = super.get(key)
                hardReferenceSet.put(key, newValue)
            }
            if (newValue == null) {
                newValue = body.invoke()
                hardReferenceSet.put(key, newValue)
            }
            return@writeLock newValue
        }!!
    }

}
