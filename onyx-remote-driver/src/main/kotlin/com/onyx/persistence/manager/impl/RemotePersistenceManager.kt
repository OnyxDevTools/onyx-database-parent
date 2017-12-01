package com.onyx.persistence.manager.impl

import com.onyx.network.push.PushRegistrar
import com.onyx.exception.OnyxException
import com.onyx.exception.StreamException
import com.onyx.extension.copy
import com.onyx.extension.set
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.RemoteQueryListener
import com.onyx.persistence.stream.QueryStream

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
 *
 * PersistenceManagerFactory factory = new RemotePersistenceManagerFactory("onx://23.234.25.23:8080");
 * factory.setCredentials("username", "password");
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 * factory.close(); //Close the remote database
 *
 * or.. Kotlin
 *
 * val factory = RemotePersistenceManagerFactory("onx://23.234.25.23:8080")
 * factory.initialize()
 *
 * val persistenceManager = factory.persistenceManager
 * factory.close()
 *
 * @see com.onyx.persistence.manager.PersistenceManager
 *
 * Tim Osborn - 02/13/2017 This was augmented to use the new RMI Server.  Also, simplified
 * so that we take advantage of default methods within the PersistenceManager interface.
 */
open class RemotePersistenceManager : PersistenceManager {

    override lateinit var context: SchemaContext
    private lateinit var proxy: PersistenceManager
    private lateinit var pushRegistrar: PushRegistrar

    constructor()

    /**
     * Default Constructor.  This should be invoked by the persistence manager factory
     *
     * @since 1.1.0
     * @param persistenceManager Proxy Persistence manager on server
     */
    constructor(persistenceManager: PersistenceManager, pushRegistrar: PushRegistrar) {
        this.proxy = persistenceManager
        this.pushRegistrar = pushRegistrar
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
     * @throws OnyxException Exception occurred while persisting an entity
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E : IManagedEntity> saveEntity(entity: E): E {
        val results = proxy.saveEntity<IManagedEntity>(entity)
        entity.copy(results, context)
        return entity
    }

    /**
     * Batch saves a list of entities.
     *
     * The entities must all be of the same type
     *
     * @since 1.0.0
     * @param entities List of entities
     * @throws OnyxException Exception occurred while saving an entity within the list.  This will not roll back preceding saves if error occurs.
     */
    @Throws(OnyxException::class)
    override fun saveEntities(entities: List<IManagedEntity>) {
        if (entities.isNotEmpty()) {
            proxy.saveEntities(entities)
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
     * @throws OnyxException Error occurred while deleting
     */
    @Throws(OnyxException::class)
    override fun deleteEntity(entity: IManagedEntity): Boolean = proxy.deleteEntity(entity)

    /**
     * Deletes list of entities.
     *
     * The entities must exist otherwise it will throw an exception.  This will cascade delete relationships and remove index references.
     *
     * Requires all of the entities to be of the same type
     *
     * @since 1.0.0
     * @param entities List of entities
     * @throws OnyxException Error occurred while deleting.  If exception is thrown, preceding entities will not be rolled back
     */
    @Throws(OnyxException::class)
    override fun deleteEntities(entities: List<IManagedEntity>) = proxy.deleteEntities(entities)

    /**
     * Execute query with criteria and optional row limitations
     *
     * @since 1.0.0
     *
     * @param query Query containing criteria
     *
     * @return Query Results
     *
     * @throws OnyxException Error while executing query
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E> executeQuery(query: Query): List<E> {
        // Transform the change listener to a remote change listener.
        if (query.changeListener != null && query.changeListener !is RemoteQueryListener<*>) {
            // Register the query listener as a push subscriber / receiver
            val remoteQueryListener = RemoteQueryListener(query.changeListener)
            this.pushRegistrar.register(remoteQueryListener, remoteQueryListener)
            query.changeListener = remoteQueryListener
        }

        val result = proxy.executeQueryForResult(query)
        query.resultsCount = result.query!!.resultsCount

        return result.results as List<E>
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
     * @throws OnyxException Error while executing query
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E : IManagedEntity> executeLazyQuery(query: Query): List<E> {
        val result = proxy.executeLazyQueryForResult(query)
        query.resultsCount = result.query!!.resultsCount

        return result.results as List<E>
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
     * @throws OnyxException Exception occurred while executing update query
     *
     * @return Number of entities updated
     */
    @Throws(OnyxException::class)
    override fun executeUpdate(query: Query): Int {
        val result = proxy.executeUpdateForResult(query)
        query.resultsCount = result.query!!.resultsCount
        return result.results as Int
    }

    /**
     * Execute query and delete entities returned in the results
     *
     * @since 1.0.0
     *
     * @param query Query used to filter entities with criteria
     *
     * @throws OnyxException Exception occurred while executing delete query
     *
     * @return Number of entities deleted
     */
    @Throws(OnyxException::class)
    override fun executeDelete(query: Query): Int {
        val result = proxy.executeDeleteForResult(query)
        query.resultsCount = result.query!!.resultsCount
        return result.results as Int
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
     * @throws OnyxException Error when hydrating entity
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E : IManagedEntity> find(entity: IManagedEntity): E {
        val results = proxy.find<IManagedEntity>(entity)
        entity.copy(results, context)

        return entity as E
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
     * @throws OnyxException Error when finding entity
     */
    @Throws(OnyxException::class)
    override fun <E : IManagedEntity> findById(clazz: Class<*>, id: Any): E? = proxy.findById(clazz, id)

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
     * @throws OnyxException Error when finding entity within partition specified
     */
    @Throws(OnyxException::class)
    override fun <E : IManagedEntity> findByIdInPartition(clazz: Class<*>, id: Any, partitionId: Any): E? = proxy.findByIdInPartition(clazz, id, partitionId)

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
     * @throws OnyxException Error when finding entity within partition specified
     */
    @Throws(OnyxException::class)
    override fun exists(entity: IManagedEntity): Boolean = proxy.exists(entity)

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
     * @throws OnyxException Error when finding entity within partition specified
     */
    @Throws(OnyxException::class)
    override fun exists(entity: IManagedEntity, partitionId: Any): Boolean = proxy.exists(entity, partitionId)

    /**
     * Provides a list of all entities with a given type
     *
     * @param clazz  Type of managed entity to retrieve
     *
     * @return Unsorted List of all entities with type
     *
     * @throws OnyxException Exception occurred while fetching results
     */
    @Throws(OnyxException::class)
    override fun <E : IManagedEntity> list(clazz: Class<*>): List<E> {
        val descriptor = context.getBaseDescriptorForEntity(clazz)
        val criteria = QueryCriteria(descriptor!!.identifier!!.name, QueryCriteriaOperator.NOT_NULL)

        return proxy.list(clazz, criteria)
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
     * @throws OnyxException Error when hydrating relationship.  The attribute must exist and be a relationship.
     */
    @Throws(OnyxException::class)
    override fun initialize(entity: IManagedEntity, attribute: String) {
        val relationship:Any? = proxy.getRelationship(entity, attribute)
        entity.set(context = context, name = attribute, value = relationship)
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
     * @throws OnyxException Error occurred while saving relationship.
     */
    @Throws(OnyxException::class)
    override fun saveRelationshipsForEntity(entity: IManagedEntity, relationship: String, relationshipIdentifiers: Set<Any>) = proxy.saveRelationshipsForEntity(entity, relationship, relationshipIdentifiers)

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
    override fun <E : IManagedEntity> getWithReference(entityType: Class<*>, reference: Reference): E? = proxy.getWithReference(entityType, reference)

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
     * @throws OnyxException error occurred while attempting to retrieve entity.
     */
    @Throws(OnyxException::class)
    override fun <E : IManagedEntity?> findByIdWithPartitionId(clazz: Class<*>, id: Any, partitionId: Long): E = proxy.findByIdWithPartitionId<E>(clazz, id, partitionId)

    /**
     * This method is used for bulk streaming data entities.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     *
     * @since 1.0.0
     *
     * @param query Query to execute and stream
     *
     * @param streamer Instance of the streamer to use to stream the data
     */
    @Throws(OnyxException::class)
    override fun <T : Any> stream(query: Query, streamer: QueryStream<T>) = throw StreamException(StreamException.UNSUPPORTED_FUNCTION_ALTERNATIVE)


    /**
     * This method is used for bulk streaming.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     *
     * @since 1.0.0
     *
     * @param query Query to execute and stream
     *
     * @param queryStreamClass Class instance of the database stream
     */
    @Throws(OnyxException::class)
    override fun stream(query: Query, queryStreamClass: Class<*>) = proxy.stream(query, queryStreamClass)

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
    @Throws(OnyxException::class)
    override fun getMapWithReferenceId(entityType: Class<*>, reference: Reference): Map<String, *>? = proxy.getMapWithReferenceId(entityType, reference)

    /**
     * Retrieve the quantity of entities that match the query criteria.
     *
     *
     * usage:
     *
     *
     * Query myQuery = new Query();
     * myQuery.setClass(SystemEntity.class);
     * long numberOfSystemEntities = persistenceManager.countForQuery(myQuery);
     *
     *
     * or:
     *
     *
     * Query myQuery = new Query(SystemEntity.class, new QueryCriteria("primaryKey", QueryCriteriaOperator.GREATER_THAN, 3));
     * long numberOfSystemEntitiesWithIdGt3 = persistenceManager.countForQuery(myQuery);
     *
     * @param query The query to apply to the count operation
     * @return The number of entities that meet the query criterion
     * @throws OnyxException Error during query.
     * @since 1.3.0 Implemented with feature request #71
     */
    @Throws(OnyxException::class)
    override fun countForQuery(query: Query): Long = proxy.countForQuery(query)

    /**
     * Un-register a query listener.  This will remove the listener from observing changes for that query.
     * If you do not un-register queries, they will not expire nor will they be de-registered automatically.
     * This could cause performance degrading if removing the registration is neglected.
     *
     * These will eventually be cleared out by the server when it detects connections have been dropped but,
     * it is better to be pro-active about it.
     *
     * @param query Query with a listener attached
     *
     * @throws OnyxException Un expected error when attempting to unregister listener
     *
     * @since 1.3.0 Added query subscribers as an enhancement.
     */
    @Throws(OnyxException::class)
    override fun removeChangeListener(query: Query): Boolean {

        // Ensure the original change listener is attached and is a remote query listener
        if (query.changeListener != null && query.changeListener is RemoteQueryListener<*>) {
            // Un-register query
            val retVal = proxy.removeChangeListener(query)
            val remoteQueryListener = query.changeListener as RemoteQueryListener<*>
            this.pushRegistrar.unregister(remoteQueryListener)
            return retVal
        }
        return false
    }

    /**
     * Listen to a query and register its subscriber
     *
     * @param query Query with query listener
     * @since 1.3.1
     */
    @Throws(OnyxException::class)
    override fun listen(query: Query) {
        // Register the query listener as a push subscriber / receiver
        val remoteQueryListener = RemoteQueryListener(query.changeListener)
        this.pushRegistrar.register(remoteQueryListener, remoteQueryListener)
        query.changeListener = remoteQueryListener

        proxy.listen(query)
    }

}
