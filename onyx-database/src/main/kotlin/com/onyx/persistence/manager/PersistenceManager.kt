package com.onyx.persistence.manager

import com.onyx.exception.OnyxException
import com.onyx.extension.get
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.*
import com.onyx.persistence.query.QueryListener
import com.onyx.persistence.stream.QueryStream
import com.onyx.util.ReflectionUtil
import java.util.*

/**
 * Persistence manager supplies a public API for performing database persistence and querying operations.
 *
 *
 * @author Tim Osborn
 * @author Chris Osborn
 * @since 1.0.0
 *
 * PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory("/MyDatabaseLocation");
 * factory.setCredentials("username", "password");
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 * factory.close(); //Close the in memory database
 *
 * or... Kotlin
 *
 * val factory = EmbeddedPersistenceManagerFactory("/MyDatabaseLocation")
 * factory.initialize()
 *
 * val manager = factory.persistenceManager
 *
 * factory.close()
 *
 */
interface PersistenceManager {

    /**
     * The context of the database contains descriptor information regarding the entities and instruction on how to structure the record data.  This is usually done within the PersistenceManagerFactory.
     *
     * @since 1.0.0
     */
    var context: SchemaContext

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
    fun <E : IManagedEntity> saveEntity(entity: IManagedEntity): E

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
    fun saveEntities(entities: List<IManagedEntity>)

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
    fun deleteEntity(entity: IManagedEntity): Boolean

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
    fun deleteEntities(entities: List<IManagedEntity>) = entities.forEach { deleteEntity(it) }

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
    fun executeDelete(query: Query): Int

    /**
     * Execute a delete query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws OnyxException Exception when deleting entities
     */
    @Throws(OnyxException::class)
    fun executeDeleteForResult(query: Query): QueryResult = QueryResult(query, executeDelete(query))

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
    fun executeUpdate(query: Query): Int

    /**
     * Execute an update query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws OnyxException when an update query failed
     */
    @Throws(OnyxException::class)
    fun executeUpdateForResult(query: Query): QueryResult = QueryResult(query, executeUpdate(query))

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
    fun <E> executeQuery(query: Query): List<E>

    /**
     * Execute a query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws OnyxException when the query is mal formed or general exception
     */
    @Throws(OnyxException::class)
    fun executeQueryForResult(query: Query): QueryResult = QueryResult(query, executeQuery<Any>(query))

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
    fun <E : IManagedEntity> executeLazyQuery(query: Query): List<E>

    /**
     * Execute a lazy query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws OnyxException General exception happened when the query.
     */
    @Throws(OnyxException::class)
    fun executeLazyQueryForResult(query: Query): QueryResult = QueryResult(query, executeLazyQuery<IManagedEntity>(query))

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
    fun <E : IManagedEntity> find(entity: IManagedEntity): E

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
    fun <E : IManagedEntity> findById(clazz: Class<*>, id: Any): E?

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
    fun <E : IManagedEntity> findByIdInPartition(clazz: Class<*>, id: Any, partitionId: Any): E?

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
    fun exists(entity: IManagedEntity): Boolean

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
    fun exists(entity: IManagedEntity, partitionId: Any): Boolean

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
    fun initialize(entity: IManagedEntity, attribute: String)

    /**
     * Get relationship for an entity
     *
     * @param entity The entity to load
     * @param attribute Attribute that represents the relationship
     * @return The relationship Value
     * @throws OnyxException Error when hydrating relationship.  The attribute must exist and must be a annotated with a relationship
     * @since 1.2.0
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    fun <T : Any?> getRelationship(entity: IManagedEntity, attribute: String): T {
        initialize(entity, attribute)
        return entity.get<T>(context = context, name = attribute) as T
    }

    /**
     * Provides a list of all entities with a given type
     *
     * @param clazz Type of managed entity to retrieve
     * @return Unsorted List of all entities with type
     * @throws OnyxException Exception occurred while fetching results
     */
    @Throws(OnyxException::class)
    fun <E : IManagedEntity> list(clazz: Class<*>): List<E> {
        val descriptor = context.getBaseDescriptorForEntity(clazz)

        // Get the class' identifier and add a simple criteria to ensure the identifier is not null.  This should return all records.
        val criteria = QueryCriteria<Nothing>(descriptor!!.identifier!!.name, QueryCriteriaOperator.NOT_NULL)
        return list(clazz, criteria)
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
     * @throws OnyxException Exception occurred while filtering results
     */
    @Throws(OnyxException::class)
    fun <E : IManagedEntity> list(clazz: Class<*>, criteria: QueryCriteria<*>): List<E> = list(clazz, criteria, arrayOf())

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
     * @throws OnyxException Exception occurred while filtering results
     */
    @Throws(OnyxException::class)
    fun <E : IManagedEntity> list(clazz: Class<*>, criteria: QueryCriteria<*>, orderBy: Array<QueryOrder>): List<E> = list(clazz, criteria, 0, -1, orderBy)

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
     * @throws OnyxException Exception occurred while filtering results
     */
    @Throws(OnyxException::class)
    fun <E : IManagedEntity> list(clazz: Class<*>, criteria: QueryCriteria<*>, orderBy: QueryOrder): List<E> {
        val queryOrders = arrayOf(orderBy)
        return list(clazz, criteria, queryOrders)
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
     * @throws OnyxException Exception occurred while filtering results
     */
    @Throws(OnyxException::class)
    fun <E : IManagedEntity> list(clazz: Class<*>, criteria: QueryCriteria<*>, partitionId: Any): List<E> = list(clazz, criteria, arrayOf(), partitionId)

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
     * @throws OnyxException Exception occurred while filtering results
     */
    @Throws(OnyxException::class)
    fun <E : IManagedEntity> list(clazz: Class<*>, criteria: QueryCriteria<*>, orderBy: Array<QueryOrder>, partitionId: Any): List<E> = list(clazz, criteria, 0, -1, orderBy, partitionId)

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
     * @throws OnyxException Exception occurred while filtering results
     */
    @Throws(OnyxException::class)
    fun <E : IManagedEntity> list(clazz: Class<*>, criteria: QueryCriteria<*>, orderBy: QueryOrder, partitionId: Any): List<E> {
        val queryOrders = arrayOf(orderBy)
        return list(clazz, criteria, queryOrders, partitionId)
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
     * @throws OnyxException Exception occurred while filtering results
     */
    @Throws(OnyxException::class)
    fun <E : IManagedEntity> list(clazz: Class<*>, criteria: QueryCriteria<*>, start: Int, maxResults: Int, orderBy: Array<QueryOrder>?): List<E> {
        val tmpQuery = Query(clazz, criteria)
        tmpQuery.maxResults = maxResults
        tmpQuery.firstRow = start
        if (orderBy != null) {
            tmpQuery.queryOrders = Arrays.asList(*orderBy)
        }
        return executeQuery(tmpQuery)
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
     * @throws OnyxException Exception occurred while filtering results
     */
    @Throws(OnyxException::class)
    fun <E : IManagedEntity> list(clazz: Class<*>, criteria: QueryCriteria<*>, start: Int, maxResults: Int, orderBy: Array<QueryOrder>?, partitionId: Any): List<E> {

        val tmpQuery = Query(clazz, criteria)
        tmpQuery.partition = partitionId
        tmpQuery.maxResults = maxResults
        tmpQuery.firstRow = start
        if (orderBy != null) {
            tmpQuery.queryOrders = Arrays.asList(*orderBy)
        }

        return executeQuery(tmpQuery)
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
    fun saveRelationshipsForEntity(entity: IManagedEntity, relationship: String, relationshipIdentifiers: Set<Any>)

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @since 1.0.0
     * @param entityType Type of managed entity
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws OnyxException The reference does not exist for that type
     */
    @Throws(OnyxException::class)
    @Deprecated("Should use entire reference")
    fun <E : IManagedEntity> getWithReferenceId(entityType: Class<*>, referenceId: Long): E?

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
    </E> */
    @Throws(OnyxException::class)
    fun <E : IManagedEntity> getWithReference(entityType: Class<*>, reference: Reference): E?

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
    fun <E : IManagedEntity?> findByIdWithPartitionId(clazz: Class<*>, id: Any, partitionId: Long): E

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
    fun <T : Any> stream(query: Query, streamer: QueryStream<T>)

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
    fun stream(query: Query, queryStreamClass: Class<*>)

    /**
     * Get Map representation of an entity with reference id
     *
     * @param entityType Original type of entity
     *
     * @param reference Reference location within a data structure
     *
     * @return Map of key key pair of the entity.  Key being the attribute name.
     */
    @Throws(OnyxException::class)
    fun getMapWithReferenceId(entityType: Class<*>, reference: Reference): Map<String, *>?

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
     * @return The number of entities that meet the query criteria
     * @throws OnyxException Error during query.
     * @since 1.3.0 Implemented with feature request #71
     */
    @Throws(OnyxException::class)
    fun countForQuery(query: Query): Long

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
    fun removeChangeListener(query: Query): Boolean

    /**
     * Listen to a query and register its subscriber
     *
     * @param query Query with query listener
     * @since 1.3.1
     */
    @Throws(OnyxException::class)
    fun listen(query: Query)

    /**
     * Listen to a query and register its subscriber
     *
     * @param query Query without query listener
     * @param queryListener listener to invoke for changes
     * @since 1.3.1
     */
    @Throws(OnyxException::class)
    fun listen(query: Query, queryListener: QueryListener<*>) {
        query.changeListener = queryListener
        listen(query)
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
    fun executeLazyQueryForResults(query: Query): QueryResult = QueryResult(query, this.executeLazyQuery<IManagedEntity>(query = query))

    /**
     * Hydrate a relationship and return the key
     *
     * @param entity    Managed Entity to attach relationship values
     * @param attribute String representation of relationship attribute
     * @throws OnyxException Error when hydrating relationship.  The attribute must exist and be a relationship.
     * @since 1.0.0
     */
    @Throws(OnyxException::class)
    fun findRelationship(entity: IManagedEntity, attribute: String): Any? = getRelationship(entity, attribute)
}
