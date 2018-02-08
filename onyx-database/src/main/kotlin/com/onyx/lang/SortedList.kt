package com.onyx.lang

/**
 * Array List that accepts a comparator.  Why this is not part of the JVM who knows...
 */
open class SortedList<T> (private val comparator:Comparator<T>) : ArrayList<T>() {

    override fun add(element: T): Boolean {
        var insertionIndex = Math.abs(this.binarySearch { comparator.compare(it, element) }) -1
        if(insertionIndex < 0) { insertionIndex = 0 }
        super.add(insertionIndex, element)
        return true
    }
}
