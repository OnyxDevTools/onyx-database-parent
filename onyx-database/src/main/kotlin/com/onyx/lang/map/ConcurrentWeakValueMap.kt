package com.onyx.lang.map

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * A thread-safe key-to-weak-value lookup intended for caches.
 *
 * Keys remain strongly reachable while their entries are present, but values may be collected as
 * soon as callers release their last strong reference. Consequently, a successful write does not
 * guarantee that a later read will return a value.
 *
 * Collected entries are removed opportunistically during subsequent operations. Cleanup uses a
 * conditional remove, preventing an older queued reference from deleting a newer value installed
 * for the same key. [putIfAbsent] likewise treats a collected value as absent and atomically
 * replaces its stale reference.
 */
class ConcurrentWeakValueMap<K : Any, V : Any> {

    private val collectedValues = ReferenceQueue<V>()
    private val values = ConcurrentHashMap<K, ValueReference<K, V>>()

    operator fun get(key: K): V? {
        removeCollectedValues()
        val reference = values[key] ?: return null
        val value = reference.get()
        if (value == null) values.remove(key, reference)
        return value
    }

    operator fun set(key: K, value: V) {
        put(key, value)
    }

    fun put(key: K, value: V): V? {
        removeCollectedValues()
        return values.put(key, ValueReference(key, value, collectedValues))?.get()
    }

    /**
     * Publishes [value] unless a live value is already associated with [key].
     * Returns the existing live value, or `null` when [value] was published.
     */
    fun putIfAbsent(key: K, value: V): V? {
        removeCollectedValues()
        val replacement = ValueReference(key, value, collectedValues)
        while (true) {
            val existing = values.putIfAbsent(key, replacement) ?: return null
            existing.get()?.let { return it }
            if (values.replace(key, existing, replacement)) return null
        }
    }

    fun remove(key: K): V? {
        removeCollectedValues()
        return values.remove(key)?.get()
    }

    fun clear() {
        values.clear()
        while (collectedValues.poll() != null) {
            // Discard references detached by the clear.
        }
    }

    internal fun retainedEntryCount(): Int {
        removeCollectedValues()
        return values.size
    }

    private fun removeCollectedValues() {
        while (true) {
            @Suppress("UNCHECKED_CAST")
            val reference = collectedValues.poll() as ValueReference<K, V>? ?: return
            values.remove(reference.key, reference)
        }
    }

    private class ValueReference<K : Any, V : Any>(
        val key: K,
        value: V,
        queue: ReferenceQueue<V>
    ) : WeakReference<V>(value, queue)
}
