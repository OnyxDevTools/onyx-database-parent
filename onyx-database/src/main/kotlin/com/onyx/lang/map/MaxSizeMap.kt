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

    override fun put(key: K, value: V): V? {
        @Suppress("UNCHECKED_CAST")
        super.put(key, value) as V
        var attempts = 0
        while (size > maxCapacity && attempts < 100) {
            val ejected = order.first()
            if(shouldEject?.invoke(ejected, value) == true) {
                onEject?.invoke(super.get(ejected)!!)
                remove(ejected)
            } else {
                attempts++
                order.remove(ejected)
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
    override operator fun get(key: K): V? {
        val value = super.get(key)
        if(value != null) {
            order.remove(key)
            order.add(key)
        }

        return value
    }

    override fun remove(key: K): V? {
        order.remove(key)
        return super.remove(key)
    }

    override fun clear() {
        order.clear()
        super.clear()
    }
}
