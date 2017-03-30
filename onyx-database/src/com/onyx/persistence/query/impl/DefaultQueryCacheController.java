package com.onyx.persistence.query.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.fetch.PartitionReference;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCacheController;
import com.onyx.query.CachedResults;
import com.onyx.util.CompareUtil;
import com.onyx.util.map.CompatHashMap;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.LastRecentlyUsedMap;
import com.onyx.util.map.SynchronizedMap;

import java.util.Map;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This controller handles how a query is cached.  In this case, the policy is if the entity is part of one of the 100
 * most recently used queries.
 *
 * @since 1.3.0 Introduced
 */
public class DefaultQueryCacheController implements QueryCacheController {

    private final SchemaContext context;

    /**
     * Constructor with context
     * @param context Schema context used to reference record controllers, descriptors, and partition information
     */
    public DefaultQueryCacheController(SchemaContext context)
    {
        this.context = context;
    }

    private final CompatMap<Class, CompatMap<Query, CachedResults>> cachedQueriesByClass = new SynchronizedMap<>(new CompatHashMap<>());

    /**
     * Get Cached results for a query. This method will return a cached query result if it exist.
     *
     * @param query Query to check for results.
     *
     * @return The cached results or null
     * @since 1.3.0
     */
    public CachedResults getCachedQueryResults(Query query)
    {
        return cachedQueriesByClass.compute(query.getEntityType(), (aClass, queryCachedResultsMap) -> {
            if(queryCachedResultsMap == null)
                queryCachedResultsMap = new LastRecentlyUsedMap<>(100, 5*60);

            return queryCachedResultsMap;
        }).get(query);
    }

    /**
     * Set cached query results.  This is typically done on executeQuery and executeLazyQuery.
     *
     * @param query Corresponding query for results
     *
     * @param results Result as references
     */
    public void setCachedQueryResults(Query query, Map results)
    {
        cachedQueriesByClass.computeIfPresent(query.getEntityType(), (aClass, queryCachedResultsMap) -> {
            queryCachedResultsMap.put(query, new CachedResults(results));
            return queryCachedResultsMap;
        });
    }

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
    @SuppressWarnings("unchecked")
    public void updateCachedQueryResultsForEntity(IManagedEntity entity, EntityDescriptor descriptor, final long entityReference, boolean remove)
    {
        cachedQueriesByClass.computeIfPresent(entity.getClass(), (aClass, queryCachedResultsMap) -> {

            Object reference = entityReference;

            // This snippet here will resolve the partition reference based on the descriptor and
            // The entity passed in
            try {
                if (descriptor.getPartition() != null
                        && descriptor.getPartition().getPartitionValue() != null
                        && descriptor.getPartition().getPartitionValue().length() > 0) {
                    final SystemPartitionEntry systemPartitionEntry = context.getPartitionWithValue(aClass, descriptor.getPartition().getPartitionValue());
                    reference = new PartitionReference(systemPartitionEntry.getIndex(), entityReference);
                }
            } catch (EntityException ignore) {}

            final Object useThisReference = reference;

            queryCachedResultsMap.forEach((query, cachedResults) -> {
                try {

                    // If indicated to remove the record, delete it and move on
                    if(remove)
                    {
                        synchronized(cachedResults.getReferences()) {
                            cachedResults.getReferences().remove(useThisReference);
                        }
                    }
                    else if (CompareUtil.meetsCriteria(query.getAllCriteria(), query.getCriteria(), entity, useThisReference, context, descriptor)) {
                        synchronized (cachedResults.getReferences()) {
                            if(query.getSelections() != null && query.getSelections().size() > 0) {
                                cachedResults.getReferences().put(useThisReference, useThisReference);
                            }
                            else
                            {
                                cachedResults.getReferences().put(useThisReference, entity);
                            }
                        }
                    }
                } catch (EntityException ignore) {}
            });

            return queryCachedResultsMap;
        });
    }
}
