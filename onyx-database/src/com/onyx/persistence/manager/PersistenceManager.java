package com.onyx.persistence.manager;

import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.persistence.query.QueryResult;
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
     * @throws EntityException Exception occured while persisting an entity
     */
    @SuppressWarnings("unused")
    IManagedEntity saveEntity(IManagedEntity entity) throws EntityException;

    /**
     * Batch saves a list of entities.
     *
     * The entities must all be of the same type
     *
     * @since 1.0.0
     * @param entities List of entities
     * @throws EntityException Exception occurred while saving an entity within the list.  This will not roll back preceding saves if error occurs.
     */
    @SuppressWarnings("unused")
    void saveEntities(List<? extends IManagedEntity> entities) throws EntityException;

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
    boolean deleteEntity(IManagedEntity entity) throws EntityException;

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
    @SuppressWarnings("unused")
    void deleteEntities(List<? extends IManagedEntity> entities) throws EntityException;

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
    int executeDelete(Query query) throws EntityException;

    /**
     * Execute a delete query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws EntityException Exception when deleting entities
     */
    @SuppressWarnings("unused")
    QueryResult executeDeleteForResult(Query query) throws EntityException;

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
    int executeUpdate(Query query) throws EntityException;

    /**
     * Execute an update query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws EntityException when an update query failed
     */
    @SuppressWarnings("unused")
    QueryResult executeUpdateForResult(Query query) throws EntityException;

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
    List executeQuery(Query query) throws EntityException;

    /**
     * Execute a query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws EntityException when the query is mal formed or general exception
     */
    @SuppressWarnings("unused")
    QueryResult executeQueryForResult(Query query) throws EntityException;

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
    List executeLazyQuery(Query query) throws EntityException;

    /**
     * Execute a lazy query and return a result object.  This is so that it will play nicely as a proxy object
     * @param query Query used to filter entities with criteria
     * @since 1.2.0
     * @return The results including the original result from the query execute and the updated query object
     * @throws EntityException General exception happened when the query.
     */
    @SuppressWarnings("unused")
    QueryResult executeLazyQueryForResult(Query query) throws EntityException;

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
    @SuppressWarnings("unused")
    IManagedEntity find(IManagedEntity entity) throws EntityException;

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
    @SuppressWarnings("unused")
    IManagedEntity findById(Class clazz, Object id) throws EntityException;

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
    @SuppressWarnings("unused")
    IManagedEntity findByIdInPartition(Class clazz, Object id, Object partitionId) throws EntityException;

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
    @SuppressWarnings("unused")
    boolean exists(IManagedEntity entity) throws EntityException;

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
    @SuppressWarnings("unused")
    boolean exists(IManagedEntity entity, Object partitionId) throws EntityException;

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
    void initialize(IManagedEntity entity, String attribute) throws EntityException;

    /**
     * Get relationship for an entity
     *
     * @param entity The entity to load
     * @param attribute Attribute that represents the relationship
     * @return The relationship Value
     * @throws EntityException Error when hydrating relationship.  The attribute must exist and must be a annotated with a relationship
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    Object getRelationship(IManagedEntity entity, String attribute) throws EntityException;


    /**
     * Provides a list of all entities with a given type
     *
     * @param clazz  Type of managed entity to retrieve
     *
     * @return Unsorted List of all entities with type
     *
     * @throws EntityException Exception occurred while fetching results
     */
    @SuppressWarnings("unused")
    List list(Class clazz) throws EntityException;

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
    @SuppressWarnings("unused")
    List list(Class clazz, QueryCriteria criteria) throws EntityException;

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
    List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy) throws EntityException;

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
    @SuppressWarnings("unused")
    List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy) throws EntityException;

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
    @SuppressWarnings("unused")
    List list(Class clazz, QueryCriteria criteria, Object partitionId) throws EntityException;

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
    @SuppressWarnings("unused")
    List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy, Object partitionId) throws EntityException;

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
    @SuppressWarnings("unused")
    List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy, Object partitionId) throws EntityException;



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
    List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy) throws EntityException;

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
    @SuppressWarnings("unused")
    List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy, Object partitionId) throws EntityException;

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
    @SuppressWarnings("unused")
    void saveRelationshipsForEntity(IManagedEntity entity, String relationship, Set<Object> relationshipIdentifiers) throws EntityException;

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @since 1.0.0
     * @param entityType Type of managed entity
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws EntityException The reference does not exist for that type
     */
    @SuppressWarnings("unused")
    IManagedEntity getWithReferenceId(Class entityType, long referenceId) throws EntityException;

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
    @SuppressWarnings("unused")
    IManagedEntity findByIdWithPartitionId(Class clazz, Object id, long partitionId) throws EntityException;

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
    void stream(Query query, QueryStream streamer) throws EntityException;

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
    void stream(Query query, Class queryStreamClass) throws EntityException;

    /**
     * Get Map representation of an entity with reference id
     *
     * @param entityType Original type of entity
     *
     * @param reference Reference location within a data structure
     *
     * @return Map of key key pair of the entity.  Key being the attribute name.
     */
    Map getMapWithReferenceId(Class entityType, long reference) throws EntityException;

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
     * @throws EntityException Error during query.
     * @since 1.3.0 Implemented with feature request #71
     */
    long countForQuery(Query query) throws EntityException;

    /**
     * Un-register a query listener.  This will remove the listener from observing changes for that query.
     * If you do not un-register queries, they will not expire nor will they be de-registered autmatically.
     * This could cause performance degredation if removing the registration is neglected.
     *
     * @param query Query with a listener attached
     *
     * @throws EntityException Un expected error when attempting to unregister listener
     *
     * @since 1.3.0 Added query subscribers as an enhancement.
     */
    boolean removeChangeListener(Query query) throws EntityException;
}
