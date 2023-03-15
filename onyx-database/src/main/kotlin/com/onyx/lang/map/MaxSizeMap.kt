package com.onyx.lang.map

import java.lang.Exception
import java.util.HashMap

/**
 * Created by Tim Osborn on 3/13/23.
 *
 * This map will retain value that are most recently used.
 */
open class MaxSizeMap<K, V> @JvmOverloads constructor(private val maxCapacity: Int, private val shouldEject: ((K, V) -> Boolean)? = null, private val onEject: ((V) -> Unit)? = null) : HashMap<K, V>(maxCapacity + 1, 1.0f) {

    init {
        if(maxCapacity <= 0) throw Exception("Invalid map capacity")
    }

    private val order: LinkedHashSet<K> = LinkedHashSet(maxCapacity)

    @Synchronized
    override fun put(key: K, value: V): V? {
        @Suppress("UNCHECKED_CAST")
        super.put(key, value) as V
        var attempts = 0
        while (size > maxCapacity && attempts < size) {
            val ejected = order.first()
            val ejectValue = super.get(ejected)
            order.remove(ejected)
            if(ejectValue == null) {
                attempts++
            }
            else if(shouldEject?.invoke(ejected, ejectValue) == true) {
                onEject?.invoke(ejectValue)
                remove(ejected)
            } else {
                attempts++
                order.add(ejected)
            }
        }
        order.add(key)
        return value
    }

    /**
     * Get the object for key.  Also indicate it as touched so it marks the record as recently used
     *
     * @param key Map entry key
     * @return Value null if it doesn't exist
     */
    @Synchronized
    override operator fun get(key: K): V? {
        val value = super.get(key)
        if(value != null) {
            order.remove(key)
            order.add(key)
        }

        return value
    }

    @Synchronized
    override fun remove(key: K): V? {
        order.remove(key)
        return super.remove(key)
    }

    @Synchronized
    override fun clear() {
        order.clear()
        super.clear()
    }
}
