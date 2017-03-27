package com.onyx.persistence.query;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.query.CachedResults;

import java.util.Map;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This controller handles how a query is cached.
 */
public interface QueryCacheController {

    /**
     * Get Cached results for a query. This method will return a cached query result if it exist.
     *
     * @param query Query to check for results.
     *
     * @return The cached results or null
     * @since 1.3.0
     */
    CachedResults getCachedQueryResults(Query query);

    /**
     * Set cached query results.  This is typically done on executeQuery and executeLazyQuery.
     *
     * @param query Corresponding query for results
     *
     * @param results Result as references
     */
    void setCachedQueryResults(Query query, Map results);

    /**
     * Update all of the cached results if an entity has been modified.  It will re-check the criteria and
     * update the applied cached query results
     *
     * @param entity Entity that was potentially inserted, updated, or deleted.
     * @param descriptor The entity's descriptor
     * @param entityReference The entitity's reference
     * @param remove Wheter or not to remove it from the cache.  In this case, it would be if an entity was deleted.
     *
     * @since 1.3.0
     */
    void updateCachedQueryResultsForEntity(IManagedEntity entity, EntityDescriptor descriptor, final long entityReference, boolean remove);

}
