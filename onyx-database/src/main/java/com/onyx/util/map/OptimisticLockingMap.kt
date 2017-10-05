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
        val value: V? = get(key)
        return value ?: lock.writeLock {
            var newValue = m[key]
            if (newValue == null) {
                newValue = body.invoke()
                m.put(key, newValue)
            }
            return@writeLock newValue
        }!!
    }

}
