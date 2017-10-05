package com.onyx.lang.map

import java.util.LinkedHashMap

/**
 * Created by tosborn1 on 3/24/17.
 *
 * This map will retain value that are most recently used.  If an value has expired, it will
 * eject the last recently used object.
 */
open class LastRecentlyUsedMap<K, V> @JvmOverloads constructor(maxCapacity: Int, private var timeToLive: Int = 60 * 5 * 1000) : LinkedHashMap<K, V>(maxCapacity + 1, 1.0f) {

    private val maxCapacity = 100

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size >= maxCapacity || (eldest!!.value as ExpirationValue).lastTouched + timeToLive < System.currentTimeMillis()

    override fun put(key: K, value: V): V? {
        @Suppress("UNCHECKED_CAST")
        super.put(key, ExpirationValue(System.currentTimeMillis(), value as Any) as V)
        return value
    }

    /**
     * Get the object for key.  Also indicate it as touched so it marks the record as recently used
     *
     * @param key Map entry key
     * @return Value null if it doesn't exist
     */
    @Suppress("UNCHECKED_CAST")
    override operator fun get(key: K): V? {
        val expirationValue = super.remove(key) as ExpirationValue?
        if (expirationValue != null) {
            expirationValue.lastTouched = System.currentTimeMillis()
            super.put(key, expirationValue as V)
        } else {
            return null
        }

        return expirationValue.value as V
    }

    /**
     * Override to not return the expiration value but the actual value
     * @param action The action to be performed for each entry
     */
    @Suppress("UNCHECKED_CAST")
    open fun forEach(action: (K,V?) -> Unit) = entries.forEach { action.invoke(it.key, (it.value as ExpirationValue).value as V)}

    /**
     * POJO for tracking last touched
     */
    class ExpirationValue internal constructor(internal var lastTouched: Long, internal val value: Any) {

        /**
         * Hash Code
         * @return The value's hash code
         */
        override fun hashCode(): Int = value.hashCode()

        /**
         * Equals compares the values equals
         * @param other Object to compare
         * @return Whether the values are equal
         */
        override fun equals(other: Any?): Boolean = other is ExpirationValue && other.value == value
    }

}
