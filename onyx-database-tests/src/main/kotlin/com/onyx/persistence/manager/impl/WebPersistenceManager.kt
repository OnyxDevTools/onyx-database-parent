package com.onyx.persistence.manager.impl

import com.fasterxml.jackson.core.JsonProcessingException
import com.onyx.exception.*
import com.onyx.extension.common.setAny
import com.onyx.extension.copy
import com.onyx.extension.identifier
import com.onyx.extension.partitionValue
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.impl.WebSchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.request.pojo.*
import com.onyx.persistence.stream.QueryStream

import java.util.*

/**
 * Persistence manager supplies a public API for performing database persistence and querying operations.  This specifically is used for an Restful WEB API database.
 * Entities that are passed through these methods must be serializable using the Jackson JSON Serializer
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * `
 *
 * PersistenceManagerFactory factory = new RemotePersistenceManagerFactory();
 * factory.setCredentials("username", "password");
 * factory.setLocation("onx://23.234.25.23:8080")
 * factory.initialize();
 *
 * PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 * factory.close(); //Close the in memory database
 *
` *
</pre> *
 *
 * @see PersistenceManager
 */
@Suppress("UNCHECKED_CAST")
class WebPersistenceManager(override var context: SchemaContext) : AbstractWebPersistenceManager(), PersistenceManager {

    /**
     * Get Database URL
     * @return Database URL with Path
     */
    private val url: String
        get() = (context as WebSchemaContext).remoteEndpoint + WEB

    /**
     * Save entity.  Persists a single entity for update or insert.  This method will cascade relationships and persist indexes.
     *
     * @since 1.0.0
     *
     * @param entity Managed Entity to Save
     *
     * @return Saved Managed Entity
     *
     * @throws OnyxException Exception occured while persisting an entity
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T : IManagedEntity> saveEntity(entity: IManagedEntity): T {
        val body = EntityRequestBody()
        body.entity = entity
        body.type = entity.javaClass.name
        body.partitionId = entity.partitionValue(context)

        val results = this.performCall(url + AbstractWebPersistenceManager.SAVE, null, entity.javaClass, body) as IManagedEntity
        entity.copy(results, context)

        return entity as T
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
            val body = EntityListRequestBody()
            try {
                body.entities = objectMapper.writeValueAsString(entities)
            } catch (e: JsonProcessingException) {
                throw EntityClassNotFoundException(OnyxException.UNKNOWN_EXCEPTION)
            }

            try {
                body.type = entities[0].javaClass.name
            } catch (e: ClassCastException) {
                throw EntityClassNotFoundException(EntityClassNotFoundException.PERSISTED_NOT_FOUND)
            }

            this.performCall(url + AbstractWebPersistenceManager.BATCH_SAVE, null, Void::class.java, body)
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
    override fun deleteEntity(entity: IManagedEntity): Boolean {
        val body = EntityRequestBody()
        body.entity = entity
        body.type = entity.javaClass.name
        return this.performCall(url + AbstractWebPersistenceManager.DELETE, null, Boolean::class.java, body) as Boolean
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
     * @throws OnyxException Error occurred while deleting.  If exception is thrown, preceding entities will not be rolled back
     */
    @Throws(OnyxException::class)
    override fun deleteEntities(entities: List<IManagedEntity>) {
        if (entities.isNotEmpty()) {
            val body = EntityListRequestBody()
            try {
                body.entities = objectMapper.writeValueAsString(entities)
            } catch (e: JsonProcessingException) {
                throw EntityClassNotFoundException(OnyxException.UNKNOWN_EXCEPTION)
            }

            body.type = entities[0].javaClass.name
            this.performCall(url + AbstractWebPersistenceManager.BATCH_DELETE, null, null, body)
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
     * @throws OnyxException Error while executing query
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E> executeQuery(query: Query): List<E> {
        val body = EntityQueryBody()
        body.query = query

        if (query.selections != null && query.selections!!.isNotEmpty()) {
            val response = this.performCall(url + AbstractWebPersistenceManager.EXECUTE_QUERY, HashMap::class.java, QueryResultResponseBody::class.java, body) as QueryResultResponseBody
            query.resultsCount = response.maxResults
            return response.resultList as List<E>
        }

        val response = this.performCall(url + AbstractWebPersistenceManager.EXECUTE_QUERY, query.entityType, QueryResultResponseBody::class.java, body) as QueryResultResponseBody
        query.resultsCount = response.maxResults
        return response.resultList as List<E>
    }

    /**
     * Execute query with criteria and optional row limitations.  For RESTful Web Services this is not implemented.  This will invoke executeQuery.  Lazy initialization is not supported by the Web Database.
     *
     * @since 1.0.0
     *
     * @param query Query containing criteria
     *
     * @return LazyQueryCollection lazy loaded results
     *
     * @throws OnyxException Error while executing query
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(OnyxException::class)
    override fun <E : IManagedEntity> executeLazyQuery(query: Query): List<E> {
        val body = EntityQueryBody()
        body.query = query
        return this.performCall(url + AbstractWebPersistenceManager.EXECUTE_QUERY, query.entityType, List::class.java, body) as List<E>
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
        val body = EntityQueryBody()
        body.query = query

        val response = this.performCall(url + AbstractWebPersistenceManager.EXECUTE_UPDATE_QUERY, null, QueryResultResponseBody::class.java, body) as QueryResultResponseBody
        query.resultsCount = response.maxResults
        return response.results
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
        val body = EntityQueryBody()
        body.query = query

        val response = this.performCall(url + AbstractWebPersistenceManager.EXECUTE_DELETE_QUERY, null, QueryResultResponseBody::class.java, body) as QueryResultResponseBody
        query.resultsCount = response.maxResults
        return response.results
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
        val body = EntityRequestBody()
        body.entity = entity
        body.type = entity.javaClass.name
        body.partitionId = entity.partitionValue(context)

        val results = this.performCall(url + AbstractWebPersistenceManager.FIND, null, entity.javaClass, body) as IManagedEntity
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
    @Suppress("UNCHECKED_CAST")
    override fun <E:IManagedEntity> findById(clazz: Class<*>, id: Any): E? {
        val body = EntityRequestBody()
        body.id = id
        body.type = clazz.name
        return this.performCall(url + AbstractWebPersistenceManager.FIND, null, clazz, body) as E
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
     * @throws OnyxException Error when finding entity within partition specified
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E:IManagedEntity> findByIdInPartition(clazz: Class<*>, id: Any, partitionId: Any): E? {
        val body = EntityRequestBody()
        body.id = id
        body.partitionId = partitionId.toString()
        body.type = clazz.name
        return this.performCall(url + AbstractWebPersistenceManager.FIND, null, clazz, body) as E
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
     * @throws OnyxException error occurred while attempting to retrieve entity.
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <E : IManagedEntity?> findByIdWithPartitionId(clazz: Class<*>, id: Any, partitionId: Long): E {
        val body = EntityRequestBody()
        body.id = id
        body.partitionId = partitionId.toString()
        body.type = clazz.name
        return this.performCall(url + AbstractWebPersistenceManager.FIND_WITH_PARTITION_ID, null, clazz, body) as E
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
     * @throws OnyxException Error when finding entity within partition specified
     */
    @Throws(OnyxException::class)
    override fun exists(entity: IManagedEntity): Boolean {
        val body = EntityRequestBody()
        body.entity = entity
        body.type = entity.javaClass.name
        body.partitionId = entity.partitionValue(context)
        return this.performCall(url + AbstractWebPersistenceManager.EXISTS, null, Boolean::class.java, body) as Boolean
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
     * @throws OnyxException Error when finding entity within partition specified
     */
    @Throws(OnyxException::class)
    override fun exists(entity: IManagedEntity, partitionId: Any): Boolean {
        val body = EntityRequestBody()
        body.entity = entity
        body.type = entity.javaClass.name
        body.partitionId = partitionId.toString()
        return this.performCall(url + AbstractWebPersistenceManager.EXISTS, null, Boolean::class.java, body) as Boolean
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
    @Suppress("UNCHECKED_CAST")
    override fun initialize(entity: IManagedEntity, attribute: String) {
        val descriptor = context.getDescriptorForEntity(entity)

        val relationshipDescriptor = descriptor.relationships[attribute] ?: throw RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, attribute, entity.javaClass.name)

        val attributeType = relationshipDescriptor.inverseClass

        val body = EntityInitializeBody()
        body.entityId = entity.identifier(context)
        body.attribute = attribute
        body.entityType = entity.javaClass.name
        body.partitionId = entity.partitionValue(context)

        val relationship = this.performCall(url + AbstractWebPersistenceManager.INITIALIZE, attributeType, List::class.java, body) as List<IManagedEntity>
        entity.setAny(relationshipDescriptor.field, relationship)
    }

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

        // Get the class' identifier and add a simple criteria to ensure the identifier is not null.  This should return all records.
        val criteria = QueryCriteria<Nothing>(descriptor!!.identifier!!.name, QueryCriteriaOperator.NOT_NULL)
        return list(clazz, criteria)
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
    override fun saveRelationshipsForEntity(entity: IManagedEntity, relationship: String, relationshipIdentifiers: Set<Any>) {
        val descriptor = context.getDescriptorForEntity(entity)

        val attributeType = descriptor.relationships[relationship]!!.type

        val body = SaveRelationshipRequestBody()
        body.entity = entity
        body.relationship = relationship
        body.identifiers = relationshipIdentifiers
        body.type = attributeType.name

        this.performCall(url + AbstractWebPersistenceManager.SAVE_RELATIONSHIPS, null, null, body)
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
    override fun <E : IManagedEntity> getWithReference(entityType: Class<*>, reference: Reference): E {
        val body = EntityRequestBody()
        body.id = reference.reference
        body.type = entityType.name
        body.partitionId = reference.partition.toString()
        return this.performCall(url + AbstractWebPersistenceManager.FIND_BY_PARTITION_REFERENCE, null, entityType, body) as E
    }

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @since 1.0.0
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws OnyxException The reference does not exist for that type
     */
    @Throws(OnyxException::class)
    override fun <E : IManagedEntity> getWithReferenceId(entityType: Class<*>, referenceId: Long): E? {
        val body = EntityRequestBody()
        body.id = referenceId
        body.type = entityType.name
        return this.performCall(url + AbstractWebPersistenceManager.FIND_BY_REFERENCE_ID, null, entityType, body) as E
    }

    /**
     * This method is used for bulk streaming data entities.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     *
     * This is unsupported in the WebPersistenceManager.  Please use the native remote driver aka RemotePersistenceManager to take advantage of this feature
     *
     * @since 1.0.0
     *
     * @param query Query to execute and stream
     *
     * @param streamer Instance of the streamer to use to stream the data
     */
    @Throws(OnyxException::class)
    override fun <T : Any> stream(query: Query, streamer: QueryStream<T>) {
        throw StreamException(StreamException.UNSUPPORTED_FUNCTION)
    }

    /**
     * This method is used for bulk streaming.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     *
     * This is unsupported in the WebPersistenceManager.  Please use the native remote driver aka RemotePersistenceManager to take advantage of this feature
     *
     * @since 1.0.0
     *
     * @param query Query to execute and stream
     *
     * @param queryStreamClass Class instance of the database stream
     */
    @Throws(OnyxException::class)
    override fun stream(query: Query, queryStreamClass: Class<*>) {
        throw StreamException(StreamException.UNSUPPORTED_FUNCTION)
    }

    @Throws(OnyxException::class)
    override fun getMapWithReferenceId(entityType: Class<*>, reference: Reference): Map<String,Any?>? = null

    /**
     * Retrieve the quantity of entities that match the query criterium.
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
     * @return The number of entities that meet the query criterium
     * @throws OnyxException Error during query.
     * @since 1.3.0 Implemented with feature request #71
     */
    @Throws(OnyxException::class)
    override fun countForQuery(query: Query): Long {
        val body = EntityQueryBody()
        body.query = query
        return this.performCall(url + AbstractWebPersistenceManager.QUERY_COUNT, null, Long::class.java, body) as Long
    }

    /**
     * This functionality is unsupported for the web server database.  Query subscribers do
     * not work due to the variation of clients.
     *
     * @param query Query with a listener attached
     *
     * @throws OnyxException Un expected error when attempting to unregister listener
     *
     * @since 1.3.0 Added query subscribers as an enhancement.
     */
    @Throws(OnyxException::class)
    override fun removeChangeListener(query: Query): Boolean = false

    /**
     * Listen to a query and register its subscriber
     *
     * @param query Query with query listener
     * @since 1.3.1
     */
    @Throws(OnyxException::class)
    override fun listen(query: Query) {

    }

    companion object {
        val WEB = "/onyx"
    }

}
