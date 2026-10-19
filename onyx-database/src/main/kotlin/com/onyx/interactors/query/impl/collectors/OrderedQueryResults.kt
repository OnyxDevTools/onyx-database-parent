package com.onyx.interactors.query.impl.collectors

import java.util.PriorityQueue

/**
 * Retains only the best page candidates, or appends unbounded results for one final sort.
 * The collector serializes additions; iteration is ordered only after [sortedResults].
 */
internal class OrderedQueryResults<T>(
    private val comparator: Comparator<Array<Any?>>,
    private val retainedRows: Long?,
    private val prepareKeys: ((T) -> Array<Any?>)? = null,
) : AbstractMutableCollection<T>() {
    private class Entry<T>(val value: T, val keys: Array<Any?>, val sequence: Long)

    private val entryComparator = Comparator<Entry<T>> { first, second ->
        val comparison = comparator.compare(first.keys, second.keys)
        if (comparison != 0) comparison else first.sequence.compareTo(second.sequence)
    }
    private val heap = retainedRows?.let {
        // Do not allocate the requested page size up front: limits can be Int.MAX_VALUE.
        PriorityQueue(11, entryComparator.reversed())
    }
    private val appended = if (heap == null) ArrayList<Entry<T>>() else null
    private var sequence = 0L

    override val size: Int
        get() = heap?.size ?: appended!!.size

    override fun add(element: T): Boolean = add(element, checkNotNull(prepareKeys) {
        "Ordered references require sort keys prepared from the collected entity"
    }(element))

    /** [keys] must be resolved before acquiring the collector's result/reference lock. */
    fun add(element: T, keys: Array<Any?>): Boolean {
        val entry = Entry(element, keys, sequence++)
        val candidates = heap ?: return appended!!.add(entry)
        if (candidates.size.toLong() >= retainedRows!!) {
            // Equal keys keep their arrival order, including at the page boundary.
            if (comparator.compare(keys, candidates.peek().keys) >= 0) return false
            candidates.poll()
        }
        candidates.add(entry)
        return true
    }

    override fun iterator(): MutableIterator<T> {
        val iterator = heap?.iterator() ?: appended!!.iterator()
        return object : MutableIterator<T> {
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): T = iterator.next().value
            override fun remove() = iterator.remove()
        }
    }

    fun sortedResults(): MutableList<T> {
        val candidates = heap ?: appended!!
        return candidates.sortedWith(entryComparator).mapTo(ArrayList(candidates.size)) { it.value }
    }
}
