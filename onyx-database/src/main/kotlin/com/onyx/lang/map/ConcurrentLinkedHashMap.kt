package com.onyx.lang.map

import java.util.concurrent.locks.ReentrantLock
import java.util.function.BiFunction

/**
 * A thread-safe implementation of a [LinkedHashMap].
 * This class provides concurrent access and modification capabilities through an internal [ReentrantLock].
 * It can be configured to function as an LRU (Least Recently Used) cache by specifying a `maxCapacity`.
 * When the `maxCapacity` is reached, the eldest entries (based on access order if `accessOrder` is true,
 * or insertion order otherwise) are evicted.
 *
 * All public methods that access or modify the map's state are synchronized.
 *
 * @param K The type of keys maintained by this map.
 * @param V The type of values mapped.
 * @property maxCapacity The maximum number of entries this map can hold. When exceeded, eviction occurs.
 * Set to [Int.MAX_VALUE] for no practical limit (behaves like a standard synchronized LinkedHashMap).
 * @param initialCapacity The initial capacity of the map.
 * @param loadFactor The load factor for the hash table.
 * @param accessOrder If true, the map orders entries based on access (LRU policy);
 * if false, it orders entries based on insertion. This parameter is crucial for LRU cache behavior.
 * @property onEvict A callback function that is invoked with the value of an entry when it is evicted from the map
 * due to capacity constraints or explicit removal by [removeEldestEntry].
 */
open class ConcurrentLinkedHashMap<K, V>(
    maxCapacity: Int,
    initialCapacity: Int = 16,
    loadFactor: Float = 0.75f,
    accessOrder: Boolean = true,
    private val onEvict: (V) -> Unit
) :
    LinkedHashMap<K, V>(
        (if (initialCapacity > maxCapacity) maxCapacity else initialCapacity).coerceAtLeast(1),
        loadFactor,
        accessOrder
    ) {

    private val lock = ReentrantLock() // Non-fair lock for potentially better throughput

    // Stores the maximum capacity for LRU eviction. Int.MAX_VALUE means no practical limit for eviction.
    private val maxCapacityValue: Int = maxCapacity

    /**
     * Removes a specified number of the eldest entries from this map.
     * The "eldest" entry is determined by the map's iteration order (access order if `accessOrder` is true,
     * otherwise insertion order).
     * The [onEvict] callback is invoked for each removed entry's value.
     * This operation is thread-safe.
     *
     * @param count The number of eldest entries to remove.
     */
    fun removeEldestEntry(count: Int) {
        lock.lock()
        try {
            repeat(count) {
                val entry = this.entries.firstOrNull() // entries here refers to the super.entries
                if (entry != null) {
                    onEvict(entry.value)
                    super.remove(entry.key) // Use super.remove to bypass our own locking remove
                } else {
                    return // No more entries to remove
                }
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Determines whether the eldest entry should be removed from the map.
     * This method is called by `put` and `putAll` operations after an entry has been inserted.
     * It facilitates LRU eviction: if the map's size exceeds `maxCapacityValue`,
     * this method returns `true`, leading to the removal of the eldest entry.
     * The [onEvict] callback is invoked for the value of the eldest entry if it is removed.
     * The lock is already held by the calling method (e.g., `put`) when this is invoked.
     *
     * @param eldest The eldest entry in the map.
     * @return `true` if the eldest entry should be removed, `false` otherwise.
     */
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
        if (size > maxCapacityValue) {
            onEvict(eldest.value)
            return true
        }
        return false
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or `null` if this map contains no mapping for the key.
     * If `accessOrder` is true, this operation records the access, moving the entry to the
     * most-recently-used position. This operation is thread-safe.
     *
     * @param key The key whose associated value is to be returned.
     * @return The value to which the specified key is mapped, or `null` if no such mapping exists.
     */
    override fun get(key: K): V? {
        lock.lock()
        try {
            return super.get(key)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is replaced.
     * If `maxCapacityValue` is set and the map exceeds this capacity after the insertion,
     * the eldest entry may be evicted. This operation is thread-safe.
     *
     * @param key The key with which the specified value is to be associated.
     * @param value The value to be associated with the specified key.
     * @return The previous value associated with `key`, or `null` if there was no mapping for `key`.
     */
    override fun put(key: K, value: V): V? {
        lock.lock()
        try {
            return super.put(key, value)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Removes the mapping for a key from this map if it is present.
     * This operation is thread-safe.
     *
     * @param key The key whose mapping is to be removed from the map.
     * @return The previous value associated with `key`, or `null` if there was no mapping for `key`.
     */
    override fun remove(key: K): V? {
        lock.lock()
        try {
            // Note: The onEvict callback is NOT called for explicit removals via this method.
            // It is only called during removeEldestEntry (automatic or manual count).
            return super.remove(key)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Removes the entry for the specified key only if it is currently mapped to the specified value.
     * This operation is thread-safe. If `accessOrder` is true, checking the value might update
     * the entry's access order even if it's not removed.
     *
     * @param key The key whose mapping is to be removed.
     * @param value The value expected to be associated with the specified key.
     * @return `true` if the value was removed, `false` otherwise.
     */
    override fun remove(key: K, value: V): Boolean {
        lock.lock()
        try {
            // Note: The onEvict callback is NOT called for explicit removals via this method.
            // super.get() is called on the LinkedHashMap parent.
            // If accessOrder is true, super.get() will correctly update the order.
            if (super.containsKey(key) && super.get(key) == value) {
                super.remove(key) // super.remove() on LinkedHashMap parent.
                return true
            }
            return false
        } finally {
            lock.unlock()
        }
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     * These mappings will replace any mappings that this map had for any of the keys
     * currently in the specified map.
     * If configured as an LRU cache, this operation may trigger multiple evictions if the
     * `maxCapacityValue` is exceeded. This operation is thread-safe.
     *
     * @param from The map whose mappings are to be stored in this map.
     */
    override fun putAll(from: Map<out K, V>) {
        lock.lock()
        try {
            super.putAll(from)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     * The [onEvict] callback is NOT called for entries removed by this operation.
     * This operation is thread-safe.
     */
    override fun clear() {
        lock.lock()
        try {
            // Note: onEvict is not called for entries removed by clear().
            // If individual eviction callbacks are needed, entries should be removed one by one
            // or via removeEldestEntry(count).
            super.clear()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Returns `true` if this map contains a mapping for the specified key.
     * This operation is thread-safe. If `accessOrder` is true, this may affect the entry's position.
     *
     * @param key The key whose presence in this map is to be tested.
     * @return `true` if this map contains a mapping for the specified key.
     */
    override fun containsKey(key: K): Boolean {
        lock.lock()
        try {
            // Note: super.containsKey does not affect access order in LinkedHashMap.
            // Only get() does. If peek-like behavior without affecting order is needed,
            // it's not directly available while maintaining thread-safety and accessOrder updates.
            return super.containsKey(key)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Returns `true` if this map maps one or more keys to the specified value.
     * This operation requires time linear in the map size and is thread-safe.
     *
     * @param value The value whose presence in this map is to be tested.
     * @return `true` if this map maps one or more keys to the specified value.
     */
    override fun containsValue(value: V): Boolean {
        lock.lock()
        try {
            return super.containsValue(value)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Returns the number of key-value mappings in this map.
     * This operation is thread-safe.
     */
    override val size: Int
        get() {
            lock.lock()
            try {
                return super.size
            } finally {
                lock.unlock()
            }
        }

    /**
     * Returns `true` if this map contains no key-value mappings.
     * This operation is thread-safe.
     */
    override fun isEmpty(): Boolean {
        lock.lock()
        try {
            return super.isEmpty()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Returns a [MutableSet] view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are reflected in the set, and vice-versa.
     * **However, for thread safety when iterating, it is recommended to create a snapshot, e.g., `LinkedHashSet(map.keys)`.**
     * Direct iteration over this returned set without external synchronization is not thread-safe if the map can be modified concurrently.
     * This implementation returns a snapshot ([LinkedHashSet]) to ensure thread-safety during iteration over the returned set.
     */
    override val keys: MutableSet<K>
        get() {
            lock.lock()
            try {
                return LinkedHashSet(super.keys) // Return a snapshot for thread-safe iteration
            } finally {
                lock.unlock()
            }
        }

    /**
     * Returns a [MutableCollection] view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are reflected in the collection, and vice-versa.
     * **However, for thread safety when iterating, it is recommended to create a snapshot, e.g., `ArrayList(map.values)`.**
     * Direct iteration over this returned collection without external synchronization is not thread-safe if the map can be modified concurrently.
     * This implementation returns a snapshot ([ArrayList]) to ensure thread-safety during iteration over the returned collection.
     */
    override val values: MutableCollection<V>
        get() {
            lock.lock()
            try {
                return ArrayList(super.values) // Return a snapshot for thread-safe iteration
            } finally {
                lock.unlock()
            }
        }

    /**
     * Returns a [MutableSet] view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are reflected in the set, and vice-versa.
     * **However, for thread safety when iterating, it is recommended to create a snapshot.**
     * Direct iteration over this returned set without external synchronization is not thread-safe if the map can be modified concurrently.
     * This implementation returns a snapshot of immutable entries ([SimpleImmutableEntry]) within a [LinkedHashSet]
     * to ensure thread-safety during iteration over the returned set.
     */
    @Suppress("UNCHECKED_CAST")
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() {
            lock.lock()
            try {
                // Create a snapshot with immutable entries for thread-safe iteration
                val snapshot = LinkedHashSet<Map.Entry<K, V>>(super.size)
                for (entry in super.entries) {
                    snapshot.add(SimpleImmutableEntry(entry.key, entry.value))
                }
                // The cast is safe because SimpleImmutableEntry implements Map.Entry,
                // and MutableMap.MutableEntry is a subtype of Map.Entry.
                // However, the entries themselves are immutable.
                return snapshot as MutableSet<MutableMap.MutableEntry<K, V>>
            } finally {
                lock.unlock()
            }
        }

    /**
     * Compares the specified object with this map for equality.
     * Returns true if the given object is also a map and the two maps represent the same mappings.
     * This operation is thread-safe.
     *
     * @param other The object to be compared for equality with this map.
     * @return `true` if the specified object is equal to this map.
     */
    override fun equals(other: Any?): Boolean {
        lock.lock()
        try {
            return super.equals(other)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Returns the hash code value for this map.
     * The hash code of a map is defined to be the sum of the hash codes of each entry in the map's entrySet view.
     * This operation is thread-safe.
     *
     * @return The hash code value for this map.
     */
    override fun hashCode(): Int {
        lock.lock()
        try {
            return super.hashCode()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Returns a string representation of this map.
     * The string representation consists of a list of key-value mappings in the order returned by the map's
     * `entrySet` view, enclosed in braces ("{}").
     * This operation is thread-safe.
     *
     * @return A string representation of this map.
     */
    override fun toString(): String {
        lock.lock()
        try {
            return super.toString()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Attempts to compute a mapping for the specified key and its current mapped value (or `null` if there is no current mapping).
     * The remappingFunction is applied atomically under the map's lock.
     * If the function returns `null`, the mapping is removed (or remains absent if initially absent).
     * If the function returns a non-null value, the existing mapping is replaced or a new one is created.
     * This operation is thread-safe.
     *
     * @param key The key with which the specified value is to be associated.
     * @param remappingFunction The function to compute a value.
     * @return The new value associated with the specified key, or `null` if none.
     */
    override fun compute(key: K, remappingFunction: BiFunction<in K, in V?, out V?>): V? {
        lock.lock()
        try {
            return super.compute(key, remappingFunction)
        } finally {
            lock.unlock()
        }
    }

    /**
     * A simple immutable entry, used for creating safe snapshots of map entries.
     */
    private class SimpleImmutableEntry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V> {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Map.Entry<*, *>) return false
            return key == other.key && value == other.value
        }

        override fun hashCode(): Int = key.hashCode() xor value.hashCode()
        override fun toString(): String = "$key=$value"
    }
}