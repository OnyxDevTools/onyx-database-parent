package com.onyx.interactors.query.impl.collectors

import java.util.PriorityQueue

/**
 * Retains only the best page candidates, or appends unbounded results for one final sort.
 * The collector serializes additions; iteration is ordered only after [sortedResults].
 */
internal class OrderedQueryResults<T>(
    private val comparator: Comparator<T>,
    private val retainedRows: Long?,
) : AbstractMutableCollection<T>() {
    private class Entry<T>(val value: T, val sequence: Long)

    private val entryComparator = Comparator<Entry<T>> { first, second ->
        val comparison = comparator.compare(first.value, second.value)
        if (comparison != 0) comparison else first.sequence.compareTo(second.sequence)
    }
    private val heap = retainedRows?.let {
        // Do not allocate the requested page size up front: limits can be Int.MAX_VALUE.
        PriorityQueue(11, entryComparator.reversed())
    }
    private val appended = if (heap == null) ArrayList<T>() else null
    private var sequence = 0L

    override val size: Int
        get() = heap?.size ?: appended!!.size

    override fun add(element: T): Boolean {
        val candidates = heap ?: return appended!!.add(element)
        if (candidates.size.toLong() >= retainedRows!!) {
            // Equal keys keep their arrival order, including at the page boundary.
            if (comparator.compare(element, candidates.peek().value) >= 0) return false
            candidates.poll()
        }
        candidates.add(Entry(element, sequence++))
        return true
    }

    override fun iterator(): MutableIterator<T> {
        val iterator = heap?.iterator() ?: return appended!!.iterator()
        return object : MutableIterator<T> {
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): T = iterator.next().value
            override fun remove() = iterator.remove()
        }
    }

    fun sortedResults(): MutableList<T> {
        val candidates = heap ?: return appended!!.apply { sortWith(comparator) }
        return candidates.sortedWith(entryComparator).mapTo(ArrayList(candidates.size)) { it.value }
    }
}
