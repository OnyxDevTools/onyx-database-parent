package com.onyx.interactors.cache.data

import com.onyx.interactors.record.data.Reference
import java.util.Collections
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Predicate

/** Hash-based membership under the cache's mutation lock, with ordered snapshots for readers. */
internal class CachedReferenceSet(
    references: Collection<Reference>,
    private val lock: Any,
) : AbstractMutableSet<Reference>() {
    private val values = LinkedHashSet(references)

    override val size: Int
        get() = synchronized(lock) { values.size }

    override fun contains(element: Reference): Boolean = synchronized(lock) { values.contains(element) }

    override fun add(element: Reference): Boolean = synchronized(lock) { values.add(element) }

    override fun remove(element: Reference): Boolean = synchronized(lock) { values.remove(element) }

    override fun clear() = synchronized(lock) { values.clear() }

    override fun addAll(elements: Collection<Reference>): Boolean {
        // Capture another collection before taking our lock to avoid nesting cache locks.
        val additions = ArrayList(elements)
        return synchronized(lock) { values.addAll(additions) }
    }

    override fun removeAll(elements: Collection<Reference>): Boolean {
        val removals = elements.toHashSet()
        return synchronized(lock) { values.removeAll(removals) }
    }

    override fun retainAll(elements: Collection<Reference>): Boolean {
        val retained = elements.toHashSet()
        return synchronized(lock) { values.retainAll(retained) }
    }

    override fun removeIf(filter: Predicate<in Reference>): Boolean =
        synchronized(lock) { values.removeIf(filter) }

    private fun snapshot(): List<Reference> = synchronized(lock) { ArrayList(values) }

    // As with CopyOnWriteArraySet, an iterator never mutates the live set.
    override fun iterator(): MutableIterator<Reference> = Collections.unmodifiableList(snapshot()).iterator()

    override fun spliterator(): Spliterator<Reference> = Spliterators.spliterator(
        snapshot(),
        Spliterator.DISTINCT or Spliterator.ORDERED or Spliterator.IMMUTABLE,
    )
}
