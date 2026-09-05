package com.onyx.lang

/**
 * Array List that accepts a comparator.  Why this is not part of the JVM who knows...
 */
open class SortedList<T> () : ArrayList<T>() {

    private lateinit var comparator:Comparator<T>

    constructor(comparator: Comparator<T>) : this() {
        this.comparator = comparator
    }

    override fun add(element: T): Boolean {
        // Insert after equal keys so a comparator returning zero preserves arrival order.
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high).ushr(1)
            if (comparator.compare(this[middle], element) <= 0) low = middle + 1 else high = middle
        }
        super.add(low, element)
        return true
    }
}
