package com.onyx.lang

/**
 * Array List that accepts a comparator.  Why this is not part of the JVM who knows...
 */
class SortedHashSet<T>(private val comparator:Comparator<T>) : SortedList<T>(comparator) {

    override fun add(element: T): Boolean {
        var index = this.binarySearch { comparator.compare(it, element) }
        if(index >= 0) {
            return false
        }
        index = -(index + 1)
        super.add(index, element)
        return true
    }
}
