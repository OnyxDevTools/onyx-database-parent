package com.onyx.persistence.query

import java.util.*

enum class QueryFunctionType(val isGroupFunction:Boolean) {
    SUM(true),
    MIN(true),
    MAX(true),
    AVG(true),
    COUNT(true),
    UPPER(false),
    LOWER(false),
    REPLACE(false),
    SUBSTRING(false)
    ;

    companion object {
        fun value(stringValue: String):QueryFunctionType = when(stringValue.lowercase(Locale.getDefault())) {
            "sum" -> SUM
            "min" -> MIN
            "max" -> MAX
            "avg" -> AVG
            "count" -> COUNT
            "upper" -> UPPER
            "lower" -> LOWER
            "replace" -> REPLACE
            "substring" -> SUBSTRING
            else -> { throw Exception("Query function not found") }
        }
    }
}