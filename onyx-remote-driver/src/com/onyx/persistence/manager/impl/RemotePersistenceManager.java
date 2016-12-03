package com.onyx.persistence.manager.impl;

import com.onyx.exception.StreamException;
import com.onyx.persistence.collections.LazyQueryCollection;
import com.onyx.persistence.factory.ConnectionManager;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory;
import com.onyx.stream.QueryMapStream;
import com.onyx.stream.QueryStream;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.RelationshipNotFoundException;
import com.onyx.helpers.PartitionHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.SocketPersistenceManager;
import com.onyx.persistence.query.*;
import com.onyx.record.AbstractRecordController;
import com.onyx.request.pojo.*;
import com.onyx.util.ReflectionUtil;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistence manager supplies a public API for performing database persistence and querying operations.  This specifically is used for an remote database.
 * Entities that are passed through these methods must be serializable
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   PersistenceManagerFactory factory = new RemotePersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("onx://23.234.25.23:8080")
 *   factory.initialize();
 *
 *   PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 *   factory.close(); //Close the in memory database
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.manager.PersistenceManager
 *
 */
public class RemotePersistenceManager extends AbstractRemotePersistenceManager implements PersistenceManager, SocketPersistenceManager {

    private ConnectionManager connectionManager;

    /**
     * Default Constructor.  This should be invoked by the persistence manager factory
     *
     * @since 1.1.0
     * @param connectionManager Responsible for verifying the connection and re-connecting
     */
    public RemotePersistenceManager(ConnectionManager connectionManager)
    {
        super(null);
        this.connectionManager = connectionManager;
    }


    /**
     * Save entity.  Persists a single entity for update or insert.  This method will cascade relationships and persist indexes.
     *
     * @since 1.0.0
     *
     * @param entity Managed Entity to Save
     *
     * @return Saved Managed Entity
     *
     * @throws EntityException Exception occured while persisting an entity
     */
    @Override
    public IManagedEntity saveEntity(IManagedEntity entity) throws EntityException
    {
        connectionManager.verifyConnection();

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.SAVE, entity);

        IManagedEntity copyValue = (IManagedEntity)this.endpoint.execute(token);
        ReflectionUtil.copy(copyValue, entity, context.getDescriptorForEntity(entity));

        return entity;
    }

    /**
     * Batch saves a list of entities.
     *
     * The entities must all be of the same type
     *
     * @since 1.0.0
     * @param entities List of entities
     * @throws EntityException Exception occurred while saving an entity within the list.  This will not roll back preceding saves if error occurs.
     */
    @Override
    public void saveEntities(List<? extends IManagedEntity> entities) throws EntityException
    {
        connectionManager.verifyConnection();

        if(entities.size() > 0)
        {
            final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.BATCH_SAVE, entities);
            this.endpoint.execute(token);
        }
    }

    /**
     * Deletes a single entity
     *
     * The entity must exist otherwise it will throw an exception.  This will cascade delete relationships and remove index references.
     *
     * @since 1.0.0
     * @param entity Managed Entity to delete
     * @return Flag indicating it was deleted
     * @throws EntityException Error occurred while deleting
     */
    @Override
    public boolean deleteEntity(IManagedEntity entity) throws EntityException
    {
        connectionManager.verifyConnection();

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.DELETE, entity);
        return (boolean)this.endpoint.execute(token);
    }

    /**
     * Deletes list of entities.
     *
     * The entities must exist otherwise it will throw an exception.  This will cascade delete relationships and remove index references.
     *
     * Requires all of the entities to be of the same type
     *
     * @since 1.0.0
     * @param entities List of entities
     * @throws EntityException Error occurred while deleting.  If exception is thrown, preceding entities will not be rolled back
     */
    @Override
    public void deleteEntities(List<? extends IManagedEntity> entities) throws EntityException
    {
        connectionManager.verifyConnection();

        if(entities.size() > 0)
        {
            final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.BATCH_DELETE, entities);
            this.endpoint.execute(token);
        }
    }

    /**
     * Execute query with criteria and optional row limitations
     *
     * @since 1.0.0
     *
     * @param query Query containing criteria
     *
     * @return Query Results
     *
     * @throws EntityException Error while executing query
     */
    @Override
    public List executeQuery(Query query) throws EntityException
    {
        connectionManager.verifyConnection();

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.QUERY, query);
        QueryResultResponseBody results = (QueryResultResponseBody)this.endpoint.execute(token);
        query.setResultsCount(results.getMaxResults());
        return results.getResultList();
    }

    /**
     * Execute query with criteria and optional row limitations.  Specify lazy instantiation of query results.
     *
     * @since 1.0.0
     *
     * @param query Query containing criteria
     *
     * @return LazyQueryCollection lazy loaded results
     *
     * @throws EntityException Error while executing query
     */
    @Override
    public List executeLazyQuery(Query query) throws EntityException
    {
        connectionManager.verifyConnection();

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.QUERY_LAZY, query);
        QueryResultResponseBody results = (QueryResultResponseBody)this.endpoint.execute(token);
        query.setResultsCount(results.getMaxResults());
        return results.getResultList();
    }

    /**
     * Updates all rows returned by a given query
     *
     * The query#updates list must not be null or empty
     *
     * @since 1.0.0
     *
     * @param query Query used to filter entities with criteria
     *
     * @throws EntityException Exception occurred while executing update query
     *
     * @return Number of entities updated
     */
    @Override
    public int executeUpdate(Query query) throws EntityException
    {
        connectionManager.verifyConnection();

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.QUERY_UPDATE, query);
        QueryResultResponseBody results = (QueryResultResponseBody)this.endpoint.execute(token);
        query.setResultsCount(results.getMaxResults());
        return results.getResults();
    }

    /**
     * Execute query and delete entities returned in the results
     *
     * @since 1.0.0
     *
     * @param query Query used to filter entities with criteria
     *
     * @throws EntityException Exception occurred while executing delete query
     *
     * @return Number of entities deleted
     */
    @Override
    public int executeDelete(Query query) throws EntityException
    {
        connectionManager.verifyConnection();

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.QUERY_DELETE, query);
        QueryResultResponseBody results = (QueryResultResponseBody)this.endpoint.execute(token);
        query.setResultsCount(results.getMaxResults());
        return results.getResults();
    }

    /**
     * Hydrates an instantiated entity.  The instantiated entity must have the primary key defined and partition value if the data is partitioned.
     * All relationships are hydrated based on their fetch policy.
     * The entity must also not be null.
     *
     * @since 1.0.0
     *
     * @param entity Entity to hydrate.
     *
     * @return Managed Entity
     *
     * @throws EntityException Error when hydrating entity
     */
    @Override
    public IManagedEntity find(IManagedEntity entity) throws EntityException
    {
        connectionManager.verifyConnection();

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.FIND, entity);
        IManagedEntity results = (IManagedEntity)this.endpoint.execute(token);
        ReflectionUtil.copy(results, entity, context.getDescriptorForEntity(entity));
        return entity;
    }

    /**
     * Find Entity By Class and ID.
     *
     * All relationships are hydrated based on their fetch policy.  This does not take into account the partition.
     *
     * @since 1.0.0
     *
     * @param clazz Managed Entity Type.  This must be a cast of IManagedEntity
     * @param id Primary Key of entity
     * @return Managed Entity
     * @throws EntityException Error when finding entity
     */
    @Override
    public IManagedEntity findById(Class clazz, Object id) throws EntityException
    {
        connectionManager.verifyConnection();

        final EntityRequestBody body = new EntityRequestBody();
        body.setId(id);
        body.setType(clazz.getName());

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.FIND_BY_ID, body);
        return (IManagedEntity)this.endpoint.execute(token);
    }

    /**
     * Find Entity By Class and ID.
     *
     * All relationships are hydrated based on their fetch policy.  This does not take into account the partition.
     *
     * @since 1.0.0
     *
     * @param clazz Managed Entity Type.  This must be a cast of IManagedEntity
     * @param id Primary Key of entity
     * @param partitionId Partition value for entity
     * @return Managed Entity
     * @throws EntityException Error when finding entity within partition specified
     */
    @Override
    public IManagedEntity findByIdInPartition(Class clazz, Object id, Object partitionId) throws EntityException
    {

        connectionManager.verifyConnection();

        final EntityRequestBody body = new EntityRequestBody();
        body.setId(id);
        body.setType(clazz.getName());
        body.setPartitionId(String.valueOf(partitionId));

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.FIND_BY_ID_IN_PARTITION, body);
        return (IManagedEntity)this.endpoint.execute(token);
    }

    /**
     * Determines if the entity exists within the database.
     *
     * It is determined by the primary id and partition value
     *
     * @since 1.0.0
     *
     * @param entity Managed Entity to check
     *
     * @return Returns true if the entity primary key exists. Otherwise it returns false
     *
     * @throws EntityException Error when finding entity within partition specified
     */
    @Override
    public boolean exists(IManagedEntity entity) throws EntityException
    {
        connectionManager.verifyConnection();

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.EXISTS, entity);
        return (boolean)this.endpoint.execute(token);
    }

    /**
     * Determines if the entity exists within the database.
     *
     * It is determined by the primary id and partition value
     *
     * @since 1.0.0
     *
     * @param entity Managed Entity to check
     *
     * @param partitionId Partition Value for entity
     *
     * @return Returns true if the entity primary key exists. Otherwise it returns false
     *
     * @throws EntityException Error when finding entity within partition specified
     */
    @Override
    public boolean exists(IManagedEntity entity, Object partitionId) throws EntityException
    {
        connectionManager.verifyConnection();

        final EntityRequestBody body = new EntityRequestBody();
        body.setEntity(entity);
        body.setType(entity.getClass().getName());
        body.setPartitionId(String.valueOf(partitionId));

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.EXISTS_IN_PARTITION, body);
        return (boolean)this.endpoint.execute(token);
    }

    /**
     * Provides a list of all entities with a given type
     *
     * @param clazz  Type of managed entity to retrieve
     *
     * @return Unsorted List of all entities with type
     *
     * @throws EntityException Exception occurred while fetching results
     */
    @Override
    public List list(Class clazz) throws EntityException
    {
        connectionManager.verifyConnection();

        final EntityDescriptor descriptor = context.getBaseDescriptorForEntity(clazz);

        // Get the class' identifier and add a simple criteria to ensure the identifier is not null.  This should return all records.
        QueryCriteria criteria = new QueryCriteria(descriptor.getIdentifier().getName(), QueryCriteriaOperator.NOT_NULL);
        return list(clazz, criteria);
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
    @Override
    public List list(Class clazz, QueryCriteria criteria) throws EntityException
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
    @Override
    public List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy) throws EntityException
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
    @Override
    public List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy) throws EntityException
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
     * @param partitionId Partition value for entities
     *
     * @return Unsorted List of results matching criteria within a partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, Object partitionId) throws EntityException
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
     * @param partitionId Partition value for entities
     *
     * @return Sorted List of results matching criteria within a partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy, Object partitionId) throws EntityException
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
     * @param partitionId Partition value for entities
     *
     * @return Sorted List of results matching criteria within a partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy, Object partitionId) throws EntityException
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
    @Override
    public List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy) throws EntityException
    {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

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
     * @param partitionId Partition value to filter results
     *
     * @return Sorted List of results matching criteria within range and partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy, Object partitionId) throws EntityException
    {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

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
     * Force Hydrate relationship based on attribute name
     *
     * @since 1.0.0
     *
     * @param entity Managed Entity to attach relationship values
     *
     * @param attribute String representation of relationship attribute
     *
     * @throws EntityException Error when hydrating relationship.  The attribute must exist and be a relationship.
     */
    @Override
    public void initialize(IManagedEntity entity, String attribute) throws EntityException
    {
        connectionManager.verifyConnection();

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);

        final RelationshipDescriptor relationshipDescriptor = descriptor.getRelationships().get(attribute);

        if(relationshipDescriptor == null)
        {
            throw new RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, attribute, entity.getClass().getName());
        }

        EntityInitializeBody body = new EntityInitializeBody();
        body.setEntityId(AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier()));
        body.setAttribute(attribute);
        body.setEntityType(entity.getClass().getName());
        Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, context);
        if(partitionValue != null)
        {
            body.setPartitionId(String.valueOf(partitionValue));
        }

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.INITIALIZE, body);
        List relationship = (List)this.endpoint.execute(token);


        ReflectionUtil.setAny(entity, relationship, ReflectionUtil.getOffsetField(entity.getClass(), attribute));
    }

    /**
     * Find Relationship value(s)
     *
     * @param entity Managed Entity to attach relationship values
     *
     * @param attribute String representation of relationship attribute
     *
     * @return reference to initialized relationship
     *
     * @throws EntityException Error when entity does not exist
     */
    @Override
    public Object findRelationship(IManagedEntity entity, String attribute) throws EntityException {
        this.initialize(entity, attribute);
        return ReflectionUtil.getAny(entity, ReflectionUtil.getOffsetField(entity.getClass(), attribute));
    }

    /**
     * This is a way to batch save all relationships for an entity.  This does not retain any existing relationships and will
     * overwrite all existing with the set you are sending in.  This is useful to optimize batch saving entities with relationships.
     *
     * @since 1.0.0
     * @param entity Parent Managed Entity
     * @param relationship Relationship attribute
     * @param relationshipIdentifiers Existing relationship identifiers
     *
     * @throws EntityException Error occurred while saving relationship.
     */
    @Override
    public void saveRelationshipsForEntity(IManagedEntity entity, String relationship, Set<Object> relationshipIdentifiers) throws EntityException
    {
        connectionManager.verifyConnection();

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);

        Class attributeType = descriptor.getRelationships().get(relationship).getType();

        SaveRelationshipRequestBody body = new SaveRelationshipRequestBody();
        body.setEntity(entity);
        body.setRelationship(relationship);
        body.setIdentifiers(relationshipIdentifiers);
        body.setType(attributeType.getName());

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.SAVE_RELATIONSHIPS, body);
        this.endpoint.execute(token);
    }

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @since 1.0.0
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws EntityException The reference does not exist for that type
     */
    @Override
    public IManagedEntity getWithReferenceId(Class entityType, long referenceId) throws EntityException
    {
        connectionManager.verifyConnection();

        final EntityRequestBody body = new EntityRequestBody();
        body.setId(referenceId);
        body.setType(entityType.getName());

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.FIND_BY_REFERENCE_ID, body);
        return (IManagedEntity)this.endpoint.execute(token);
    }

    /**
     * Retrieves an entity using the primaryKey and partition
     *
     * @since 1.0.0
     *
     * @param clazz Entity Type
     *
     * @param id Entity Primary Key
     *
     * @param partitionId - Partition Identifier.  Not to be confused with partition value.  This is a unique id within the partition System table
     * @return Managed Entity
     *
     * @throws EntityException error occurred while attempting to retrieve entity.
     */
    @Override
    public IManagedEntity findByIdWithPartitionId(Class clazz, Object id, long partitionId) throws EntityException
    {
        connectionManager.verifyConnection();

        final EntityRequestBody body = new EntityRequestBody();
        body.setId(id);
        body.setPartitionId(String.valueOf(partitionId));
        body.setType(clazz.getName());

        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.FIND_WITH_PARTITION_ID, body);
        return (IManagedEntity)this.endpoint.execute(token);
    }


    /**
     * Execute query and delete entities returned in the results
     *
     * @since 1.0.0
     *
     * @param query Query used to filter entities with criteria
     *
     * @throws RemoteException Exception occurred while executing delete query
     *
     * @return Number of entities deleted
     */
    public QueryResult executeDeleteForResults(Query query) throws RemoteException
    {
        return new QueryResult(query, this.executeDelete(query));
    }

    /**
     * Updates all rows returned by a given query
     *
     * The query#updates list must not be null or empty
     *
     * @since 1.0.0
     *
     * @param query Query used to filter entities with criteria
     *
     * @throws RemoteException Exception occurred while executing update query
     *
     * @return Number of entities updated
     */
    public QueryResult executeUpdateForResults(Query query) throws RemoteException
    {
        return new QueryResult(query, this.executeUpdate(query));
    }

    /**
     * Execute query with criteria and optional row limitations
     *
     * @since 1.0.0
     *
     * @param query Query containing criteria
     *
     * @return Query Results
     *
     * @throws RemoteException Error while executing query
     */
    public QueryResult executeQueryForResults(Query query) throws RemoteException
    {
        return new QueryResult(query, this.executeQuery(query));
    }

    /**
     * Execute query with criteria and optional row limitations.  Specify lazy instantiation of query results.
     *
     * @since 1.0.0
     *
     * @param query Query containing criteria
     *
     * @return LazyQueryCollection lazy loaded results
     *
     * @throws RemoteException Error while executing query
     */
    public QueryResult executeLazyQueryForResults(Query query) throws RemoteException
    {
        return new QueryResult(query, this.executeLazyQuery(query));
    }

    /**
     * This method is used for bulk streaming data entities.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     *
     * @since 1.0.0
     *
     * @param query Query to execute and stream
     *
     * @param streamer Instance of the streamer to use to stream the data
     *
     */
    @Override
    public void stream(Query query, QueryStream streamer) throws EntityException
    {
        throw new StreamException(StreamException.UNSUPPORTED_FUNCTION_ALTERNATIVE);
    }

    /**
     * This method is used for bulk streaming.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     *
     * @since 1.0.0
     *
     * @param query Query to execute and stream
     *
     * @param queryStreamClass Class instance of the database stream
     *
     */
    @Override
    public void stream(Query query, Class queryStreamClass) throws EntityException
    {
        connectionManager.verifyConnection();

        final StreamRequestBody body = new StreamRequestBody(query, queryStreamClass);
        final RequestToken token = new RequestToken(RequestEndpoint.PERSISTENCE, RequestTokenType.STREAM_CLASS, body);
        this.endpoint.execute(token);
    }

    /**
     * Get Map representation of an entity with reference id.
     *
     * This is unsupported in the RemotePersistenceManager.  The reason is because it should not apply since the stream API
     * is not supported as a proxy QueryStream.
     *
     * @param entityType Original type of entity
     *
     * @param reference Reference location within a data structure
     *
     * @return Map of key value pair of the entity.  Key being the attribute name.
     */
    @Override
    public Map getMapWithReferenceId(Class entityType, long reference) throws EntityException {
        throw new StreamException(StreamException.UNSUPPORTED_FUNCTION);
    }
}
