package com.onyx.util.map

import com.onyx.concurrent.impl.DefaultDispatchReadWriteLock

/**
 * Created by tosborn1 on 3/24/17.
 *
 * This map only locks on write access.  It is non blocking for read access.
 */
class OptimisticLockingMap<K, V>(private val m: MutableMap<K, V>) : MutableMap<K, V> {

    private val lock = DefaultDispatchReadWriteLock()

    // region Properties

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = lock.optimisticReadLock { m.entries }
    override val keys: MutableSet<K>
        get() = lock.optimisticReadLock { m.keys }
    override val values: MutableCollection<V>
        get() = lock.optimisticReadLock { m.values }
    override val size: Int
        get() = lock.optimisticReadLock { m.size }

    // endregion

    // region Overload Read Methods

    override fun isEmpty(): Boolean = lock.optimisticReadLock { m.isEmpty() }
    override fun containsKey(key: K): Boolean = lock.optimisticReadLock { m.containsKey(key) }
    override fun containsValue(value: V): Boolean = lock.optimisticReadLock { m.containsValue(value) }
    override operator fun get(key: K): V? = lock.optimisticReadLock { m[key] }

    // endregion

    // region Overload Write Synchronized Methods

    override fun put(key: K, value: V): V? = lock.writeLock { m.put(key, value) }
    override fun remove(key: K): V? = lock.writeLock { m.remove(key) }
    override fun putAll(from: Map<out K, V>) = lock.writeLock { m.putAll(from) }
    override fun clear() = lock.writeLock { m.clear() }

    // endregion
}
