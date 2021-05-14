package com.onyx.lang

import kotlin.math.abs


/**
 * Array List that accepts a comparator.  Why this is not part of the JVM who knows...
 */
class SortedHashSet<T>(private val comparator:Comparator<T>) : SortedList<T>(comparator) {

    override fun add(element: T): Boolean {
        if(this.contains(element))
            return false
        var insertionIndex = abs(this.binarySearch { comparator.compare(it, element) }) -1
        if(insertionIndex < 0) { insertionIndex = 0 }
        super.add(insertionIndex, element)
        return true
    }
}
