package com.onyx.extension

import com.onyx.extension.common.castTo
import com.onyx.extension.common.compare
import com.onyx.persistence.query.QueryCriteriaOperator

@Suppress("UNCHECKED_CAST")
fun <T> Iterable<T>.sum():T {
    var doubleSum = 0.0
    var classType:Class<*>? = null
    this.forEach { item ->
        if(classType == null && item != null) {
            val iduno:Any = item
            classType = iduno::class.java
        }
        val toAdd:Double = (item as Number?)?.toDouble() ?: 0.0
        doubleSum += toAdd
    }
    return doubleSum.castTo(classType!!) as T
}

@Suppress("UNCHECKED_CAST")
fun <T> Iterable<T>.avg():T {
    val sum:Any = sum() as Any
    val classType = sum::class.java
    return ((sum as Number).toDouble() / this.count().toDouble()).castTo(classType) as T
}

fun <T> Iterable<T>.min():T? = this.minWith(kotlin.Comparator { o1, o2 ->
        when {
            o1.compare(o2, QueryCriteriaOperator.LESS_THAN) -> 1
            o1.compare(o2, QueryCriteriaOperator.GREATER_THAN) -> -1
            else -> 0
        }
    })

fun <T> Iterable<T>.max():T? = this.minWith(kotlin.Comparator { o1, o2 ->
    when {
        o2.compare(o1, QueryCriteriaOperator.LESS_THAN) -> 1
        o2.compare(o1, QueryCriteriaOperator.GREATER_THAN) -> -1
        else -> 0
    }
})