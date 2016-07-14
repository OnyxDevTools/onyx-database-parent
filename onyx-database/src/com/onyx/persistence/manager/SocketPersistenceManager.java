package com.onyx.persistence.manager;

import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.persistence.query.QueryResult;
import com.onyx.stream.QueryStream;

import java.rmi.Remote;
import java.rmi.RemoteException;
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
public interface SocketPersistenceManager extends Remote {

    /**
     * Save entity.  Persists a single entity for update or insert.  This method will cascade relationships and persist indexes.
     *
     * @param entity Managed Entity to Save
     * @return Saved Managed Entity
     * @throws RemoteException Exception occured while persisting an entity
     * @since 1.0.0
     */
    IManagedEntity saveEntity(IManagedEntity entity) throws RemoteException;

    /**
     * Batch saves a list of entities.
     * <p>
     * The entities must all be of the same type
     *
     * @param entities List of entities
     * @throws RemoteException Exception occurred while saving an entity within the list.  This will not roll back preceding saves if error occurs.
     * @since 1.0.0
     */
    void saveEntities(List<? extends IManagedEntity> entities) throws RemoteException;

    /**
     * Deletes a single entity
     * <p>
     * The entity must exist otherwise it will throw an exception.  This will cascade delete relationships and remove index references.
     *
     * @param entity Managed Entity to delete
     * @return Flag indicating it was deleted
     * @throws RemoteException Error occurred while deleting
     * @since 1.0.0
     */
    boolean deleteEntity(IManagedEntity entity) throws RemoteException;

    /**
     * Deletes list of entities.
     * <p>
     * The entities must exist otherwise it will throw an exception.  This will cascade delete relationships and remove index references.
     * <p>
     * Requires all of the entities to be of the same type
     *
     * @param entities List of entities
     * @throws RemoteException Error occurred while deleting.  If exception is thrown, preceding entities will not be rolled back
     * @since 1.0.0
     */
    void deleteEntities(List<? extends IManagedEntity> entities) throws RemoteException;

    /**
     * Execute query and delete entities returned in the results
     *
     * @param query Query used to filter entities with criteria
     * @return Number of entities deleted
     * @throws RemoteException Exception occurred while executing delete query
     * @since 1.0.0
     */
    QueryResult executeDeleteForResults(Query query) throws RemoteException;

    /**
     * Updates all rows returned by a given query
     * <p>
     * The query#updates list must not be null or empty
     *
     * @param query Query used to filter entities with criteria
     * @return Number of entities updated
     * @throws RemoteException Exception occurred while executing update query
     * @since 1.0.0
     */
    QueryResult executeUpdateForResults(Query query) throws RemoteException;

    /**
     * Execute query with criteria and optional row limitations
     *
     * @param query Query containing criteria
     * @return Query Results
     * @throws RemoteException Error while executing query
     * @since 1.0.0
     */
    QueryResult executeQueryForResults(Query query) throws RemoteException;

    /**
     * Execute query with criteria and optional row limitations.  Specify lazy instantiation of query results.
     *
     * @param query Query containing criteria
     * @return LazyQueryCollection lazy loaded results
     * @throws RemoteException Error while executing query
     * @since 1.0.0
     */
    QueryResult executeLazyQueryForResults(Query query) throws RemoteException;

    /**
     * Hydrates an instantiated entity.  The instantiated entity must have the primary key defined and partition value if the data is partitioned.
     * All relationships are hydrated based on their fetch policy.
     * The entity must also not be null.
     *
     * @param entity Entity to hydrate.
     * @return Managed Entity
     * @throws RemoteException Error when hydrating entity
     * @since 1.0.0
     */
    IManagedEntity find(IManagedEntity entity) throws RemoteException;

    /**
     * Find Entity By Class and ID.
     * <p>
     * All relationships are hydrated based on their fetch policy.  This does not take into account the partition.
     *
     * @param clazz Managed Entity Type.  This must be a cast of IManagedEntity
     * @param id    Primary Key of entity
     * @return Managed Entity
     * @throws RemoteException Error when finding entity
     * @since 1.0.0
     */
    IManagedEntity findById(Class clazz, Object id) throws RemoteException;

    /**
     * Find Entity By Class and ID.
     * <p>
     * All relationships are hydrated based on their fetch policy.  This does not take into account the partition.
     *
     * @param clazz       Managed Entity Type.  This must be a cast of IManagedEntity
     * @param id          Primary Key of entity
     * @param partitionId Partition value for entity
     * @return Managed Entity
     * @throws RemoteException Error when finding entity within partition specified
     * @since 1.0.0
     */
    IManagedEntity findByIdInPartition(Class clazz, Object id, Object partitionId) throws RemoteException;

    /**
     * Determines if the entity exists within the database.
     * <p>
     * It is determined by the primary id and partition value
     *
     * @param entity Managed Entity to check
     * @return Returns true if the entity primary key exists. Otherwise it returns false
     * @throws RemoteException Error when finding entity within partition specified
     * @since 1.0.0
     */
    boolean exists(IManagedEntity entity) throws RemoteException;

    /**
     * Determines if the entity exists within the database.
     * <p>
     * It is determined by the primary id and partition value
     *
     * @param entity      Managed Entity to check
     * @param partitionId Partition Value for entity
     * @return Returns true if the entity primary key exists. Otherwise it returns false
     * @throws RemoteException Error when finding entity within partition specified
     * @since 1.0.0
     */
    boolean exists(IManagedEntity entity, Object partitionId) throws RemoteException;

    /**
     * Force Hydrate relationship based on attribute name
     *
     * @param entity    Managed Entity to attach relationship values
     * @param attribute String representation of relationship attribute
     * @throws RemoteException Error when hydrating relationship.  The attribute must exist and be a relationship.
     * @since 1.0.0
     */
    void initialize(IManagedEntity entity, String attribute) throws RemoteException;

    /**
     * Hydrate a relationship and return the value
     *
     * @param entity    Managed Entity to attach relationship values
     * @param attribute String representation of relationship attribute
     * @throws RemoteException Error when hydrating relationship.  The attribute must exist and be a relationship.
     * @since 1.0.0
     */
    Object findRelationship(IManagedEntity entity, String attribute) throws RemoteException;

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results.
     *
     * @param clazz    Managed Entity type
     * @param criteria Query Criteria to filter results
     * @return Unsorted List of results matching criteria
     * @throws RemoteException Exception occurred while filtering results
     * @since 1.0.0
     */
    List list(Class clazz, QueryCriteria criteria) throws RemoteException;

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results.
     *
     * @param clazz    Managed Entity type
     * @param criteria Query Criteria to filter results
     * @param orderBy  Array of sort objects
     * @return Sorted List of results matching criteria
     * @throws RemoteException Exception occurred while filtering results
     * @since 1.0.0
     */
    List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy) throws RemoteException;

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results.
     *
     * @param clazz    Managed Entity type
     * @param criteria Query Criteria to filter results
     * @param orderBy  A single sort specification
     * @return Sorted List of results matching criteria
     * @throws RemoteException Exception occurred while filtering results
     * @since 1.0.0
     */
    List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy) throws RemoteException;

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results within a partition.
     *
     * @param clazz       Managed Entity type
     * @param criteria    Query Criteria to filter results
     * @param partitionId Partition value for entities
     * @return Unsorted List of results matching criteria within a partition
     * @throws RemoteException Exception occurred while filtering results
     * @since 1.0.0
     */
    List list(Class clazz, QueryCriteria criteria, Object partitionId) throws RemoteException;

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results within a partition.
     *
     * @param clazz       Managed Entity type
     * @param criteria    Query Criteria to filter results
     * @param orderBy     Array of sort order specifications
     * @param partitionId Partition value for entities
     * @return Sorted List of results matching criteria within a partition
     * @throws RemoteException Exception occurred while filtering results
     * @since 1.0.0
     */
    List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy, Object partitionId) throws RemoteException;

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results within a partition.
     *
     * @param clazz       Managed Entity type
     * @param criteria    Query Criteria to filter results
     * @param orderBy     A single order specification
     * @param partitionId Partition value for entities
     * @return Sorted List of results matching criteria within a partition
     * @throws RemoteException Exception occurred while filtering results
     * @since 1.0.0
     */
    List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy, Object partitionId) throws RemoteException;

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results.
     * <p>
     * This allows for a specified range of results.
     *
     * @param clazz      Managed Entity type
     * @param criteria   Query Criteria to filter results
     * @param start      Start of record results.
     * @param maxResults Max number of results returned
     * @param orderBy    An array of sort order specification
     * @return Sorted List of results matching criteria within range
     * @throws RemoteException Exception occurred while filtering results
     * @since 1.0.0
     */
    List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy) throws RemoteException;

    /**
     * Provides a list of results with a list of given criteria with no limits on number of results within a partition.
     * <p>
     * This allows for a specified range of results.
     *
     * @param clazz       Managed Entity type
     * @param criteria    Query Criteria to filter results
     * @param start       Start of record results.
     * @param maxResults  Max number of results returned
     * @param orderBy     An array of sort order specification
     * @param partitionId Partition value to filter results
     * @return Sorted List of results matching criteria within range and partition
     * @throws RemoteException Exception occurred while filtering results
     * @since 1.0.0
     */
    List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy, Object partitionId) throws RemoteException;

    /**
     * This is a way to batch save all relationships for an entity.  This does not retain any existing relationships and will
     * overwrite all existing with the set you are sending in.  This is useful to optimize batch saving entities with relationships.
     *
     * @param entity                  Parent Managed Entity
     * @param relationship            Relationship attribute
     * @param relationshipIdentifiers Existing relationship identifiers
     * @throws RemoteException Error occurred while saving relationship.
     * @since 1.0.0
     */
    void saveRelationshipsForEntity(IManagedEntity entity, String relationship, Set<Object> relationshipIdentifiers) throws RemoteException;

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @param entityType  Type of managed entity
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws RemoteException The reference does not exist for that type
     * @since 1.0.0
     */
    IManagedEntity getWithReferenceId(Class entityType, long referenceId) throws RemoteException;

    /**
     * Retrieves an entity using the primaryKey and partition
     *
     * @param clazz       Entity Type
     * @param id          Entity Primary Key
     * @param partitionId - Partition Identifier.  Not to be confused with partition value.  This is a unique id within the partition System table
     * @return Managed Entity
     * @throws RemoteException error occurred while attempting to retrieve entity.
     * @since 1.0.0
     */
    IManagedEntity findByIdWithPartitionId(Class clazz, Object id, long partitionId) throws RemoteException;

    /**
     * This method is used for bulk streaming.  An example of bulk streaming is for analytics or bulk updates included but not limited to model changes.
     *
     * @param query            Query to execute and stream
     * @param queryStreamClass Class instance of the database stream
     * @since 1.0.0
     */
    void stream(Query query, Class queryStreamClass) throws RemoteException;

    /**
     * Get Map representation of an entity with reference id
     *
     * @param entityType Original type of entity
     *
     * @param reference Reference location within a data structure
     *
     * @return Map of key value pair of the entity.  Key being the attribute name.
     */
    Map getMapWithReferenceId(Class entityType, long reference) throws RemoteException;
}