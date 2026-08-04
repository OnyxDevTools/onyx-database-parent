package com.onyx.persistence.query

import java.util.*

enum class QueryFunctionType(
    val isGroupFunction: Boolean,
    val coerceNumericStrings: Boolean = false,
) {
    SUM(true, true),
    MIN(true, true),
    STD(true, true),
    MEDIAN(true, true),
    MAX(true, true),
    AVG(true, true),
    VARIANCE(true, true),
    COUNT(true),
    UPPER(false),
    LOWER(false),
    REPLACE(false),
    SUBSTRING(false),
    PERCENTILE(true, true),
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
