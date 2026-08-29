package com.onyx.persistence.query

/**
 * Defines a full-text query with an optional minimum match score.
 */
data class FullTextQuery(
    val queryText: String,
    val minScore: Float? = null
)

internal fun resolveFullTextQuery(value: Any?): FullTextQuery? = when (value) {
    is FullTextQuery -> value
    is String -> FullTextQuery(value)
    is Map<*, *> -> resolveVectorSearchQuery(value)?.let { search ->
        search.text?.let { text -> FullTextQuery(text, search.minScore) }
    }
    null -> null
    else -> FullTextQuery(value.toString())
}
