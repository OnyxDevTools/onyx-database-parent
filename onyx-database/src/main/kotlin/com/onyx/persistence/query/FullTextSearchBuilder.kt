package com.onyx.persistence.query

import com.onyx.persistence.manager.PersistenceManager

class FullTextSearchBuilder(
    private val manager: PersistenceManager,
    private val queryText: String,
    private val minScore: Float?,
    private val options: SearchOptions? = null,
) {
    constructor(
        manager: PersistenceManager,
        queryText: String,
        options: SearchOptions,
    ) : this(manager, queryText, null, options)

    private var limit: Int = 100

    fun limit(limit: Int): FullTextSearchBuilder {
        this.limit = limit
        return this
    }

    fun list(): List<FullTextSearchResult> = options?.let {
        manager.searchAllTables(queryText, it, limit)
    } ?: manager.searchAllTables(queryText, limit, minScore)
}
