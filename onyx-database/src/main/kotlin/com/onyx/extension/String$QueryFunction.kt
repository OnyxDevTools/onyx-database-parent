package com.onyx.extension

import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.function.QueryFunctionFactory
import com.onyx.persistence.query.QueryFunctionType

/**
 * Get a query function within a selection string.  This will parse the function type and its parameters.
 *
 * @since 2.1.0
 */
fun String.getFunctionWithinSelection(): QueryFunction? {
    val match = "(\\w+)\\((.+)\\)".toRegex().find(this)

    return if(match != null) {
        val function = match.groupValues[1]
        var attribute = match.groupValues[2]

        val tokens = attribute.split(",").map { it.trim().replace("'", "").replace("\"", "") }
        if(tokens.size > 1) {
            attribute = tokens[0]
            val param1 = tokens[1]
            val param2 = if(tokens.size > 2) tokens[2] else null
            QueryFunctionFactory.create(QueryFunctionType.value(function), attribute, param1, param2)
        } else {
            QueryFunctionFactory.create(QueryFunctionType.value(function), attribute)
        }
    } else {
        null
    }
}

/**
 * This will parse the attribute within the selection string.  In the event there is a function, it will parse it
 * to get the original attribute
 *
 * @since 2.1.0
 */
fun String.getAttributeWithinSelection():String {
    val match = "(\\w+)\\((.+)\\)".toRegex().find(this)

    return if(match != null) {
        var attribute = match.groupValues[2]

        val tokens = attribute.split(",").map { it.trim().replace("'", "").replace("\"", "") }
        if(tokens.size > 1) {
            attribute = tokens[0]
        }

        attribute
    } else {
        this
    }
}