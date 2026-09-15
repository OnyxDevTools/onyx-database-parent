package com.onyx.lang.map

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Bounded, strong-reference cache using CLOCK (second-chance) eviction to approximate LRU.
 *
 * Hits do not acquire [writeLock] or move entries: they look up a node and, if needed, set its
 * volatile reference bit. Structural changes and value replacements share one writer lock.
 * Eviction clears reference bits while advancing around a ring, preferring unreferenced nodes.
 * A scan is capped at one full revolution so concurrent hits cannot starve a writer; if every
 * candidate receives another hit during the scan, the starting candidate is evicted anyway.
 *
 * Individual map operations are thread-safe. Iterators are weakly consistent, like those of
 * [ConcurrentHashMap]; compound operations such as get-then-put are not atomic. Keys and values
 * must be non-null. The number of mapped entries never exceeds [maxCapacity].
 */
class ConcurrentClockCache<K : Any, V : Any>(private val maxCapacity: Int) : AbstractMutableMap<K, V>() {
    init {
        require(maxCapacity > 0) { "Cache capacity must be positive" }
    }

    private val table = ConcurrentHashMap<K, Node<K, V>>()
    private val writeLock = ReentrantLock()
    // Only writers access the ring. Readers never follow next/previous links.
    private var hand: Node<K, V>? = null

    override val size: Int
        get() = table.size

    override fun containsKey(key: K): Boolean = table.containsKey(key)

    override fun get(key: K): V? {
        val node = table[key] ?: return null
        if (!node.referenced) node.referenced = true
        return node.value
    }

    override fun put(key: K, value: V): V? = writeLock.withLock {
        table[key]?.let { return@withLock replaceValue(it, value) }
        val node = Node(key, value)
        if (table.size >= maxCapacity) evict()
        table[key] = node
        val current = hand
        if (current == null) {
            node.previous = node
            node.next = node
            hand = node
        } else {
            // The vacancy just passed by the clock hand becomes the newest slot in the ring.
            val tail = checkNotNull(current.previous)
            node.previous = tail
            node.next = current
            tail.next = node
            current.previous = node
        }
        null
    }

    override fun remove(key: K): V? = writeLock.withLock {
        val node = table.remove(key) ?: return@withLock null
        unlink(node)
        node.value
    }

    override fun clear() = writeLock.withLock {
        // Detach nodes so an outstanding iterator entry cannot retain the rest of an old ring.
        table.values.forEach { node ->
            node.previous = null
            node.next = null
        }
        hand = null
        table.clear()
    }

    private fun replaceValue(node: Node<K, V>, value: V): V {
        val previous = node.value
        node.value = value
        node.referenced = true
        return previous
    }

    private fun evict() {
        var victim = checkNotNull(hand)
        var remaining = table.size
        while (remaining > 0 && victim.referenced) {
            victim.referenced = false
            victim = checkNotNull(victim.next)
            remaining--
        }
        hand = victim.next
        table.remove(victim.key)
        unlink(victim)
    }

    private fun unlink(node: Node<K, V>) {
        val next = checkNotNull(node.next)
        val previous = checkNotNull(node.previous)
        if (next === node) {
            hand = null
        } else {
            previous.next = next
            next.previous = previous
            if (hand === node) hand = next
        }
        node.previous = null
        node.next = null
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> =
        object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
            override val size: Int
                get() = this@ConcurrentClockCache.size

            override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
                throw UnsupportedOperationException("Use put to add cache entries")

            override fun clear() = this@ConcurrentClockCache.clear()

            override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean =
                table[element.key]?.value == element.value

            override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean = writeLock.withLock {
                val node = table[element.key] ?: return@withLock false
                if (node.value != element.value) return@withLock false
                table.remove(node.key)
                unlink(node)
                true
            }

            override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
                val iterator = table.values.iterator()
                return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                    private var current: Node<K, V>? = null

                    override fun hasNext(): Boolean = iterator.hasNext()

                    override fun next(): MutableMap.MutableEntry<K, V> {
                        val node = iterator.next()
                        current = node
                        return object : MutableMap.MutableEntry<K, V> {
                            override val key: K get() = node.key
                            override val value: V get() = node.value

                            override fun setValue(newValue: V): V = writeLock.withLock {
                                check(table[node.key] === node) { "Cache entry has been removed" }
                                replaceValue(node, newValue)
                            }

                            override fun equals(other: Any?): Boolean =
                                other is Map.Entry<*, *> && key == other.key && value == other.value

                            override fun hashCode(): Int = key.hashCode() xor value.hashCode()

                            override fun toString(): String = "$key=$value"
                        }
                    }

                    override fun remove() = writeLock.withLock {
                        val node = checkNotNull(current) { "Call next before remove" }
                        if (table.remove(node.key, node)) unlink(node)
                        current = null
                    }
                }
            }
        }

    private class Node<K, V>(val key: K, @Volatile var value: V) {
        @Volatile var referenced = true
        var previous: Node<K, V>? = null
        var next: Node<K, V>? = null
    }
}
