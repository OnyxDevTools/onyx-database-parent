package com.onyx.interactors.cache

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.IManagedEntity
import com.onyx.interactors.cache.data.CachedResults
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryListener
import com.onyx.persistence.query.QueryListenerEvent

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This controller handles how a query is cached.
 */
interface QueryCacheInteractor {

    /**
     * Cache query results from the closure.  If the query has already been cached, return the results
     * of the cache.
     *
     * @param query Query results to cache
     * @param body Closure to execute to retrieve the results of the query
     *
     * @since 2.0.0
     */
    fun <T : Map<Any, Any?>> cache(query: Query, body: () -> T): T

    /**
     * Get Cached results for a query. This method will return a cached query result if it exist.
     *
     * @param query Query to check for results.
     *
     * @return The cached results or null
     * @since 1.3.0
     */
    fun getCachedQueryResults(query: Query): CachedResults?

    /**
     * Set cached query results.  This is typically done on executeQuery and executeLazyQuery.
     *
     * @param query Corresponding query for results
     *
     * @param results Result as references
     */
    fun setCachedQueryResults(query: Query, results: Map<Any, Any?>): CachedResults?

    /**
     * Update all of the cached results if an entity has been modified.  It will re-check the criteria and
     * update the applied cached query results
     *
     * @param entity Entity that was potentially inserted, updated, or deleted.
     * @param descriptor The entity's descriptor
     * @param entityReference The entity's reference
     * @param type Wheter or not to remove it from the cache.  In this case, it would be if an entity was deleted.
     *
     * @since 1.3.0
     */
    fun updateCachedQueryResultsForEntity(entity: IManagedEntity, descriptor: EntityDescriptor, entityReference: Long, type: QueryListenerEvent)

    /**
     * Subscribe a query listener with associated cached results
     *
     * @param results Results to listen to
     * @param queryListener listner to respond to cache changes
     *
     * @since 1.3.0
     */
    fun subscribe(results: CachedResults, queryListener: QueryListener<*>)

    /**
     * This method is used to subscribe irrespective of a query being ran.
     * @param query Query object with defined listener
     *
     * @since 1.3.1
     */
    fun subscribe(query: Query)

    /**
     * Un-subscribe query
     * @param query Query to un-subscribe from
     * @return Whether the listener was listening to begin with
     *
     * @since 1.3.0
     */
    fun unSubscribe(query: Query): Boolean
}
