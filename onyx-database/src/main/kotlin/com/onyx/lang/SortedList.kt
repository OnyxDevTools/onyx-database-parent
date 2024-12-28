package com.onyx.lang

import kotlin.math.abs

/**
 * Array List that accepts a comparator.  Why this is not part of the JVM who knows...
 */
open class SortedList<T> () : ArrayList<T>() {

    private lateinit var comparator:Comparator<T>

    constructor(comparator: Comparator<T>) : this() {
        this.comparator = comparator
    }

    override fun add(element: T): Boolean {
        var insertionIndex = abs(this.binarySearch { comparator.compare(it, element) }) -1
        if(insertionIndex < 0) { insertionIndex = 0 }
        super.add(insertionIndex, element)
        return true
    }
}
