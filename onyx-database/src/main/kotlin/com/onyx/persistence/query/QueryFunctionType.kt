package com.onyx.persistence.query

import java.util.*

enum class QueryFunctionType(val isGroupFunction:Boolean) {
    SUM(true),
    MIN(true),
    STD(true),
    MEDIAN(true),
    MAX(true),
    AVG(true),
    VARIANCE(true),
    COUNT(true),
    UPPER(false),
    LOWER(false),
    REPLACE(false),
    SUBSTRING(false),
    PERCENTILE(true),
    FORMAT(false),
    ;

    companion object {
        fun value(stringValue: String):QueryFunctionType = when(stringValue.lowercase(Locale.getDefault())) {
            "sum" -> SUM
            "min" -> MIN
            "max" -> MAX
            "avg" -> AVG
            "std" -> STD
            "variance" -> VARIANCE
            "median" -> MEDIAN
            "count" -> COUNT
            "upper" -> UPPER
            "lower" -> LOWER
            "replace" -> REPLACE
            "substring" -> SUBSTRING
            "percentile" -> PERCENTILE
            "format" -> FORMAT
            else -> { throw Exception("Query function not found") }
        }
    }
}
