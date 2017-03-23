package com.onyx.persistence.manager.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.*;
import com.onyx.fetch.PartitionQueryController;
import com.onyx.helpers.*;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.collections.LazyQueryCollection;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.*;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;
import com.onyx.relationship.EntityRelationshipManager;
import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.RelationshipReference;
import com.onyx.stream.QueryMapStream;
import com.onyx.stream.QueryStream;
import com.onyx.util.ReflectionUtil;

import java.util.*;

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
public class EmbeddedPersistenceManager extends AbstractPersistenceManager implements PersistenceManager {

    @SuppressWarnings("WeakerAccess")
    protected SchemaContext context;

    private boolean journalingEnabled;

    /**
     * Default constructor.  We do not want to export the object if in embedded mode.
     */
    public EmbeddedPersistenceManager() {

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

        Object id;
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

        final PartitionQueryController queryController = new PartitionQueryController(descriptor, this, context);

        try
        {
            final Map results = queryController.getReferencesForQuery(query);

            query.setResultsCount(results.size());

            // Write Delete transaction to log
            if(this.journalingEnabled)
            {
                context.getTransactionController().writeDeleteQuery(query);
            }

            return queryController.deleteRecordsWithReferences(results, query);
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
    @SuppressWarnings("unchecked")
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

        final PartitionQueryController queryController = new PartitionQueryController(descriptor, this, context);

        try
        {
            final Map<Long, Long> results = queryController.getReferencesForQuery(query);

            query.setResultsCount(results.size());

            // Write Delete transaction to log
            if(this.journalingEnabled)
            {
                context.getTransactionController().writeQueryUpdate(query);
            }

            return queryController.performUpdatsForQuery(query, results);
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

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(clazz, query.getPartition());

        ValidationHelper.validateQuery(descriptor, query, context);

        final PartitionQueryController queryController = new PartitionQueryController(descriptor, this, context);

        try
        {
            Map results = queryController.getReferencesForQuery(query);

            query.setResultsCount(results.size());

            // This will go through and get a subset of fields
            if (query.getSelections() != null)
            {
                if (query.getQueryOrders() != null || query.getFirstRow() > 0 || query.getMaxResults() != -1)
                {
                    results = queryController.sort(query, results);
                }

                final Map<Object, Map<String, Object>> attributeValues = queryController.hydrateQuerySelections(query, results);

                return new ArrayList<>(attributeValues.values());
            } else
            {

                return queryController.hydrateResultsWithReferences(query, results);

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
    @SuppressWarnings("unchecked")
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

        final PartitionQueryController queryController = new PartitionQueryController(descriptor, this, context);

        try
        {
            Map<Long, Long> results = queryController.getReferencesForQuery(query);

            query.setResultsCount(results.size());
            if (query.getQueryOrders() != null || query.getFirstRow() > 0 || query.getMaxResults() != -1)
            {
                results = queryController.sort(query, results);
            }
            return new LazyQueryCollection<IManagedEntity>(descriptor, results, context);
        } finally
        {
            queryController.cleanup();
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

        ReflectionUtil.copy(results, entity, descriptor);

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
     * @param partitionId Partition key for entity
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
        RelationshipReference entityId;
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
            throw new RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, attribute, entity.getClass().getName());
        }

        RelationshipController relationshipController = context.getRelationshipController(relationshipDescriptor);
        relationshipController.hydrateRelationshipForEntity(entityId, entity, new EntityRelationshipManager(), true);
    }

    /**
     * Hydrate a relationship and return the key
     *
     * @since 1.0.0
     *
     * @param entity Managed Entity to attach relationship values
     *
     * @param attribute String representation of relationship attribute
     *
     * @throws EntityException Error when hydrating relationship.  The attribute must exist and be a relationship.
     */
    @SuppressWarnings("unused")
    public Object findRelationship(IManagedEntity entity, String attribute) throws EntityException {
        return getRelationship(entity, attribute);
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
            throw new RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, relationship, entity.getClass().getName());
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
     * @param partitionId - Partition Identifier.  Not to be confused with partition key.  This is a unique id within the partition System table
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
     * @throws EntityException Exception occurred while executing delete query
     *
     * @return Number of entities deleted
     */
    @SuppressWarnings("unused")
    public QueryResult executeDeleteForResults(Query query) throws EntityException
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
     * @throws EntityException Exception occurred while executing update query
     *
     * @return Number of entities updated
     */
    @SuppressWarnings("unused")
    public QueryResult executeUpdateForResults(Query query) throws EntityException
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
     * @throws EntityException Error while executing query
     */
    @SuppressWarnings("unused")
    public QueryResult executeQueryForResults(Query query) throws EntityException
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
     * @throws EntityException Error while executing query
     */
    @SuppressWarnings("unused")
    public QueryResult executeLazyQueryForResults(Query query) throws EntityException
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
     * @return Map of key key pair of the entity.  Key being the attribute name.
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
     * Retrieve the quantity of entities that match the query criterium.
     *
     * usage:
     *
     * Query myQuery = new Query();
     * myQuery.setClass(SystemEntity.class);
     * long numberOfSystemEntities = persistenceManager.countForQuery(myQuery);
     *
     * or:
     *
     * Query myQuery = new Query(SystemEntity.class, new QueryCriteria("primaryKey", QueryCriteriaOperator.GREATER_THAN, 3));
     * long numberOfSystemEntitiesWithIdGt3 = persistenceManager.countForQuery(myQuery);
     *
     * @param query The query to apply to the count operation
     * @return The number of entities that meet the query criterium
     * @throws EntityException Error during query.
     * @since 1.3.0 Implemented with feature request #71
     */
    @Override
    public long countForQuery(Query query) throws EntityException {

        PartitionHelper.setPartitionIdForQuery(query, context); // Helper for setting the partition mode
        final Class clazz = query.getEntityType();

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(clazz, query.getPartition());
        ValidationHelper.validateQuery(descriptor, query, context);

        final PartitionQueryController queryController = new PartitionQueryController(descriptor, this, context);

        return queryController.getCountForQuery(query);
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
    @SuppressWarnings("unchecked")
    public void stream(Query query, QueryStream streamer) throws EntityException
    {
        final LazyQueryCollection entityList = (LazyQueryCollection) executeLazyQuery(query);
        final PersistenceManager persistenceManagerInstance = this;

        for (int i = 0; i < entityList.size(); i++) {
            Object objectToStream;

            if (streamer instanceof QueryMapStream) {
                objectToStream = entityList.getDict(i);
            } else {
                objectToStream = entityList.get(i);
            }

            streamer.accept(objectToStream, persistenceManagerInstance);
        }
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
        QueryStream streamer;
        try {
            streamer = (QueryStream)queryStreamClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new StreamException(StreamException.CANNOT_INSTANTIATE_STREAM);
        }
        this.stream(query, streamer);
    }


}
