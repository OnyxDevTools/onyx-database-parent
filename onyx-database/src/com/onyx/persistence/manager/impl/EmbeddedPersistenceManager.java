package com.onyx.persistence.manager.impl;

import com.onyx.aggregate.Aggregator;
import com.onyx.aggregate.MapAggregator;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.*;
import com.onyx.fetch.PartitionQueryController;
import com.onyx.helpers.*;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.collections.LazyQueryCollection;
import com.onyx.persistence.manager.SocketPersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.persistence.query.QueryResult;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;
import com.onyx.relationship.EntityRelationshipManager;
import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.RelationshipReference;
import com.onyx.util.AttributeField;
import com.onyx.util.ObjectUtil;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Persistence manager supplies a public API for performing database persistence and querying operations.  This specifically is used for an embedded database.
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("/MyDatabaseLocation")
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
public class EmbeddedPersistenceManager extends UnicastRemoteObject implements PersistenceManager, SocketPersistenceManager {

    protected SchemaContext context;
    protected boolean journalingEnabled;

    public static final ObjectUtil objectUtil = ObjectUtil.getInstance();

    /**
     * Default constructor.  We do not want to export the object if in embedded mode.
     * @throws RemoteException
     */
    public EmbeddedPersistenceManager() throws RemoteException {
        super();
        UnicastRemoteObject.unexportObject(this, true);
    }

    /**
     * Constructor with option to export RMI service
     * @param export Should export RMI Service
     * @throws RemoteException RMI Exception
     */
    public EmbeddedPersistenceManager(boolean export) throws RemoteException
    {
        super();
        if(!export)
        {
            UnicastRemoteObject.unexportObject(this, true);
        }
    }

    /**
     * The context of the database contains descriptor information regarding the entities and instruction on how to structure the record data.  This is usually done within the PersistenceManagerFactory.
     *
     * @since 1.0.0
     * @param context Schema Context implementation
     */
    @Override
    public void setContext(SchemaContext context)
    {
        this.context = context;
        this.context.setSystemPersistenceManager(this);
    }

    /**
     * Set journaling Enabled.  If enabled, the persistence manager will keep a WAL transaction file for all persistence actions.
     *
     * @param journalingEnabled Enabled True or False
     */
    public void setJournalingEnabled(boolean journalingEnabled)
    {
        this.journalingEnabled = journalingEnabled;
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
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final RecordController recordController = context.getRecordController(descriptor);

        Object id = AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier());
        long oldReferenceId = 0;

        if(descriptor.getIndexes().size() > 0)
        {
            oldReferenceId = (id != null) ? recordController.getReferenceId(id) : 0;
        }

        id = recordController.save(entity);

        // Add Write Transaction to log
        if(this.journalingEnabled)
        {
            context.getTransactionController().writeSave(entity);
        }

        IndexHelper.saveAllIndexesForEntity(context, descriptor, id, oldReferenceId, entity);
        RelationshipHelper.saveAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

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
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        if (entities.isEmpty())
            return;

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entities.get(0));
        final RecordController recordController = context.getRecordController(descriptor);
        long oldReferenceId = 0;

        Object id = null;
        for (IManagedEntity entity : entities)
        {

            id = AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier());
            if(descriptor.getIndexes().size() > 0)
            {
                oldReferenceId = (id != null) ? recordController.getReferenceId(id) : 0;
            }
            id = recordController.save(entity);

            // Add write trasaction to log
            if(this.journalingEnabled)
            {
                context.getTransactionController().writeSave(entity);
            }

            IndexHelper.saveAllIndexesForEntity(context, descriptor, id, oldReferenceId, entity);
            RelationshipHelper.saveAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);
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
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final RecordController recordController = context.getRecordController(descriptor);

        // Write Delete transaction to log
        if(this.journalingEnabled)
        {
            context.getTransactionController().writeDelete(entity);
        }

        final long referenceId = recordController.getReferenceId(AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier()));
        IndexHelper.deleteAllIndexesForEntity(context, descriptor, referenceId);
        RelationshipHelper.deleteAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

        recordController.delete(entity);

        return true;
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
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        for (Object entity : entities)
        {
            deleteEntity((IManagedEntity) entity);
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
    @Override
    public int executeDelete(Query query) throws EntityException
    {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        PartitionHelper.setPartitionIdForQuery(query, context); // Helper for setting the partition mode

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(query.getEntityType(), query.getPartition());

        ValidationHelper.validateQuery(descriptor, query, context);

        final PartitionQueryController queryController = new PartitionQueryController(query.getCriteria(), query.getEntityType(), descriptor, query, context, this);
        try
        {
            final Map results = queryController.getIndexesForCriteria(query.getCriteria(), null, true, query);

            query.setResultsCount(results.size());

            // Write Delete transaction to log
            if(this.journalingEnabled)
            {
                context.getTransactionController().writeDeleteQuery(query);
            }

            return queryController.deleteRecordsWithIndexes(results, query);
        } finally
        {
            queryController.cleanup();
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
    @Override
    public int executeUpdate(Query query) throws EntityException
    {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        PartitionHelper.setPartitionIdForQuery(query, context); // Helper for setting the partition mode

        // This will throw an exception if not valid
        final Class clazz = query.getEntityType();
        final IManagedEntity entity = EntityDescriptor.createNewEntity(clazz);

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, query.getPartition());
        ValidationHelper.validateQuery(descriptor, query, context);

        final PartitionQueryController queryController = new PartitionQueryController(query.getCriteria(), clazz, descriptor, query, context, this);
        try
        {
            final Map<Long, Long> results = queryController.getIndexesForCriteria(query.getCriteria(), null, true, query);

            query.setResultsCount(results.size());

            // Write Delete transaction to log
            if(this.journalingEnabled)
            {
                context.getTransactionController().writeQueryUpdate(query);
            }

            return queryController.updateRecordsWithValues(results, query.getUpdates(), query.getFirstRow(), query.getMaxResults());
        } finally
        {
            queryController.cleanup();
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
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        PartitionHelper.setPartitionIdForQuery(query, context); // Helper for setting the partition mode

        final Class clazz = query.getEntityType();
        IManagedEntity entity = EntityDescriptor.createNewEntity(clazz);

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, query.getPartition());

        ValidationHelper.validateQuery(descriptor, query, context);

        final PartitionQueryController queryController = new PartitionQueryController(query.getCriteria(), clazz, descriptor, query, context, this);

        try
        {
            Map results = queryController.getIndexesForCriteria(query.getCriteria(), null, true, query);

            query.setResultsCount(results.size());

            // This will go through and get a subset of fields
            if (query.getSelections() != null)
            {
                if (query.getQueryOrders() != null || query.getFirstRow() > 0 || query.getMaxResults() != -1)
                {
                    results = queryController.sort(
                            (query.getQueryOrders() != null) ? query.getQueryOrders().toArray(new QueryOrder[query.getQueryOrders().size()]) : new QueryOrder[0], results);
                }

                final Map<Object, Map<String, Object>> attributeValues = queryController.hydrateQueryAttributes(query.getSelections().toArray(new String[query.getSelections().size()]), results, false, query.getFirstRow(), query.getMaxResults());

                final List<Map<String, Object>> finalResults = new ArrayList<>(attributeValues.values());

                if (query.getProjections() != null)
                {
                    // TODO: Implement Projections
                }

                return finalResults;

            } else
            {

                final List returnValue = queryController.hydrateResultsWithIndexes(results,
                        (query.getQueryOrders() != null) ? query.getQueryOrders().toArray(new QueryOrder[query.getQueryOrders().size()]) : new QueryOrder[0],
                        query.getFirstRow(),
                        query.getMaxResults());
                if (query.getProjections() != null)
                {
                    // TODO: Implement Projections
                }

                return returnValue;
            }
        } finally
        {
            queryController.cleanup();
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
    @Override
    public List executeLazyQuery(Query query) throws EntityException
    {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        PartitionHelper.setPartitionIdForQuery(query, context); // Helper for setting the partition mode

        final Class clazz = query.getEntityType();
        IManagedEntity entity = EntityDescriptor.createNewEntity(clazz);

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, query.getPartition());

        ValidationHelper.validateQuery(descriptor, query, context);

        final PartitionQueryController queryController = new PartitionQueryController(query.getCriteria(), clazz, descriptor, query, context, this);

        try
        {
            Map<Long, Long> results = queryController.getIndexesForCriteria(query.getCriteria(), null, true, query);

            query.setResultsCount(results.size());

            LazyQueryCollection<IManagedEntity> retVal = new LazyQueryCollection<IManagedEntity>(descriptor, results, context);
            return retVal;
        } finally
        {
            queryController.cleanup();
        }
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
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);


        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        IManagedEntity results = recordController.get(entity);

        if (results == null)
        {
            throw new NoResultsException();
        }

        RelationshipHelper.hydrateAllRelationshipsForEntity(results, new EntityRelationshipManager(), context);

        ObjectUtil.copy(results, entity, descriptor);

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
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        IManagedEntity entity = EntityDescriptor.createNewEntity(clazz);
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, "");
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        entity = recordController.getWithId(id);

        if (entity == null)
        {
            return null;
        }

        RelationshipHelper.hydrateAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

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
     * @param partitionId Partition value for entity
     * @return Managed Entity
     * @throws EntityException Error when finding entity within partition specified
     */
    @Override
    public IManagedEntity findByIdInPartition(Class clazz, Object id, Object partitionId) throws EntityException
    {

        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        IManagedEntity entity = EntityDescriptor.createNewEntity(clazz);
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionId);
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        entity = recordController.getWithId(id);

        if (entity == null)
        {
            return null;
        }

        RelationshipHelper.hydrateAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

        return entity;
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
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        return recordController.exists(entity);
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
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionId);
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        return recordController.exists(entity);
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
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);

        final Object identifier = AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier());
        RelationshipReference entityId = null;
        Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, context);

        if (partitionValue != PartitionHelper.NULL_PARTITION && partitionValue != null)
        {
            SystemPartitionEntry partitionEntry = context.getPartitionWithValue(descriptor.getClazz(), PartitionHelper.getPartitionFieldValue(entity, context));
            entityId = new RelationshipReference(identifier, partitionEntry.getIndex());
        } else
        {
            entityId = new RelationshipReference(identifier, 0);
        }

        RelationshipDescriptor relationshipDescriptor = descriptor.getRelationships().get(attribute);

        if (relationshipDescriptor == null)
        {
            throw new RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, attribute, entity.getClass().getCanonicalName());
        }

        RelationshipController relationshipController = context.getRelationshipController(relationshipDescriptor);
        relationshipController.hydrateRelationshipForEntity(entityId, entity, new EntityRelationshipManager(), true);
    }

    /**
     * Hydrate a relationship and return the value
     *
     * @since 1.0.0
     *
     * @param entity Managed Entity to attach relationship values
     *
     * @param attribute String representation of relationship attribute
     *
     * @throws RemoteException Error when hydrating relationship.  The attribute must exist and be a relationship.
     */
    @Override
    public Object findRelationship(IManagedEntity entity, String attribute) throws RemoteException {
        this.initialize(entity, attribute);
        return objectUtil.getAttribute(new AttributeField(objectUtil.getField(entity.getClass(), attribute)), entity);
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
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final RelationshipDescriptor relationshipDescriptor = descriptor.getRelationships().get(relationship);

        if (relationshipDescriptor == null)
        {
            throw new RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, relationship, entity.getClass().getCanonicalName());
        }

        Set<RelationshipReference> references = new HashSet<>();
        for (Object identifier : relationshipIdentifiers)
        {
            if (identifier instanceof RelationshipReference)
            {
                references.add((RelationshipReference) identifier);
            } else
            {
                references.add(new RelationshipReference(identifier, 0));
            }
        }
        final RelationshipController relationshipController = context.getRelationshipController(relationshipDescriptor);
        relationshipController.updateAll(entity, references);
    }

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @since 1.0.0
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws EntityException The reference does not exist for that type
     */
    public IManagedEntity getWithReferenceId(Class entityType, long referenceId) throws EntityException
    {

        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        IManagedEntity entity = EntityDescriptor.createNewEntity(entityType);
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, "");
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        entity = recordController.getWithReferenceId(referenceId);

        RelationshipHelper.hydrateAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

        return entity;
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
    public IManagedEntity findByIdWithPartitionId(Class clazz, Object id, long partitionId) throws EntityException
    {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        IManagedEntity entity = EntityDescriptor.createNewEntity(clazz);
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, "");
        final PartitionContext partitionContext = new PartitionContext(context, descriptor);
        final RecordController recordController = partitionContext.getRecordControllerForPartition(partitionId);

        // Find the object
        entity = recordController.getWithId(id);

        RelationshipHelper.hydrateAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

        return entity;
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
     * Get Map representation of an entity with reference id
     *
     * @param entityType Original type of entity
     *
     * @param reference Reference location within a data structure
     *
     * @return Map of key value pair of the entity.  Key being the attribute name.
     */
    public Map getMapWithReferenceId(Class entityType, long reference) throws EntityException
    {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        IManagedEntity entity = EntityDescriptor.createNewEntity(entityType);
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, "");
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        return recordController.getMapWithReferenceId(reference);
    }



    /**
     * This method is used for bulk aggregation.  An example of bulk aggregation is for analytics or bulk updates included but not limited to model changes.
     *
     * @since 1.0.0
     *
     * @param aggregator Instance of the aggregator to implement the bulk operation
     *
     * @param query Query to execute and process by the aggregator
     */

    @Override
    public void aggregate(Aggregator aggregator, Query query) throws EntityException
    {

        final LazyQueryCollection entityList = (LazyQueryCollection)executeLazyQuery(query);
        final BiConsumer consumer = aggregator.getConsumer();
        final PersistenceManager persistenceManagerInstance = this;

        for(int i = 0; i < entityList.size(); i++)
        {
            Object objectToAggregate = null;

            if(aggregator instanceof MapAggregator)
            {
                objectToAggregate = entityList.getDict(i);
            }
            else
            {
                objectToAggregate = entityList.get(i);
            }

            consumer.accept(objectToAggregate, persistenceManagerInstance);
        }
    }

    /**
     * This method is used for bulk aggregation.  An example of bulk aggregation is for analytics or bulk updates included but not limited to model changes.
     *
     * @since 1.0.0
     *
     * @param aggregatorClass Class instance of the aggregator
     *
     * @param query Query to execute and process by the aggregator
     */
    @Override
    public void aggregate(Class<Aggregator> aggregatorClass, Query query) throws EntityException
    {
        Aggregator aggregator = null;
        try {
            aggregator = aggregatorClass.newInstance();
        } catch (InstantiationException e) {
            throw new AggregatorException(AggregatorException.CANNOT_INSTANTIATE_AGGREGATOR);
        } catch (IllegalAccessException e) {
            throw new AggregatorException(AggregatorException.CANNOT_INSTANTIATE_AGGREGATOR);
        }
        this.aggregate(aggregator, query);
    }

}
