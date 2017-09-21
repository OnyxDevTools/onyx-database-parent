package com.onyx.interactors.cache.data
import com.onyx.util.map.LastRecentlyUsedMap

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This query build on top of the last recently used.  It also stores hard references that do not get removed
 */
class CachedQueryMap<K, V>(maxCapacity: Int, timeToLive: Int) : LastRecentlyUsedMap<K, V>(maxCapacity, timeToLive) {

    private val hardReferenceSet = HashMap<K,V?>()

    /**
     * Put an object.  This will wrap it in a Expiration value to track when it was last recently referenced
     *
     * @param key   Map entry key
     * @param value Map entry value
     * @return the value just entered
     * @since 1.3.0
     */
    fun putStrongReference(key: K, value: V): V = synchronized(this) { hardReferenceSet.put(key, value); value }

    /**
     * Get the object for key.  Also indicate it as touched so it marks the record as recently used
     *
     * @param key Map entry key
     * @return Value null if it doesnt exist
     */
    override operator fun get(key: K): V? = synchronized(this) { return hardReferenceSet[key] ?: return super.get(key) }

    /**
     * Override to not return the expiration value but the actual value
     *
     * @param action The action to be performed for each entry
     */
    fun forEach(action: (K,V?) -> Unit) = synchronized(this) {
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
    override fun remove(key: K): V = synchronized(this) {
        var value: V? = hardReferenceSet.remove(key)
        if (value == null)
            value = super.remove(key)
        return value!!
    }

    /**
     * The compute method only applies to hard references
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return Value that was returned in the remapping function
     */
    fun compute(key: K, remappingFunction: (K, V?) -> V ): V = synchronized(this) {
        val before = hardReferenceSet[key]
        val existed = before != null

        val value = remappingFunction.invoke(key, before)
        if (!existed || value !== before) {
            hardReferenceSet.put(key, value)
        }

        return value
    }
}
