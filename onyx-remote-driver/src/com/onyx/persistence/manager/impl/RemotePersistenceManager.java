package com.onyx.persistence.manager.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.exception.StreamException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.*;
import com.onyx.stream.QueryStream;
import com.onyx.util.ReflectionUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistence manager supplies a public API for performing database persistence and querying operations.  This specifically is used for an remote database.
 * Entities that are passed through these methods must be serializable
 *
 * This was refactored on 02/12/2017 in order to use the new RMI Server.  It has been simplified to just use proxies.  Also some
 * of the default methods were  implemented on the interface.  Yay Java 1.8
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
 *   factory.close(); //Close the remote database
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.manager.PersistenceManager
 *
 * Tim Osborn - 02/13/2017 This was augmented to use the new RMI Server.  Also, simplified
 *              so that we take advantage of default methods within the PersistenceManager interface.
 */
public class RemotePersistenceManager extends AbstractPersistenceManager implements PersistenceManager {

    private SchemaContext context;
    private final PersistenceManager proxy;

    /**
     * Default Constructor.  This should be invoked by the persistence manager factory
     *
     * @since 1.1.0
     * @param persistenceManager Proxy Persistence manager on server
     */
    public RemotePersistenceManager(PersistenceManager persistenceManager)
    {
        this.proxy = persistenceManager;
    }


    @Override
    public void setContext(SchemaContext context) {
        this.context = context;
    }

    @Override
    public SchemaContext getContext() {
        return context;
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
     * @throws EntityException Exception occurred while persisting an entity
     */
    @Override
    public IManagedEntity saveEntity(IManagedEntity entity) throws EntityException
    {
        IManagedEntity copyValue = proxy.saveEntity(entity);
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
        if(entities.size() > 0)
        {
            proxy.saveEntities(entities);
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
        return proxy.deleteEntity(entity);
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
        proxy.deleteEntities(entities);
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
        QueryResult result = proxy.executeQueryForResult(query);
        query.setResultsCount(result.getQuery().getResultsCount());
        return (List)result.getResults();
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
        QueryResult result = proxy.executeLazyQueryForResult(query);
        query.setResultsCount(result.getQuery().getResultsCount());
        return (List)result.getResults();
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
        QueryResult result = proxy.executeUpdateForResult(query);
        query.setResultsCount(result.getQuery().getResultsCount());
        return (int)result.getResults();
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
        QueryResult result = proxy.executeDeleteForResult(query);
        query.setResultsCount(result.getQuery().getResultsCount());
        return (int)result.getResults();
    }

    /**
     * Hydrates an instantiated entity.  The instantiated entity must have the primary key defined and partition key if the data is partitioned.
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
        IManagedEntity results = proxy.find(entity);
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
        return proxy.findById(clazz, id);
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
     * @param partitionId Partition key for entity
     * @return Managed Entity
     * @throws EntityException Error when finding entity within partition specified
     */
    @Override
    public IManagedEntity findByIdInPartition(Class clazz, Object id, Object partitionId) throws EntityException
    {
        return proxy.findByIdInPartition(clazz, id, partitionId);
    }

    /**
     * Determines if the entity exists within the database.
     *
     * It is determined by the primary id and partition key
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
        return proxy.exists(entity);
    }

    /**
     * Determines if the entity exists within the database.
     *
     * It is determined by the primary id and partition key
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
        return proxy.exists(entity,partitionId);
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
        final EntityDescriptor descriptor = context.getBaseDescriptorForEntity(clazz);
        QueryCriteria criteria = new QueryCriteria(descriptor.getIdentifier().getName(), QueryCriteriaOperator.NOT_NULL);

        return proxy.list(clazz, criteria);
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
        Object relationship = proxy.getRelationship(entity, attribute);
        ReflectionUtil.setAny(entity, relationship, context.getDescriptorForEntity(entity).getRelationships().get(attribute).getField());
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
        proxy.saveRelationshipsForEntity(entity, relationship, relationshipIdentifiers);
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
        return proxy.getWithReferenceId(entityType, referenceId);
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
     * @param partitionId - Partition Identifier.  Not to be confused with partition key.  This is a unique id within the partition System table
     * @return Managed Entity
     *
     * @throws EntityException error occurred while attempting to retrieve entity.
     */
    @Override
    public IManagedEntity findByIdWithPartitionId(Class clazz, Object id, long partitionId) throws EntityException
    {
        return proxy.findByIdWithPartitionId(clazz, id, partitionId);
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
        proxy.stream(query, queryStreamClass);
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
     * @return Map of key key pair of the entity.  Key being the attribute name.
     */
    @Override
    public Map getMapWithReferenceId(Class entityType, long reference) throws EntityException {
        throw new StreamException(StreamException.UNSUPPORTED_FUNCTION);
    }
}
