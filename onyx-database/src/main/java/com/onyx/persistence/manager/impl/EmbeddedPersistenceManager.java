package com.onyx.persistence.manager.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.*;
import com.onyx.fetch.PartitionQueryController;
import com.onyx.fetch.PartitionReference;
import com.onyx.helpers.*;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.collections.LazyQueryCollection;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryResult;
import com.onyx.query.CachedResults;
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
 * @author Tim Osborn
 * @see com.onyx.persistence.manager.PersistenceManager
 * @since 1.0.0
 * <p>
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
     * @param context Schema Context implementation
     * @since 1.0.0
     */
    @Override
    public void setContext(SchemaContext context) {
        this.context = context;
        this.context.setSystemPersistenceManager(this);
    }

    /**
     * Return the Schema Context that was created by the Persistence Manager Factory.
     *
     * @return context Schema Context Implementation
     * @since 1.0.0
     */
    public SchemaContext getContext() {
        return this.context;
    }

    /**
     * Set journaling Enabled.  If enabled, the persistence manager will keep a WAL transaction file for all persistence actions.
     *
     * @param journalingEnabled Enabled True or False
     */
    public void setJournalingEnabled(boolean journalingEnabled) {
        this.journalingEnabled = journalingEnabled;
    }

    /**
     * Save entity.  Persists a single entity for update or insert.  This method will cascade relationships and persist indexes.
     *
     * @param entity Managed Entity to Save
     * @return Saved Managed Entity
     * @throws OnyxException Exception occured while persisting an entity
     * @since 1.0.0
     */
    @Override
    public <E extends IManagedEntity> E saveEntity(IManagedEntity entity) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final RecordController recordController = context.getRecordController(descriptor);

        Object id = AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier());
        long oldReferenceId = 0;

        if (descriptor.getIndexes().size() > 0) {
            oldReferenceId = (id != null) ? recordController.getReferenceId(id) : 0;
        }

        id = recordController.save(entity);

        // Add Write Transaction to log
        if (this.journalingEnabled) {
            context.getTransactionController().writeSave(entity);
        }

        IndexHelper.saveAllIndexesForEntity(context, descriptor, id, oldReferenceId, entity);
        RelationshipHelper.saveAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

        //noinspection unchecked
        return (E)entity;
    }

    /**
     * Batch saves a list of entities.
     * <p>
     * The entities must all be of the same type
     *
     * @param entities List of entities
     * @throws OnyxException Exception occurred while saving an entity within the list.  This will not roll back preceding saves if error occurs.
     * @since 1.0.0
     */
    @Override
    public void saveEntities(List<? extends IManagedEntity> entities) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        if (entities.isEmpty())
            return;

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entities.get(0));
        final RecordController recordController = context.getRecordController(descriptor);
        long oldReferenceId = 0;

        Object id;
        for (IManagedEntity entity : entities) {

            id = AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier());
            if (descriptor.getIndexes().size() > 0) {
                oldReferenceId = (id != null) ? recordController.getReferenceId(id) : 0;
            }
            id = recordController.save(entity);

            // Add write trasaction to log
            if (this.journalingEnabled) {
                context.getTransactionController().writeSave(entity);
            }

            IndexHelper.saveAllIndexesForEntity(context, descriptor, id, oldReferenceId, entity);
            RelationshipHelper.saveAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);
        }

    }

    /**
     * Deletes a single entity
     * <p>
     * The entity must exist otherwise it will throw an exception.  This will cascade delete relationships and remove index references.
     *
     * @param entity Managed Entity to delete
     * @return Flag indicating it was deleted
     * @throws OnyxException Error occurred while deleting
     * @since 1.0.0
     */
    @Override
    public boolean deleteEntity(IManagedEntity entity) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final RecordController recordController = context.getRecordController(descriptor);

        // Write Delete transaction to log
        if (this.journalingEnabled) {
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
     * <p>
     * The entities must exist otherwise it will throw an exception.  This will cascade delete relationships and remove index references.
     * <p>
     * Requires all of the entities to be of the same type
     *
     * @param entities List of entities
     * @throws OnyxException Error occurred while deleting.  If exception is thrown, preceding entities will not be rolled back
     * @since 1.0.0
     */
    @Override
    public void deleteEntities(List<? extends IManagedEntity> entities) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        for (Object entity : entities) {
            deleteEntity((IManagedEntity) entity);
        }

    }

    /**
     * Execute query and delete entities returned in the results
     *
     * @param query Query used to filter entities with criteria
     * @return Number of entities deleted
     * @throws OnyxException Exception occurred while executing delete query
     * @since 1.0.0
     */
    @Override
    public int executeDelete(Query query) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(query.getEntityType(), query.getPartition());

        ValidationHelper.validateQuery(descriptor, query, context);

        final PartitionQueryController queryController = new PartitionQueryController(descriptor, this, context);

        try {
            final Map results = queryController.getReferencesForQuery(query);

            query.setResultsCount(results.size());

            // Write Delete transaction to log
            if (this.journalingEnabled) {
                context.getTransactionController().writeDeleteQuery(query);
            }

            return queryController.deleteRecordsWithReferences(results, query);
        } finally {
            queryController.cleanup();
        }
    }

    /**
     * Updates all rows returned by a given query
     * <p>
     * The query#updates list must not be null or empty
     *
     * @param query Query used to filter entities with criteria
     * @return Number of entities updated
     * @throws OnyxException Exception occurred while executing update query
     * @since 1.0.0
     */
    @Override
    @SuppressWarnings("unchecked")
    public int executeUpdate(Query query) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        // This will throw an exception if not valid
        final Class clazz = query.getEntityType();
        final IManagedEntity entity = ReflectionUtil.createNewEntity(clazz);

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, query.getPartition());
        ValidationHelper.validateQuery(descriptor, query, context);

        final PartitionQueryController queryController = new PartitionQueryController(descriptor, this, context);

        try {
            final Map<Long, Long> results = queryController.getReferencesForQuery(query);

            query.setResultsCount(results.size());

            // Write Delete transaction to log
            if (this.journalingEnabled) {
                context.getTransactionController().writeQueryUpdate(query);
            }

            return queryController.performUpdatsForQuery(query, results);
        } finally {
            queryController.cleanup();
        }
    }

    /**
     * Execute query with criteria and optional row limitations
     *
     * @param query Query containing criteria
     * @return Query Results
     * @throws OnyxException Error while executing query
     * @since 1.0.0
     */
    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> executeQuery(Query query) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final Class clazz = query.getEntityType();

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(clazz, query.getPartition());

        ValidationHelper.validateQuery(descriptor, query, context);

        final PartitionQueryController queryController = new PartitionQueryController(descriptor, this, context);
        CachedResults cachedResults = null;

        try {
            // Check to see if there are cached query resutls
            cachedResults = context.getQueryCacheController().getCachedQueryResults(query);
            Map results;

            // If there are, use the cache rather re-checking criteria
            if (cachedResults != null && cachedResults.getReferences() != null) {
                results = cachedResults.getReferences();
                query.setResultsCount(cachedResults.getReferences().size());
                if (query.getSelections() != null) {
                    final Map<Object, Map<String, Object>> attributeValues = queryController.hydrateQuerySelections(query, results);
                    return (List<E>)new ArrayList(attributeValues.values());
                } else {
                    return (List<E>)queryController.hydrateResultsWithReferences(query, results);
                }
            }
            results = queryController.getReferencesForQuery(query);

            query.setResultsCount(results.size());

            if ((query.getQueryOrders() != null && query.getQueryOrders().size() > 0) || query.getFirstRow() > 0 || query.getMaxResults() != -1) {
                results = queryController.sort(query, results);
            }

            // This will go through and get a subset of fields
            if (query.getSelections() != null) {

                // Cache the query results
                cachedResults = context.getQueryCacheController().setCachedQueryResults(query, results);

                final Map<Object, Map<String, Object>> attributeValues = queryController.hydrateQuerySelections(query, results);
                if(query.isDistinct()) {
                    Set linkedHashSet = new LinkedHashSet<>(attributeValues.values());
                    return new ArrayList(linkedHashSet);
                }
                return (List<E>)new ArrayList<>(attributeValues.values());
            } else {
                if(cachedResults == null)
                    // Cache the query results
                    cachedResults = context.getQueryCacheController().setCachedQueryResults(query, results);
                else
                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (cachedResults) {
                        cachedResults.setReferences(results);
                    }
                return queryController.hydrateResultsWithReferences(query, results);
            }
        } finally {
            if(query.getChangeListener() != null) {
                context.getQueryCacheController().subscribe(cachedResults, query.getChangeListener());
            }
            queryController.cleanup();
        }
    }

    /**
     * Execute query with criteria and optional row limitations.  Specify lazy instantiation of query results.
     *
     * @param query Query containing criteria
     * @return LazyQueryCollection lazy loaded results
     * @throws OnyxException Error while executing query
     * @since 1.0.0
     */
    @Override
    @SuppressWarnings("unchecked")
    public List executeLazyQuery(Query query) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final Class clazz = query.getEntityType();
        IManagedEntity entity = ReflectionUtil.createNewEntity(clazz);

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, query.getPartition());

        ValidationHelper.validateQuery(descriptor, query, context);

        final PartitionQueryController queryController = new PartitionQueryController(descriptor, this, context);
        CachedResults cachedResults = null;

        try {
            // Check for cached query results.
            cachedResults = context.getQueryCacheController().getCachedQueryResults(query);
            Map results;

            // If there are, hydrate the existing rather than looking to the store
            if (cachedResults != null && cachedResults.getReferences() != null) {
                results = cachedResults.getReferences();
                query.setResultsCount(results.size());
                return new LazyQueryCollection<IManagedEntity>(descriptor, results, context);
            }

            // There were no cached results, load them from the store
            results = queryController.getReferencesForQuery(query);

            query.setResultsCount(results.size());
            if ((query.getQueryOrders() != null && query.getQueryOrders().size() > 0)  || query.getFirstRow() > 0 || query.getMaxResults() != -1) {
                results = queryController.sort(query, results);
            }

            if(cachedResults == null)
                // Cache the query results
                cachedResults = context.getQueryCacheController().setCachedQueryResults(query, results);
            else
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (cachedResults) {
                    cachedResults.setReferences(results);
                }

            return new LazyQueryCollection<IManagedEntity>(descriptor, results, context);
        } finally {
            if(query.getChangeListener() != null) {
                context.getQueryCacheController().subscribe(cachedResults, query.getChangeListener());
            }
            queryController.cleanup();
        }
    }

    /**
     * Hydrates an instantiated entity.  The instantiated entity must have the primary key defined and partition key if the data is partitioned.
     * All relationships are hydrated based on their fetch policy.
     * The entity must also not be null.
     *
     * @param entity Entity to hydrate.
     * @return Managed Entity
     * @throws OnyxException Error when hydrating entity
     * @since 1.0.0
     */
    @Override
    public <E extends IManagedEntity> E find(IManagedEntity entity) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);


        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        IManagedEntity results = recordController.get(entity);

        if (results == null) {
            throw new NoResultsException();
        }

        RelationshipHelper.hydrateAllRelationshipsForEntity(results, new EntityRelationshipManager(), context);

        ReflectionUtil.copy(results, entity, descriptor);

        //noinspection unchecked
        return (E)entity;
    }

    /**
     * Find Entity By Class and ID.
     * <p>
     * All relationships are hydrated based on their fetch policy.  This does not take into account the partition.
     *
     * @param clazz Managed Entity Type.  This must be a cast of IManagedEntity
     * @param id    Primary Key of entity
     * @return Managed Entity
     * @throws OnyxException Error when finding entity
     * @since 1.0.0
     */
    @Override
    public <E extends IManagedEntity> E findById(Class clazz, Object id) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        IManagedEntity entity = ReflectionUtil.createNewEntity(clazz);
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, "");
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        entity = recordController.getWithId(id);

        if (entity == null) {
            return null;
        }

        RelationshipHelper.hydrateAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

        //noinspection unchecked
        return (E)entity;
    }

    /**
     * Find Entity By Class and ID.
     * <p>
     * All relationships are hydrated based on their fetch policy.  This does not take into account the partition.
     *
     * @param clazz       Managed Entity Type.  This must be a cast of IManagedEntity
     * @param id          Primary Key of entity
     * @param partitionId Partition key for entity
     * @return Managed Entity
     * @throws OnyxException Error when finding entity within partition specified
     * @since 1.0.0
     */
    @Override
    public <E extends IManagedEntity> E findByIdInPartition(Class clazz, Object id, Object partitionId) throws OnyxException {

        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        IManagedEntity entity = ReflectionUtil.createNewEntity(clazz);
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionId);
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        entity = recordController.getWithId(id);

        if (entity == null) {
            return null;
        }

        RelationshipHelper.hydrateAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

        //noinspection unchecked
        return (E)entity;
    }

    /**
     * Determines if the entity exists within the database.
     * <p>
     * It is determined by the primary id and partition key
     *
     * @param entity Managed Entity to check
     * @return Returns true if the entity primary key exists. Otherwise it returns false
     * @throws OnyxException Error when finding entity within partition specified
     * @since 1.0.0
     */
    @Override
    public boolean exists(IManagedEntity entity) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        return recordController.exists(entity);
    }

    /**
     * Determines if the entity exists within the database.
     * <p>
     * It is determined by the primary id and partition key
     *
     * @param entity      Managed Entity to check
     * @param partitionId Partition Value for entity
     * @return Returns true if the entity primary key exists. Otherwise it returns false
     * @throws OnyxException Error when finding entity within partition specified
     * @since 1.0.0
     */
    @Override
    public boolean exists(IManagedEntity entity, Object partitionId) throws OnyxException {
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
     * @param entity    Managed Entity to attach relationship values
     * @param attribute String representation of relationship attribute
     * @throws OnyxException Error when hydrating relationship.  The attribute must exist and be a relationship.
     * @since 1.0.0
     */
    @Override
    public void initialize(IManagedEntity entity, String attribute) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);

        final Object identifier = AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier());
        RelationshipReference entityId;
        Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, context);

        if (partitionValue != PartitionHelper.NULL_PARTITION && partitionValue != null) {
            SystemPartitionEntry partitionEntry = context.getPartitionWithValue(descriptor.getEntityClass(), PartitionHelper.getPartitionFieldValue(entity, context));
            entityId = new RelationshipReference(identifier, partitionEntry.getIndex());
        } else {
            entityId = new RelationshipReference(identifier, 0);
        }

        RelationshipDescriptor relationshipDescriptor = descriptor.getRelationships().get(attribute);

        if (relationshipDescriptor == null) {
            throw new RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, attribute, entity.getClass().getName());
        }

        RelationshipController relationshipController = context.getRelationshipController(relationshipDescriptor);
        relationshipController.hydrateRelationshipForEntity(entityId, entity, new EntityRelationshipManager(), true);
    }

    /**
     * Hydrate a relationship and return the key
     *
     * @param entity    Managed Entity to attach relationship values
     * @param attribute String representation of relationship attribute
     * @throws OnyxException Error when hydrating relationship.  The attribute must exist and be a relationship.
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public Object findRelationship(IManagedEntity entity, String attribute) throws OnyxException {
        return getRelationship(entity, attribute);
    }

    /**
     * Provides a list of all entities with a given type
     *
     * @param clazz Type of managed entity to retrieve
     * @return Unsorted List of all entities with type
     * @throws OnyxException Exception occurred while fetching results
     */
    @Override
    public <E extends IManagedEntity> List<E> list(Class clazz) throws OnyxException {
        final EntityDescriptor descriptor = context.getBaseDescriptorForEntity(clazz);

        // Get the class' identifier and add a simple criteria to ensure the identifier is not null.  This should return all records.
        QueryCriteria criteria = new QueryCriteria(descriptor.getIdentifier().getName(), QueryCriteriaOperator.NOT_NULL);
        return list(clazz, criteria);
    }

    /**
     * This is a way to batch save all relationships for an entity.  This does not retain any existing relationships and will
     * overwrite all existing with the set you are sending in.  This is useful to optimize batch saving entities with relationships.
     *
     * @param entity                  Parent Managed Entity
     * @param relationship            Relationship attribute
     * @param relationshipIdentifiers Existing relationship identifiers
     * @throws OnyxException Error occurred while saving relationship.
     * @since 1.0.0
     */
    public void saveRelationshipsForEntity(IManagedEntity entity, String relationship, Set<Object> relationshipIdentifiers) throws OnyxException {
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final RelationshipDescriptor relationshipDescriptor = descriptor.getRelationships().get(relationship);

        if (relationshipDescriptor == null) {
            throw new RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, relationship, entity.getClass().getName());
        }

        Set<RelationshipReference> references = new HashSet<>();
        for (Object identifier : relationshipIdentifiers) {
            if (identifier instanceof RelationshipReference) {
                references.add((RelationshipReference) identifier);
            } else {
                references.add(new RelationshipReference(identifier, 0));
            }
        }
        final RelationshipController relationshipController = context.getRelationshipController(relationshipDescriptor);
        relationshipController.updateAll(entity, references);
    }

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws OnyxException The reference does not exist for that type
     * @since 1.0.0
     */
    public <E extends IManagedEntity> E getWithReferenceId(Class entityType, long referenceId) throws OnyxException {

        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        IManagedEntity entity = ReflectionUtil.createNewEntity(entityType);
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, "");
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        entity = recordController.getWithReferenceId(referenceId);

        RelationshipHelper.hydrateAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

        //noinspection unchecked
        return (E)entity;
    }

    /**
     * Get an entity by its partition reference.  This is the same as the method above but for objects that have
     * a reference as part of a partition.  An example usage would be in LazyQueryCollection so that it may
     * hydrate objects in random partitions.
     *
     * @param entityType         Type of managed entity
     * @param partitionReference Partition reference holding both the partition id and reference id
     * @param <E>                The managed entity implementation class
     * @return Managed Entity
     * @throws OnyxException The reference does not exist for that type
     */
    @Override
    @SuppressWarnings("unchecked")
    public <E extends IManagedEntity> E getWithPartitionReference(Class entityType, PartitionReference partitionReference) throws OnyxException {

        final SystemPartitionEntry partitionEntry = context.getPartitionWithId((partitionReference).partition);
        if (partitionEntry == null)
            return null;
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entityType, partitionEntry.getValue());
        final RecordController recordController = context.getRecordController(descriptor);
        return (E) recordController.getWithReferenceId(partitionReference.reference);
    }

    /**
     * Retrieves an entity using the primaryKey and partition
     *
     * @param clazz       Entity Type
     * @param id          Entity Primary Key
     * @param partitionId - Partition Identifier.  Not to be confused with partition key.  This is a unique id within the partition System table
     * @return Managed Entity
     * @throws OnyxException error occurred while attempting to retrieve entity.
     * @since 1.0.0
     */
    public <E extends IManagedEntity> E findByIdWithPartitionId(Class clazz, Object id, long partitionId) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        IManagedEntity entity = ReflectionUtil.createNewEntity(clazz);
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, "");
        final PartitionContext partitionContext = new PartitionContext(context, descriptor);
        final RecordController recordController = partitionContext.getRecordControllerForPartition(partitionId);

        // Find the object
        entity = recordController.getWithId(id);

        RelationshipHelper.hydrateAllRelationshipsForEntity(entity, new EntityRelationshipManager(), context);

        //noinspection unchecked
        return (E)entity;
    }

    /**
     * Execute query and delete entities returned in the results
     *
     * @param query Query used to filter entities with criteria
     * @return Number of entities deleted
     * @throws OnyxException Exception occurred while executing delete query
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public QueryResult executeDeleteForResults(Query query) throws OnyxException {
        return new QueryResult(query, this.executeDelete(query));
    }

    /**
     * Updates all rows returned by a given query
     * <p>
     * The query#updates list must not be null or empty
     *
     * @param query Query used to filter entities with criteria
     * @return Number of entities updated
     * @throws OnyxException Exception occurred while executing update query
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public QueryResult executeUpdateForResults(Query query) throws OnyxException {
        return new QueryResult(query, this.executeUpdate(query));
    }

    /**
     * Execute query with criteria and optional row limitations
     *
     * @param query Query containing criteria
     * @return Query Results
     * @throws OnyxException Error while executing query
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public QueryResult executeQueryForResults(Query query) throws OnyxException {
        return new QueryResult(query, this.executeQuery(query));
    }

    /**
     * Execute query with criteria and optional row limitations.  Specify lazy instantiation of query results.
     *
     * @param query Query containing criteria
     * @return LazyQueryCollection lazy loaded results
     * @throws OnyxException Error while executing query
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public QueryResult executeLazyQueryForResults(Query query) throws OnyxException {
        return new QueryResult(query, this.executeLazyQuery(query));
    }

    /**
     * Get Map representation of an entity with reference id
     *
     * @param entityType Original type of entity
     * @param reference  Reference location within a data structure
     * @return Map of key key pair of the entity.  Key being the attribute name.
     */
    public Map getMapWithReferenceId(Class entityType, long reference) throws OnyxException {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        IManagedEntity entity = ReflectionUtil.createNewEntity(entityType);
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, "");
        final RecordController recordController = context.getRecordController(descriptor);

        // Find the object
        return recordController.getMapWithReferenceId(reference);
    }

    /**
     * Retrieve the quantity of entities that match the query criterium.
     * <p>
     * usage:
     * <p>
     * Query myQuery = new Query();
     * myQuery.setClass(SystemEntity.class);
     * long numberOfSystemEntities = persistenceManager.countForQuery(myQuery);
     * <p>
     * or:
     * <p>
     * Query myQuery = new Query(SystemEntity.class, new QueryCriteria("primaryKey", QueryCriteriaOperator.GREATER_THAN, 3));
     * long numberOfSystemEntitiesWithIdGt3 = persistenceManager.countForQuery(myQuery);
     *
     * @param query The query to apply to the count operation
     * @return The number of entities that meet the query criterium
     * @throws OnyxException Error during query.
     * @since 1.3.0 Implemented with feature request #71
     */
    @Override
    public long countForQuery(Query query) throws OnyxException {

        final Class clazz = query.getEntityType();

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(clazz, query.getPartition());
        ValidationHelper.validateQuery(descriptor, query, context);

        CachedResults cachedResults = context.getQueryCacheController().getCachedQueryResults(query);
        if (cachedResults != null && cachedResults.getReferences() != null)
            return cachedResults.getReferences().size();

        final PartitionQueryController queryController = new PartitionQueryController(descriptor, this, context);

        try {
            return queryController.getCountForQuery(query);
        } finally {
            queryController.cleanup();
        }
    }

    /**
     * This method is used for bulk streaming data entities.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     *
     * @param query    Query to execute and stream
     * @param streamer Instance of the streamer to use to stream the data
     * @since 1.0.0
     */
    @Override
    @SuppressWarnings("unchecked")
    public void stream(Query query, QueryStream streamer) throws OnyxException {
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
     * @param query            Query to execute and stream
     * @param queryStreamClass Class instance of the database stream
     * @since 1.0.0
     */
    @Override
    public void stream(Query query, Class queryStreamClass) throws OnyxException {
        QueryStream streamer;
        try {
            streamer = (QueryStream) queryStreamClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new StreamException(StreamException.CANNOT_INSTANTIATE_STREAM);
        }
        this.stream(query, streamer);
    }

    /**
     * Un-register a query listener.  This will remove the listener from observing changes for that query.
     * If you do not un-register queries, they will not expire nor will they be de-registered autmatically.
     * This could cause performance degredation if removing the registration is neglected.
     *
     * @param query Query with a listener attached
     *
     * @throws OnyxException Un expected error when attempting to unregister listener
     *
     * @since 1.3.0 Added query subscribers as an enhancement.
     */
    public boolean removeChangeListener(Query query) throws OnyxException
    {
        final Class clazz = query.getEntityType();

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(clazz, query.getPartition());
        ValidationHelper.validateQuery(descriptor, query, context);

        return context.getQueryCacheController().unsubscribe(query);
    }

    /**
     * Listen to a query and register its subscriber
     *
     * @param query Query with query listener
     * @since 1.3.1
     */
    @Override
    public void listen(Query query) throws OnyxException {
        final Class clazz = query.getEntityType();

        // We want to lock the index controller so that it does not do background indexing
        final EntityDescriptor descriptor = context.getDescriptorForEntity(clazz, query.getPartition());
        ValidationHelper.validateQuery(descriptor, query, context);

        context.getQueryCacheController().subscribe(query);
    }

}
