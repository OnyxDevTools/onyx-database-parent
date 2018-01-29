package com.onyx.lang.map

/**
 * Created by Tim Osborn on 3/24/17.
 *
 * This map only locks on write access.  It is non blocking for read access.
 */
class WriteSynchronizedMap<K, V>(m: MutableMap<K, V>) : OptimisticLockingMap<K, V>(m) {
    override operator fun get(key: K): V? = m[key]
}
