package com.onyx.persistence.query.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.fetch.PartitionReference;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.CachedQueryMap;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCacheController;
import com.onyx.query.CachedResults;
import com.onyx.query.QueryListener;
import com.onyx.query.QueryListenerEvent;
import com.onyx.util.CompareUtil;
import com.onyx.util.map.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    // @since 1.3.0 - Changed to CachedQueryMap so we can retain strong references for query subscriptions
    private final CompatMap<Class, CachedQueryMap<Query, CachedResults>> cachedQueriesByClass = new SynchronizedMap<>(new CompatHashMap<>());

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
                // Only allow 100 cached queries per entity with a 5 minute LRU expiration
                // At some point make this configurable.  The magic number is 100 because that
                // will limit record insert performance degredation.
                queryCachedResultsMap = new CachedQueryMap<>(100, 5*60);

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
    public CachedResults setCachedQueryResults(Query query, Map results)
    {
        final AtomicReference<CachedResults> cachedResultsReference = new AtomicReference<>();
        cachedQueriesByClass.computeIfPresent(query.getEntityType(), (aClass, queryCachedResultsMap) -> {
            CachedResults cachedResults = new CachedResults(results);
            cachedResultsReference.set(cachedResults);
            // Set a strong reference if this is a query listener.  In that
            // case we do not want it to get cleaned up.
            if(query.getChangeListener() != null)
                queryCachedResultsMap.putStrongReference(query, cachedResults);
            else
                queryCachedResultsMap.put(query, cachedResults);
            return queryCachedResultsMap;
        });
        return cachedResultsReference.get();
    }

    /**
     * Update all of the cached results if an entity has been modified.  It will re-check the criteria and
     * update the applied cached query results
     *
     * @param entity Entity that was potentially inserted, updated, or deleted.
     * @param descriptor The entity's descriptor
     * @param entityReference The entitity's reference
     * @param type Wheter or not to remove it from the cache.  In this case, it would be if an entity was deleted.
     *
     * @since 1.3.0
     */
    @SuppressWarnings("unchecked")
    public void updateCachedQueryResultsForEntity(IManagedEntity entity, EntityDescriptor descriptor, final long entityReference, QueryListenerEvent type)
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
                    if(type != QueryListenerEvent.INSERT && type != QueryListenerEvent.UPDATE)
                    {
                        synchronized(cachedResults.getReferences()) {
                            cachedResults.remove(useThisReference, entity, type, CompareUtil.meetsCriteria(query.getAllCriteria(), query.getCriteria(), entity, useThisReference, context, descriptor));
                        }
                    }
                    else if (CompareUtil.meetsCriteria(query.getAllCriteria(), query.getCriteria(), entity, useThisReference, context, descriptor)) {
                        synchronized (cachedResults.getReferences()) {
                            if(query.getSelections() != null && query.getSelections().size() > 0) {
                                cachedResults.put(useThisReference, useThisReference, type);
                            }
                            else
                            {
                                cachedResults.put(useThisReference, entity, type);
                            }
                        }
                    }
                } catch (EntityException ignore) {}
            });

            return queryCachedResultsMap;
        });
    }

    /**
     * Subscribe a query listener with associated cached results.
     * The subscription will ocur during executeQuery and executeLazyQuery.
     * Currently you cannot subscribe to delete or update queries.
     *
     * @param results Results to listen to
     * @param queryListener listner to respond to cache changes
     *
     * @since 1.3.0
     */
    public void subscribe(CachedResults results, QueryListener queryListener)
    {
        results.subscribe(queryListener);
    }

    /**
     * Unsubscribe query.  This must be done manually.  The unsubscribe will not
     * be done auto-magically.  This is highlighted as a danger of using this
     * functionality.
     *
     * @param query Query to unsubscribe from
     * @return Whether the listener was listening to begin with
     *
     * @since 1.3.0
     */
    public boolean unsubscribe(Query query) {
        final CachedResults cachedResults = getCachedQueryResults(query);
        return cachedResults != null && cachedResults.unsubscribe(query.getChangeListener());
    }
}
