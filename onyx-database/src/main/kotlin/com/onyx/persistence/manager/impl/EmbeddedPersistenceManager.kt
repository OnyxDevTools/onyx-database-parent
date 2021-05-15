package com.onyx.persistence.manager.impl

import com.onyx.exception.*
import com.onyx.extension.*
import com.onyx.extension.common.instance
import com.onyx.interactors.query.QueryCollector
import com.onyx.interactors.query.impl.DefaultQueryInteractor
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.*
import com.onyx.persistence.collections.LazyQueryCollection
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.interactors.relationship.data.RelationshipTransaction
import com.onyx.interactors.relationship.data.RelationshipReference
import com.onyx.persistence.query.QueryListenerEvent
import com.onyx.persistence.stream.QueryMapStream
import com.onyx.persistence.stream.QueryStream
import java.util.*

/**
 * Persistence manager supplies a public API for performing database persistence and querying operations.  This specifically is used for an embedded database.
 *
 * @author Tim Osborn
 * @see com.onyx.persistence.manager.PersistenceManager
 *
 * @since 1.0.0
 *
 *
 * PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
 * factory.setCredentials("username", "password");
 * factory.setLocation("/MyDatabaseLocation")
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 * factory.close(); //Close the in memory database
 *
 */
open class EmbeddedPersistenceManager(context: SchemaContext) : PersistenceManager {

    override var context: SchemaContext = context
        set(value) {
            field = value
            value.systemPersistenceManager = this
        }

    var isJournalingEnabled: Boolean = false

    /**
     * Save entity.  Persists a single entity for update or insert.  This method will cascade relationships and persist indexes.
     *
     * @param entity Managed Entity to Save
     * @return Saved Managed Entity
     * @throws OnyxException Exception occurred while persisting an entity
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun <E : IManagedEntity> saveEntity(entity: E): E {
        context.checkForKillSwitch()

        if(entity.isValid(context)) {
            val putResult = entity.save(context)

            journal {
                context.transactionInteractor.writeSave(entity)
            }

            entity.saveIndexes(context, if(putResult.isInsert) 0 else putResult.recordId, putResult.recordId)
            entity.saveRelationships(context)

            // Update Cached queries
            context.queryCacheInteractor.updateCachedQueryResultsForEntity(entity, entity.descriptor(context), entity.reference(putResult.recordId, context), if (putResult.isInsert) QueryListenerEvent.INSERT else QueryListenerEvent.UPDATE)

        }
        return entity
    }

    /**
     * Batch saves a list of entities.
     *
     *
     * The entities must all be of the same type
     *
     * @param entities List of entities
     * @throws OnyxException Exception occurred while saving an entity within the list.  This will not roll back preceding saves if error occurs.
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun saveEntities(entities: List<IManagedEntity>) {
        context.checkForKillSwitch()

        if (entities.isEmpty())
            return

        try {
            entities.forEach {
                if (it.isValid(context)) {
                    saveEntity(it)
                }
            }
        } catch (e:ClassCastException) {
            throw EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND)
        }
    }

    /**
     * Deletes a single entity
     *
     *
     * The entity must exist otherwise it will throw an exception.  This will cascade delete relationships and remove index references.
     *
     * @param entity Managed Entity to delete
     * @return Flag indicating it was deleted
     * @throws OnyxException Error occurred while deleting
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun deleteEntity(entity: IManagedEntity): Boolean {
        context.checkForKillSwitch()
        val descriptor = context.getDescriptorForEntity(entity)

        val previousReferenceId = entity.referenceId(context, descriptor)

        if(previousReferenceId > 0) {
            journal {
                context.transactionInteractor.writeDelete(entity)
            }
            entity.deleteAllIndexes(context, previousReferenceId, descriptor)
            entity.deleteRelationships(context)
            entity.recordInteractor(context, descriptor).delete(entity)
        }

        return previousReferenceId > 0
    }

    /**
     * Execute query and delete entities returned in the results
     *
     * @param query Query used to filter entities with criteria
     * @return Number of entities deleted
     * @throws OnyxException Exception occurred while executing delete query
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun executeDelete(query: Query): Int {
        context.checkForKillSwitch()

        // We want to lock the index controller so that it does not do background indexing
        val descriptor = context.getDescriptorForEntity(query.entityType, query.partition)

        query.isUpdateOrDelete = true
        query.validate(context, descriptor)
        val queryController = DefaultQueryInteractor(descriptor, this, context)

        val results:QueryCollector<IManagedEntity> = queryController.getReferencesForQuery(query)
        query.resultsCount = results.getNumberOfResults()

        journal {
            context.transactionInteractor.writeDeleteQuery(query)
        }

        return queryController.deleteRecordsWithReferences(results.references, query)
    }

    /**
     * Updates all rows returned by a given query
     *
     * The query#updates list must not be null or empty
     *
     * @param query Query used to filter entities with criteria
     * @return Number of entities updated
     * @throws OnyxException Exception occurred while executing update query
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun executeUpdate(query: Query): Int {
        context.checkForKillSwitch()

        // We want to lock the index controller so that it does not do background indexing
        val descriptor = context.getDescriptorForEntity(query.entityType, query.partition)
        query.isUpdateOrDelete = true
        query.validate(context, descriptor)

        val queryController = DefaultQueryInteractor(descriptor, this, context)

        val results:QueryCollector<IManagedEntity> = queryController.getReferencesForQuery(query)
        query.resultsCount = results.getNumberOfResults()

        journal {
            context.transactionInteractor.writeQueryUpdate(query)
        }

        return queryController.updateRecordsWithReferences(query, results.references)
    }

    /**
     * Execute query with criteria and optional row limitations
     *
     * @param query Query containing criteria
     * @return Query Results
     * @throws OnyxException Error while executing query
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E> executeQuery(query: Query): List<E> {
        context.checkForKillSwitch()

        val descriptor = context.getDescriptorForEntity(query.entityType, query.partition)
        query.validate(context, descriptor)

        val queryController = DefaultQueryInteractor(descriptor, this, context)
        val results:QueryCollector<E> = cache(query) { queryController.getReferencesForQuery(query) }
        return results.results as List<E>
    }

    /**
     * Execute query with criteria and optional row limitations.  Specify lazy instantiation of query results.
     *
     * @param query Query containing criteria
     * @return LazyQueryCollection lazy loaded results
     * @throws OnyxException Error while executing query
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E : IManagedEntity> executeLazyQuery(query: Query): List<E> {
        context.checkForKillSwitch()

        query.isLazy = true
        val descriptor = context.getDescriptorForEntity(query.entityType, query.partition)
        query.validate(context, descriptor)

        val queryController = DefaultQueryInteractor(descriptor, this, context)
        val results:QueryCollector<E> = cache(query) { queryController.getReferencesForQuery(query) }
        return LazyQueryCollection<IManagedEntity>(descriptor, results.getLimitedReferences(), context) as List<E>
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
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E : IManagedEntity> find(entity: IManagedEntity): E {
        context.checkForKillSwitch()

        val results = entity.recordInteractor(context)[entity] ?: throw NoResultsException()
        results.hydrateRelationships(context)
        entity.copy(results, context)
        return entity as E
    }

    /**
     * Find Entity By Class and ID.
     *
     *
     * All relationships are hydrated based on their fetch policy.  This does not take into account the partition.
     *
     * @param clazz Managed Entity Type.  This must be a cast of IManagedEntity
     * @param id    Primary Key of entity
     * @return Managed Entity
     * @throws OnyxException Error when finding entity
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E : IManagedEntity> findById(clazz: Class<*>, id: Any): E? {
        context.checkForKillSwitch()

        var entity: IManagedEntity? = clazz.createNewEntity()

        // Find the object
        entity = entity!!.recordInteractor(context).getWithId(id)
        entity?.hydrateRelationships(context)
        return entity as E?
    }

    /**
     * Find Entity By Class and ID.
     *
     *
     * All relationships are hydrated based on their fetch policy.  This does not take into account the partition.
     *
     * @param clazz       Managed Entity Type.  This must be a cast of IManagedEntity
     * @param id          Primary Key of entity
     * @param partitionId Partition key for entity
     * @return Managed Entity
     * @throws OnyxException Error when finding entity within partition specified
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E : IManagedEntity> findByIdInPartition(clazz: Class<*>, id: Any, partitionId: Any): E? {

        context.checkForKillSwitch()

        var entity: IManagedEntity? = clazz.createNewEntity()
        entity?.setPartitionValue(context = context, value = partitionId)

        // Find the object
        entity = entity!!.recordInteractor(context).getWithId(id)
        entity?.hydrateRelationships(context)

        return entity as E?
    }

    /**
     * Determines if the entity exists within the database.
     *
     *
     * It is determined by the primary id and partition key
     *
     * @param entity Managed Entity to check
     * @return Returns true if the entity primary key exists. Otherwise it returns false
     * @throws OnyxException Error when finding entity within partition specified
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun exists(entity: IManagedEntity): Boolean {
        context.checkForKillSwitch()
        return entity.recordInteractor(context).exists(entity)
    }

    /**
     * Determines if the entity exists within the database.
     *
     *
     * It is determined by the primary id and partition key
     *
     * @param entity      Managed Entity to check
     * @param partitionId Partition Value for entity
     * @return Returns true if the entity primary key exists. Otherwise it returns false
     * @throws OnyxException Error when finding entity within partition specified
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun exists(entity: IManagedEntity, partitionId: Any): Boolean {
        context.checkForKillSwitch()

        val descriptor = context.getDescriptorForEntity(entity, partitionId)
        val recordInteractor = context.getRecordInteractor(descriptor)

        return recordInteractor.exists(entity)
    }

    /**
     * Force Hydrate relationship based on attribute name
     *
     * @param entity    Managed Entity to attach relationship values
     * @param attribute String representation of relationship attribute
     * @throws OnyxException Error when hydrating relationship.  The attribute must exist and be a relationship.
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun initialize(entity: IManagedEntity, attribute: String) {
        context.checkForKillSwitch()

        val descriptor = context.getDescriptorForEntity(entity)
        val relationshipDescriptor = descriptor.relationships[attribute] ?: throw RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, attribute, entity.javaClass.name)
        val relationshipInteractor = context.getRelationshipInteractor(relationshipDescriptor)
        relationshipInteractor.hydrateRelationshipForEntity(entity, RelationshipTransaction(), true)
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
    @Throws(OnyxException::class)
    override fun saveRelationshipsForEntity(entity: IManagedEntity, relationship: String, relationshipIdentifiers: Set<Any>) {
        context.checkForKillSwitch()

        val relationships = context.getDescriptorForEntity(entity).relationships
        val relationshipDescriptor = relationships[relationship] ?: throw RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, relationship, entity.javaClass.name)
        val references = HashSet<RelationshipReference>()

        relationshipIdentifiers.forEach {
            if (it is RelationshipReference) {
                references.add(it)
            } else {
                references.add(RelationshipReference(it, 0))
            }
        }

        val relationshipInteractor = context.getRelationshipInteractor(relationshipDescriptor)
        relationshipInteractor.updateAll(entity, references)
    }

    /**
     * Get an entity by its partition reference.  This is the same as the method above but for objects that have
     * a reference as part of a partition.  An example usage would be in LazyQueryCollection so that it may
     * hydrate objects in random partitions.
     *
     * @param entityType         Type of managed entity
     * @param reference Partition reference holding both the partition id and reference id
     * @param <E>                The managed entity implementation class
     * @return Managed Entity
     * @throws OnyxException The reference does not exist for that type
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E : IManagedEntity> getWithReference(entityType: Class<*>, reference: Reference): E? {
        context.checkForKillSwitch()
        val managedEntity = reference.toManagedEntity(context, entityType)
        managedEntity?.hydrateRelationships(context)
        return managedEntity as E
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
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E : IManagedEntity?> findByIdWithPartitionId(clazz: Class<*>, id: Any, partitionId: Int): E {
        context.checkForKillSwitch()

        val entity = RelationshipReference(identifier = id, partitionId = partitionId).toManagedEntity(context, clazz)
        entity?.hydrateRelationships(context)

        return entity as E
    }

    /**
     * Get Map representation of an entity with reference id
     *
     * @param entityType Original type of entity
     * @param reference  Reference location within a data structure
     * @return Map of key key pair of the entity.  Key being the attribute name.
     */
    @Throws(OnyxException::class)
    override fun getMapWithReferenceId(entityType: Class<*>, reference: Reference): Map<String, *>? {
        context.checkForKillSwitch()
        return reference.recordInteractor(context, entityType).getMapWithReferenceId(reference.reference)
    }

    /**
     * Retrieve the quantity of entities that match the query criteria.
     *
     *
     * usage:
     *
     *
     * Query myQuery = new Query();
     * myQuery.setClass(SystemEntity.class);
     * int numberOfSystemEntities = persistenceManager.countForQuery(myQuery);
     *
     *
     * or:
     *
     *
     * Query myQuery = new Query(SystemEntity.class, new QueryCriteria("primaryKey", QueryCriteriaOperator.GREATER_THAN, 3));
     * int numberOfSystemEntitiesWithIdGt3 = persistenceManager.countForQuery(myQuery);
     *
     * @param query The query to apply to the count operation
     * @return The number of entities that meet the query criteria
     * @throws OnyxException Error during query.
     * @since 1.3.0 Implemented with feature request #71
     */
    @Throws(OnyxException::class)
    override fun countForQuery(query: Query): Int {
        context.checkForKillSwitch()

        val clazz = query.entityType

        // We want to lock the index controller so that it does not do background indexing
        val descriptor = context.getDescriptorForEntity(clazz, query.partition)
        query.validate(context, descriptor)

        val cachedResults = context.queryCacheInteractor.getCachedQueryResults(query)
        if (cachedResults?.references != null)
            return cachedResults.references!!.size

        val queryController = DefaultQueryInteractor(descriptor, this, context)
        return queryController.getCountForQuery(query)
    }

    /**
     * This method is used for bulk streaming data entities.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     *
     * @param query    Query to execute and stream
     * @param streamer Instance of the streamer to use to stream the data
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> stream(query: Query, streamer: QueryStream<T>) {
        context.checkForKillSwitch()
        val entityList = this.executeLazyQuery<IManagedEntity>(query) as LazyQueryCollection<IManagedEntity>

        entityList.forEachIndexed { index, iManagedEntity ->
            if (streamer is QueryMapStream) {
                (streamer as QueryStream<Map<String, Any?>>).accept(entityList.getDict(index) as Map<String, Any?>, this)
            } else {
                streamer.accept(iManagedEntity as T, this)
            }
        }
    }

    /**
     * This method is used for bulk streaming.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     *
     * @param query            Query to execute and stream
     * @param queryStreamClass Class instance of the database stream
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    override fun stream(query: Query, queryStreamClass: Class<*>) {
        context.checkForKillSwitch()
        val streamer:QueryStream<*> = try {
            queryStreamClass.instance()
        } catch (e: InstantiationException) {
            throw StreamException(StreamException.CANNOT_INSTANTIATE_STREAM)
        } catch (e: IllegalAccessException) {
            throw StreamException(StreamException.CANNOT_INSTANTIATE_STREAM)
        }

        this.stream(query, streamer)
    }

    /**
     * Un-register a query listener.  This will remove the listener from observing changes for that query.
     * If you do not un-register queries, they will not expire nor will they be de-registered automatically.
     * This could cause performance degradation if removing the registration is neglected.
     *
     * @param query Query with a listener attached
     *
     * @throws OnyxException Un expected error when attempting to unregister listener
     *
     * @since 1.3.0 Added query subscribers as an enhancement.
     */
    @Throws(OnyxException::class)
    override fun removeChangeListener(query: Query): Boolean {
        query.validate(context)
        return context.queryCacheInteractor.unSubscribe(query)
    }

    /**
     * Listen to a query and register its subscriber
     *
     * @param query Query with query listener
     * @since 1.3.1
     */
    @Throws(OnyxException::class)
    override fun listen(query: Query) {
        context.checkForKillSwitch()
        query.validate(context)
        context.queryCacheInteractor.subscribe(query)
    }

    /**
     * Run Journaling code if it is enabled
     *
     * @since 2.0.0 Added as a fancy unit
     */
    private fun journal(body:() -> Unit) {
        if(isJournalingEnabled)
            body.invoke()
    }

    /**
     * Cache query results from the closure.  If the query has already been cached, return the results
     * of the cache.
     *
     * @param query Query results to cache
     * @param body Closure to execute to retrieve the results of the query
     *
     * @since 2.0.0
     */
    private fun <E> cache(query: Query, body: () -> QueryCollector<E>) = context.queryCacheInteractor.cache(query, body)
}
