package com.onyx.interactors.query

import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity

/**
 * Collects and aggregates the results of a query
 */
interface QueryCollector<T> {

    var results: MutableCollection<T>
    val references:MutableList<Reference>

    /**
     * Invoked upon each items' result.  This method is responsible for aggregating the item.
     *
     * @since 2.1.3
     */
    fun collect(reference: Reference, entity: IManagedEntity?)

    /**
     * Format the collected results and do some last preparation for data ingestion.
     *
     * @since 2.1.3
     */
    fun finalizeResults()

    /**
     * Set the references for a query.  This will collect for each of the references
     *
     * @since 2.1.3
     */
    fun setReferenceSet(value:MutableSet<Reference>)

    /**
     * Limit the number of references based on query definitions.
     *
     * @since 2.1.3
     */
    fun getLimitedReferences():MutableList<Reference>

    /**
     * Get the total number of items matching the results
     */
    fun getNumberOfResults():Int

    /**
     * Indicator of whether the results should be cached or not
     */
    fun shouldCacheResults():Boolean

}