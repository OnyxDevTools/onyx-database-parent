package com.onyx.interactors.query

import com.onyx.exception.OnyxException
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.query.Query

interface QueryInteractor {

    /**
     * Find object ids that match the criteria
     *
     * @param query Query Criteria
     * @return References matching query criteria
     * @throws com.onyx.exception.OnyxException General query exception
     * @since 1.3.0 This has been refactored to remove the logic for meeting criteria.  That has
     * been moved to CompareUtil
     */
    fun <T> getReferencesForQuery(query: Query): QueryCollector<T>

    /**
     * Delete record with reference ids
     *
     * @param records References to delete
     * @param query   Query object
     * @return Number of entities deleted
     */
    fun deleteRecordsWithReferences(records: List<Reference>, query: Query): Int

    /**
     * Update records
     *
     * @param query   Query information containing update values
     * @param records Entity references as a result of the query
     * @return how many entities were updated
     */
    fun updateRecordsWithReferences(query: Query, records: List<Reference>): Int

    /**
     * Get the count for a query.  This is used to get the count without actually executing the query.  It is lighter weight
     * than the entire query and in most cases will use the longSize on the disk map data structure if it is
     * for the entire table.
     *
     * @param query Query to identify count for
     * @return The number of records matching query criterion
     * @throws OnyxException Exception occurred while executing query
     * @since 1.3.0 Added as enhancement #71
     */
    @Throws(OnyxException::class)
    fun getCountForQuery(query: Query): Long

}