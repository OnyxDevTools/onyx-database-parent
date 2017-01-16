package com.onyx.persistence.manager.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.exception.NoResultsException;
import com.onyx.exception.StreamException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.manager.SocketPersistenceManager;
import com.onyx.persistence.query.*;
import com.onyx.stream.QueryStream;
import com.onyx.util.ReflectionUtil;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by tosborn1 on 3/12/16.
 *
 * This class is a wrapper for a proxy persistence manager.
 * The purpose is to interface with the proxy without loosing your memory reference
 * of the entites that you are working with.
 *
 */
public class DefaultSocketPersistenceManager implements PersistenceManager
{
    protected SocketPersistenceManager proxyPersistenceManager;
    protected SchemaContext context = null;

    /**
     * Default Constructor including a proxy persistence manager
     * @param proxyPersistenceManager
     */
    public DefaultSocketPersistenceManager(SocketPersistenceManager proxyPersistenceManager, SchemaContext context)
    {
        super();
        this.context = context;
        this.proxyPersistenceManager = proxyPersistenceManager;
    }

    /**
     * The context of the database contains descriptor information regarding the entities and instruction on how to structure the record data.  This is usually done within the PersistenceManagerFactory.
     *
     * @since 1.0.0
     * @param context Schema Context implementation
     */
    public void setContext(SchemaContext context)
    {
        this.context = context;
    }

    /**
     * Return the Schema Context that was created by the Persistence Manager Factory.
     *
     * @since 1.0.0
     * @return context Schema Context Implementation
     */
    public SchemaContext getContext()
    {
        return this.context;
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
    public IManagedEntity saveEntity(IManagedEntity entity) throws EntityException
    {
        try {
            IManagedEntity savedEntity = proxyPersistenceManager.saveEntity(entity);
            ReflectionUtil.copy(savedEntity, entity, context.getDescriptorForEntity(entity));
            return entity;
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public void saveEntities(List<? extends IManagedEntity> entities) throws EntityException
    {
        try {
            proxyPersistenceManager.saveEntities(entities);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
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
    public boolean deleteEntity(IManagedEntity entity) throws EntityException
    {
        try {
            return proxyPersistenceManager.deleteEntity(entity);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public void deleteEntities(List<? extends IManagedEntity> entities) throws EntityException
    {
        try {
            proxyPersistenceManager.deleteEntities(entities);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public int executeDelete(Query query) throws EntityException
    {
        try {
            final QueryResult result = proxyPersistenceManager.executeDeleteForResults(query);
            query.setResultsCount(result.getQuery().getResultsCount());
            return (int)result.getResults();
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public int executeUpdate(Query query) throws EntityException
    {
        try {
            final QueryResult result = proxyPersistenceManager.executeUpdateForResults(query);
            query.setResultsCount(result.getQuery().getResultsCount());
            return (int)result.getResults();
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
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
    public List executeQuery(Query query) throws EntityException
    {
        try {
            final QueryResult result = proxyPersistenceManager.executeQueryForResults(query);
            query.setResultsCount(result.getQuery().getResultsCount());
            return (List)result.getResults();
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public List executeLazyQuery(Query query) throws EntityException
    {
        try {
            final QueryResult result = proxyPersistenceManager.executeLazyQueryForResults(query);
            query.setResultsCount(result.getQuery().getResultsCount());
            return (List)result.getResults();
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public IManagedEntity find(IManagedEntity entity) throws EntityException
    {
        try {
            IManagedEntity foundEntity = proxyPersistenceManager.find(entity);
            if(foundEntity != null)
            {
                ReflectionUtil.copy(foundEntity, entity, context.getDescriptorForEntity(entity));
            }
            return entity;
        } catch (RemoteException e) {
            throw new NoResultsException(e);
        }
    }

    /**
     * Find Relationship key(s)
     *
     * @param entity Managed Entity to attach relationship values
     *
     * @param attribute String representation of relationship attribute
     *s
     * @return reference to initialized relationship
     *
     * @throws EntityException Error when entity does not exist
     */
    public Object findRelationship(IManagedEntity entity, String attribute) throws EntityException {
        this.initialize(entity, attribute);
        return ReflectionUtil.getAny(entity, ReflectionUtil.getOffsetField(entity.getClass(), attribute));
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
    public IManagedEntity findById(Class clazz, Object id) throws EntityException
    {
        try {
            return proxyPersistenceManager.findById(clazz, id);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public IManagedEntity findByIdInPartition(Class clazz, Object id, Object partitionId) throws EntityException
    {
        try {
            return proxyPersistenceManager.findByIdInPartition(clazz, id, partitionId);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public boolean exists(IManagedEntity entity) throws EntityException
    {
        try {
            return proxyPersistenceManager.exists(entity);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public boolean exists(IManagedEntity entity, Object partitionId) throws EntityException
    {
        try {
            return proxyPersistenceManager.exists(entity, partitionId);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public void initialize(IManagedEntity entity, String attribute) throws EntityException
    {
        try {
            Object relationshipValue = proxyPersistenceManager.findRelationship(entity, attribute);
            ReflectionUtil.setAny(entity, relationshipValue, ReflectionUtil.getOffsetField(entity.getClass(), attribute));
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public List list(Class clazz, QueryCriteria criteria) throws EntityException
    {
        try {
            return proxyPersistenceManager.list(clazz, criteria);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy) throws EntityException
    {
        try {
            return proxyPersistenceManager.list(clazz, criteria, orderBy);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy) throws EntityException
    {
        try {
            return proxyPersistenceManager.list(clazz, criteria, orderBy);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public List list(Class clazz, QueryCriteria criteria, Object partitionId) throws EntityException
    {
        try {
            return proxyPersistenceManager.list(clazz, criteria, partitionId);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy, Object partitionId) throws EntityException
    {
        try {
            return proxyPersistenceManager.list(clazz, criteria, orderBy, partitionId);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy, Object partitionId) throws EntityException
    {
        try {
            return proxyPersistenceManager.list(clazz, criteria, orderBy, partitionId);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy) throws EntityException
    {
        try {
            return proxyPersistenceManager.list(clazz, criteria, start, maxResults, orderBy);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy, Object partitionId) throws EntityException
    {
        try {
            return proxyPersistenceManager.list(clazz, criteria, start, maxResults, orderBy, partitionId);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public void saveRelationshipsForEntity(IManagedEntity entity, String relationship, Set<Object> relationshipIdentifiers) throws EntityException
    {
        try {
            proxyPersistenceManager.saveRelationshipsForEntity(entity, relationship, relationshipIdentifiers);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
    }

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @since 1.0.0
     * @param entityType Type of managed entity
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws EntityException The reference does not exist for that type
     */
    public IManagedEntity getWithReferenceId(Class entityType, long referenceId) throws EntityException
    {
        try {
            return proxyPersistenceManager.getWithReferenceId(entityType, referenceId);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
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
    public IManagedEntity findByIdWithPartitionId(Class clazz, Object id, long partitionId) throws EntityException
    {
        try {
            return proxyPersistenceManager.findByIdWithPartitionId(clazz, id, partitionId);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
    }

    /**
     * This method is used for bulk streaming data entities.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     * This is unsupported in a remote instance.  The purpose is to enforce efficiency and simplicity.
     *
     * It would be better to use the PersistenceManager#stream(Query query, Class queryStreamClass) method.
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
        try
        {
            proxyPersistenceManager.stream(query, queryStreamClass);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
    }

    /**
     * Get Map representation of an entity with reference id
     *
     * @param entityType Original type of entity
     *
     * @param reference Reference location within a data structure
     *
     * @return Map of key key pair of the entity.  Key being the attribute name.
     */
    @Override
    public Map getMapWithReferenceId(Class entityType, long reference) throws EntityException {
        try
        {
            return proxyPersistenceManager.getMapWithReferenceId(entityType, reference);
        } catch (RemoteException e) {
            throw (EntityException)e.getCause();
        }
    }
}
