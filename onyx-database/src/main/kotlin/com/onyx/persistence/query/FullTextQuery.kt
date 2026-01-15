package com.onyx.persistence.query

/**
 * Defines a Lucene full-text query with an optional minimum match score.
 */
data class FullTextQuery(
    val queryText: String,
    val minScore: Float? = null
)

internal fun resolveFullTextQuery(value: Any?): FullTextQuery? = when (value) {
    is FullTextQuery -> value
    is String -> FullTextQuery(value)
    null -> null
    else -> FullTextQuery(value.toString())
}
