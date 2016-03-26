package com.onyx.persistence.manager;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.persistence.query.QueryResult;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
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
     * @since 1.0.0
     *
     * @param entity Managed Entity to Save
     *
     * @return Saved Managed Entity
     *
     * @throws RemoteException Exception occured while persisting an entity
     */
    IManagedEntity saveEntity(IManagedEntity entity) throws RemoteException;

    /**
     * Batch saves a list of entities.
     *
     * The entities must all be of the same type
     *
     * @since 1.0.0
     * @param entities List of entities
     * @throws RemoteException Exception occurred while saving an entity within the list.  This will not roll back preceding saves if error occurs.
     */
    void saveEntities(List<? extends IManagedEntity> entities) throws RemoteException;

    /**
     * Deletes a single entity
     *
     * The entity must exist otherwise it will throw an exception.  This will cascade delete relationships and remove index references.
     *
     * @since 1.0.0
     * @param entity Managed Entity to delete
     * @return Flag indicating it was deleted
     * @throws RemoteException Error occurred while deleting
     */
    boolean deleteEntity(IManagedEntity entity) throws RemoteException;

    /**
     * Deletes list of entities.
     *
     * The entities must exist otherwise it will throw an exception.  This will cascade delete relationships and remove index references.
     *
     * Requires all of the entities to be of the same type
     *
     * @since 1.0.0
     * @param entities List of entities
     * @throws RemoteException Error occurred while deleting.  If exception is thrown, preceding entities will not be rolled back
     */
    void deleteEntities(List<? extends IManagedEntity> entities) throws RemoteException;

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
    QueryResult executeDeleteForResults(Query query) throws RemoteException;

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
    QueryResult executeUpdateForResults(Query query) throws RemoteException;

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
    QueryResult executeQueryForResults(Query query) throws RemoteException;

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
    QueryResult executeLazyQueryForResults(Query query) throws RemoteException;

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
     * @throws RemoteException Error when hydrating entity
     */
    IManagedEntity find(IManagedEntity entity) throws RemoteException;

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
     * @throws RemoteException Error when finding entity
     */
    IManagedEntity findById(Class clazz, Object id) throws RemoteException;

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
     * @throws RemoteException Error when finding entity within partition specified
     */
    IManagedEntity findByIdInPartition(Class clazz, Object id, Object partitionId) throws RemoteException;

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
     * @throws RemoteException Error when finding entity within partition specified
     */
    boolean exists(IManagedEntity entity) throws RemoteException;

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
     * @throws RemoteException Error when finding entity within partition specified
     */
    boolean exists(IManagedEntity entity, Object partitionId) throws RemoteException;

    /**
     * Force Hydrate relationship based on attribute name
     *
     * @since 1.0.0
     *
     * @param entity Managed Entity to attach relationship values
     *
     * @param attribute String representation of relationship attribute
     *
     * @throws RemoteException Error when hydrating relationship.  The attribute must exist and be a relationship.
     */
    void initialize(IManagedEntity entity, String attribute) throws RemoteException;

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
    Object findRelationship(IManagedEntity entity, String attribute) throws RemoteException;

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
     * @throws RemoteException Exception occurred while filtering results
     */
    List list(Class clazz, QueryCriteria criteria) throws RemoteException;

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
     * @throws RemoteException Exception occurred while filtering results
     */
    List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy) throws RemoteException;

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
     * @throws RemoteException Exception occurred while filtering results
     */
    List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy) throws RemoteException;

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
     * @throws RemoteException Exception occurred while filtering results
     */
    List list(Class clazz, QueryCriteria criteria, Object partitionId) throws RemoteException;

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
     * @throws RemoteException Exception occurred while filtering results
     */
    List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy, Object partitionId) throws RemoteException;

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
     * @throws RemoteException Exception occurred while filtering results
     */
    List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy, Object partitionId) throws RemoteException;

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
     * @throws RemoteException Exception occurred while filtering results
     */
    List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy) throws RemoteException;

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
     * @throws RemoteException Exception occurred while filtering results
     */
    List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy, Object partitionId) throws RemoteException;

    /**
     * This is a way to batch save all relationships for an entity.  This does not retain any existing relationships and will
     * overwrite all existing with the set you are sending in.  This is useful to optimize batch saving entities with relationships.
     *
     * @since 1.0.0
     * @param entity Parent Managed Entity
     * @param relationship Relationship attribute
     * @param relationshipIdentifiers Existing relationship identifiers
     *
     * @throws RemoteException Error occurred while saving relationship.
     */
    void saveRelationshipsForEntity(IManagedEntity entity, String relationship, Set<Object> relationshipIdentifiers) throws RemoteException;

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @since 1.0.0
     * @param entityType Type of managed entity
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws RemoteException The reference does not exist for that type
     */
    IManagedEntity getWithReferenceId(Class entityType, long referenceId) throws RemoteException;

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
     * @throws RemoteException error occurred while attempting to retrieve entity.
     */
    IManagedEntity findByIdWithPartitionId(Class clazz, Object id, long partitionId) throws RemoteException;
}
