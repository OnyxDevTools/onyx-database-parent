package com.onyx.persistence.query

import com.onyx.persistence.manager.PersistenceManager

class FullTextSearchBuilder(
    private val manager: PersistenceManager,
    private val queryText: String,
    private val minScore: Float?
) {
    private var limit: Int = 100

    fun limit(limit: Int): FullTextSearchBuilder {
        this.limit = limit
        return this
    }

    fun list(): List<FullTextSearchResult> = manager.searchAllTables(queryText, limit, minScore)
}
