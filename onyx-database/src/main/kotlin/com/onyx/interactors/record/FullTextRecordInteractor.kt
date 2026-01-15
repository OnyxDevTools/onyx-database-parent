package com.onyx.interactors.record

/**
 * Interface for record interactors that can execute full-text searches.
 */
interface FullTextRecordInteractor {

    /**
     * Execute a full-text search and return record reference IDs with scores.
     *
     * @param queryText Lucene query string.
     * @param limit Maximum number of results.
     * @return Map of record reference IDs to scores.
     */
    fun searchAll(queryText: String, limit: Int): Map<Long, Float>
}
