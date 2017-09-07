package com.onyx.persistence.manager;

import com.onyx.exception.OnyxException;
import com.onyx.fetch.PartitionReference;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.persistence.query.QueryResult;
import com.onyx.query.QueryListener;
import com.onyx.stream.QueryStream;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistence manager supplies a public API for performing database persistence and querying operations.
 *
 *
 * @author Tim Osborn
 * @author Chris Osborn
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
 */
public interface PersistenceManager {

    /**
     * The context of the database contains descriptor information regarding the entities and instruction on how to structure the record data.  This is usually done within the PersistenceManagerFactory.
     *
     * @since 1.0.0
     * @param context Schema Context implementation
     */
    @SuppressWarnings("unused")
    void setContext(SchemaContext context);

    /**
     * Return the Schema Context that was created by the Persistence Manager Factory.
     *
     * @since 1.0.0
     * @return context Schema Context Implementation
     */
    @SuppressWarnings("unused")
    SchemaContext getContext();

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> E saveEntity(IManagedEntity entity) throws OnyxException;

    /**
     * Batch saves a list of entities.
     *
     * The entities must all be of the same type
     *
     * @since 1.0.0
     * @param entities List of entities
     * @throws OnyxException Exception occurred while saving an entity within the list.  This will not roll back preceding saves if error occurs.
     */
    @SuppressWarnings("unused")
    void saveEntities(List<? extends IManagedEntity> entities) throws OnyxException;

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
    boolean deleteEntity(IManagedEntity entity) throws OnyxException;

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
    @SuppressWarnings("unused")
    void deleteEntities(List<? extends IManagedEntity> entities) throws OnyxException;

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
    int executeDelete(Query query) throws OnyxException;

    /**
     * Execute a delete query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws OnyxException Exception when deleting entities
     */
    @SuppressWarnings("unused")
    QueryResult executeDeleteForResult(Query query) throws OnyxException;

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
    int executeUpdate(Query query) throws OnyxException;

    /**
     * Execute an update query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws OnyxException when an update query failed
     */
    @SuppressWarnings("unused")
    QueryResult executeUpdateForResult(Query query) throws OnyxException;

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
    <E> List<E> executeQuery(Query query) throws OnyxException;

    /**
     * Execute a query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws OnyxException when the query is mal formed or general exception
     */
    @SuppressWarnings("unused")
    QueryResult executeQueryForResult(Query query) throws OnyxException;

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
    <E extends IManagedEntity> List<E> executeLazyQuery(Query query) throws OnyxException;

    /**
     * Execute a lazy query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws OnyxException General exception happened when the query.
     */
    @SuppressWarnings("unused")
    QueryResult executeLazyQueryForResult(Query query) throws OnyxException;

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> E find(IManagedEntity entity) throws OnyxException;

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> E findById(Class clazz, Object id) throws OnyxException;

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> E findByIdInPartition(Class clazz, Object id, Object partitionId) throws OnyxException;

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
    @SuppressWarnings("unused")
    boolean exists(IManagedEntity entity) throws OnyxException;

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
    @SuppressWarnings("unused")
    boolean exists(IManagedEntity entity, Object partitionId) throws OnyxException;

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
    void initialize(IManagedEntity entity, String attribute) throws OnyxException;

    /**
     * Get relationship for an entity
     *
     * @param entity The entity to load
     * @param attribute Attribute that represents the relationship
     * @return The relationship Value
     * @throws OnyxException Error when hydrating relationship.  The attribute must exist and must be a annotated with a relationship
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    Object getRelationship(IManagedEntity entity, String attribute) throws OnyxException;


    /**
     * Provides a list of all entities with a given type
     *
     * @param clazz  Type of managed entity to retrieve
     *
     * @return Unsorted List of all entities with type
     *
     * @throws OnyxException Exception occurred while fetching results
     */
    @SuppressWarnings("unused")
    <E extends IManagedEntity> List<E> list(Class clazz) throws OnyxException;

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria) throws OnyxException;

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
    <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy) throws OnyxException;

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, QueryOrder orderBy) throws OnyxException;

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, Object partitionId) throws OnyxException;

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy, Object partitionId) throws OnyxException;

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, QueryOrder orderBy, Object partitionId) throws OnyxException;

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
    <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy) throws OnyxException;

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> List<E> list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy, Object partitionId) throws OnyxException;

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
    @SuppressWarnings("unused")
    void saveRelationshipsForEntity(IManagedEntity entity, String relationship, Set<Object> relationshipIdentifiers) throws OnyxException;

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @since 1.0.0
     * @param entityType Type of managed entity
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws OnyxException The reference does not exist for that type
     */
    @SuppressWarnings("unused")
    <E extends IManagedEntity> E getWithReferenceId(Class entityType, long referenceId) throws OnyxException;

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
    <E extends IManagedEntity> E getWithPartitionReference(Class entityType, PartitionReference partitionReference) throws OnyxException;

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
    @SuppressWarnings("unused")
    <E extends IManagedEntity> E findByIdWithPartitionId(Class clazz, Object id, long partitionId) throws OnyxException;

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
    @SuppressWarnings("unused")
    void stream(Query query, QueryStream streamer) throws OnyxException;

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
    @SuppressWarnings("unused")
    void stream(Query query, Class queryStreamClass) throws OnyxException;

    /**
     * Get Map representation of an entity with reference id
     *
     * @param entityType Original type of entity
     *
     * @param reference Reference location within a data structure
     *
     * @return Map of key key pair of the entity.  Key being the attribute name.
     */
    Map getMapWithReferenceId(Class entityType, long reference) throws OnyxException;

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
    long countForQuery(Query query) throws OnyxException;

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
    boolean removeChangeListener(Query query) throws OnyxException;

    /**
     * Listen to a query and register its subscriber
     *
     * @param query Query with query listener
     * @since 1.3.1
     */
    void listen(Query query) throws OnyxException;

    /**
     * Listen to a query and register its subscriber
     *
     * @param query Query without query listener
     * @param queryListener listener to invoke for changes
     * @since 1.3.1
     */
    @SuppressWarnings("unused")
    void listen(Query query, QueryListener queryListener) throws OnyxException;
}
