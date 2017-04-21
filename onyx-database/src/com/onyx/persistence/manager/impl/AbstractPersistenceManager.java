package com.onyx.persistence.manager.impl;

import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.persistence.query.QueryResult;
import com.onyx.query.QueryListener;
import com.onyx.util.ReflectionUtil;

import java.util.Arrays;
import java.util.List;

/**
 * This class only exist because Android does not support default metods within interfaces yet
 */
abstract class AbstractPersistenceManager implements PersistenceManager {
    /**
     * Execute a lazy query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws EntityException General exception happened when the query.
     */
    @SuppressWarnings("unused")
    public QueryResult executeLazyQueryForResult(Query query) throws EntityException
    {
        return new QueryResult(query, executeLazyQuery(query));
    }

    /**
     * Get relationship for an entity
     *
     * @param entity The entity to load
     * @param attribute Attribute that represents the relationship
     * @return The relationship Value
     * @throws EntityException Error when hydrating relationship.  The attribute must exist and must be a annotated with a relationship
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    public Object getRelationship(IManagedEntity entity, String attribute) throws EntityException
    {
        initialize(entity, attribute);
        return ReflectionUtil.getAny(entity, getContext().getDescriptorForEntity(entity).getRelationships().get(attribute).getField());
    }

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results.
     *
     * @since 1.0.0
     *
     * @param clazz Managed Entity type
     *
     * @param criteria Query Criteria to filter results
     *
     * @return Unsorted List of results matching criteria
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @SuppressWarnings("unused")
    public <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria) throws EntityException
    {
        return list(clazz, criteria, new QueryOrder[0]);
    }

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results.
     *
     * @since 1.0.0
     *
     * @param clazz Managed Entity type
     *
     * @param criteria Query Criteria to filter results
     *
     * @param orderBy Array of sort objects
     *
     * @return Sorted List of results matching criteria
     *
     * @throws EntityException Exception occurred while filtering results
     */
    public <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy) throws EntityException
    {
        return list(clazz, criteria, 0, -1, orderBy);
    }

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results.
     *
     * @since 1.0.0
     *
     * @param clazz Managed Entity type
     *
     * @param criteria Query Criteria to filter results
     *
     * @param orderBy A single sort specification
     *
     * @return Sorted List of results matching criteria
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @SuppressWarnings("unused")
    public <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, QueryOrder orderBy) throws EntityException
    {
        QueryOrder[] queryOrders = {orderBy};
        return list(clazz, criteria, queryOrders);
    }

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results within a partition.
     *
     * @since 1.0.0
     *
     * @param clazz Managed Entity type
     *
     * @param criteria Query Criteria to filter results
     *
     * @param partitionId Partition key for entities
     *
     * @return Unsorted List of results matching criteria within a partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @SuppressWarnings("unused")
    public <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, Object partitionId) throws EntityException
    {
        return list(clazz, criteria, new QueryOrder[0], partitionId);
    }

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results within a partition.
     *
     * @since 1.0.0
     *
     * @param clazz Managed Entity type
     *
     * @param criteria Query Criteria to filter results
     *
     * @param orderBy Array of sort order specifications
     *
     * @param partitionId Partition key for entities
     *
     * @return Sorted List of results matching criteria within a partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @SuppressWarnings("unused")
    public <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy, Object partitionId) throws EntityException
    {
        return list(clazz, criteria, 0, -1, orderBy, partitionId);
    }

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results within a partition.
     *
     * @since 1.0.0
     *
     * @param clazz Managed Entity type
     *
     * @param criteria Query Criteria to filter results
     *
     * @param orderBy A single order specification
     *
     * @param partitionId Partition key for entities
     *
     * @return Sorted List of results matching criteria within a partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @SuppressWarnings("unused")
    public <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, QueryOrder orderBy, Object partitionId) throws EntityException
    {
        QueryOrder[] queryOrders = {orderBy};
        return list(clazz, criteria, queryOrders, partitionId);
    }

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results.
     *
     * This allows for a specified range of results.
     *
     * @since 1.0.0
     *
     * @param clazz Managed Entity type
     *
     * @param criteria Query Criteria to filter results
     *
     * @param start Start of record results.
     *
     * @param maxResults Max number of results returned
     *
     * @param orderBy An array of sort order specification
     *
     * @return Sorted List of results matching criteria within range
     *
     * @throws EntityException Exception occurred while filtering results
     */
    public <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy) throws EntityException
    {
        final Query tmpQuery = new Query(clazz, criteria);
        tmpQuery.setMaxResults(maxResults);
        tmpQuery.setFirstRow(start);
        if (orderBy != null)
        {
            tmpQuery.setQueryOrders(Arrays.asList(orderBy));
        }
        return executeQuery(tmpQuery);
    }

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results within a partition.
     *
     * This allows for a specified range of results.
     *
     * @since 1.0.0
     *
     * @param clazz Managed Entity type
     *
     * @param criteria Query Criteria to filter results
     *
     * @param start Start of record results.
     *
     * @param maxResults Max number of results returned
     *
     * @param orderBy An array of sort order specification
     *
     * @param partitionId Partition key to filter results
     *
     * @return Sorted List of results matching criteria within range and partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @SuppressWarnings("unused")
    public <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy, Object partitionId) throws EntityException
    {

        final Query tmpQuery = new Query(clazz, criteria);
        tmpQuery.setPartition(partitionId);
        tmpQuery.setMaxResults(maxResults);
        tmpQuery.setFirstRow(start);
        if (orderBy != null)
        {
            tmpQuery.setQueryOrders(Arrays.asList(orderBy));
        }

        return executeQuery(tmpQuery);
    }

    /**
     * Execute a delete query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws EntityException Exception when deleting entities
     */
    @SuppressWarnings("unused")
    public QueryResult executeDeleteForResult(Query query) throws EntityException
    {
        return new QueryResult(query, executeDelete(query));
    }

    /**
     * Execute an update query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws EntityException when an update query failed
     */
    @SuppressWarnings("unused")
    public QueryResult executeUpdateForResult(Query query) throws EntityException
    {
        return new QueryResult(query, executeUpdate(query));
    }

    /**
     * Execute a query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws EntityException when the query is mal formed or general exception
     */
    @SuppressWarnings("unused")
    public QueryResult executeQueryForResult(Query query) throws EntityException
    {
        return new QueryResult(query, executeQuery(query));
    }

    /**
     * Listen to a query and register its subscriber
     *
     * @param query Query without query listener
     * @param queryListener listener to invoke for changes
     * @since 1.3.1
     */
    @Override
    @SuppressWarnings("unused")
    public void listen(Query query, QueryListener queryListener) throws EntityException {
        query.setChangeListener(queryListener);
        listen(query);
    }
}
