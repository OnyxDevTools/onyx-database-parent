package com.onyx.concurrent.impl

/**
 * Created by tosborn1 on 2/19/17.
 *
 * This class is a map that does not perform any actions.  The purpose is so that it can be injected to ignore caching
 */
class EmptyMap<K,V> : MutableMap<K, V> {
    override val size: Int
        get() = 0
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = HashSet()
    override val keys: MutableSet<K>
        get() = HashSet()
    override val values: MutableCollection<V>
        get() = HashSet()

    override fun containsKey(key: K): Boolean = false
    override fun containsValue(value: V): Boolean = false
    override fun get(key: K): V? = null
    override fun isEmpty(): Boolean = true
    override fun clear() {}
    override fun put(key: K, value: V): V? = null
    override fun putAll(from: Map<out K, V>) {}
    override fun remove(key: K): V? = null
}
