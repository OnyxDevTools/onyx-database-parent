package com.onyx.persistence.query

import com.onyx.persistence.IManagedEntity

/**
 * Result returned from a full-text search across one or more tables.
 */
data class FullTextSearchResult(
    val id: Any?,
    val entityType: Class<*>,
    val entity: IManagedEntity
)
