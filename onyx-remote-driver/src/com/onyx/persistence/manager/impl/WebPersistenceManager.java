package com.onyx.persistence.manager.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.exception.EntityClassNotFoundException;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.RelationshipNotFoundException;
import com.onyx.helpers.PartitionHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.record.AbstractRecordController;
import com.onyx.request.pojo.*;
import com.onyx.util.AttributeField;
import com.onyx.util.ObjectUtil;

import java.util.*;

/**
 * Persistence manager supplies a public API for performing database persistence and querying operations.  This specifically is used for an Restful WEB API database.
 * Entities that are passed through these methods must be serializable using the Jackson JSON Serializer
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   PersistenceManagerFactory factory = new RemotePersistenceManagerFactory();
 *   factory.setCredentials("username", "password");
 *   factory.setLocation("onx://23.234.25.23:8080")
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
public class WebPersistenceManager extends AbstractWebPersistenceManager implements PersistenceManager
{

    public static final String WEB = "/onyx";
    public static final ObjectUtil objectUtil = ObjectUtil.getInstance();

    /**
     * Get Database URL
     * @return Database URL with Path
     */
    public String getURL()
    {
        return context.getRemoteEndpoint() + WEB;
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
        EntityRequestBody body = new EntityRequestBody();
        body.setEntity(entity);
        body.setType(entity.getClass().getCanonicalName());

        Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, context);
        if(partitionValue != null)
        {
            body.setPartitionId(String.valueOf(partitionValue));
        }
        ObjectUtil.copy((IManagedEntity)this.performCall(getURL() + SAVE, null, entity.getClass(), body), entity, context.getDescriptorForEntity(entity));
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
        if(entities.size() > 0)
        {
            EntityListRequestBody body = new EntityListRequestBody();
            try
            {
                body.setEntities(objectMapper.writeValueAsString(entities));
            } catch (JsonProcessingException e)
            {
                throw new EntityClassNotFoundException(EntityClassNotFoundException.UNKNOWN_EXCEPTION);
            }

            try
            {
                body.setType(entities.get(0).getClass().getCanonicalName());
            }
            catch (ClassCastException e)
            {
                throw new EntityClassNotFoundException(EntityClassNotFoundException.PERSISTED_NOT_FOUND);
            }
            this.performCall(getURL() + BATCH_SAVE, null, Void.class, body);
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
        EntityRequestBody body = new EntityRequestBody();
        body.setEntity(entity);
        body.setType(entity.getClass().getCanonicalName());
        return (boolean)this.performCall(getURL() + DELETE, null, Boolean.class, body);
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
        if(entities.size() > 0)
        {
            EntityListRequestBody body = new EntityListRequestBody();
            try
            {
                body.setEntities(objectMapper.writeValueAsString(entities));
            } catch (JsonProcessingException e)
            {
                throw new EntityClassNotFoundException(EntityClassNotFoundException.UNKNOWN_EXCEPTION);
            }
            body.setType(entities.get(0).getClass().getCanonicalName());
            this.performCall(getURL() + BATCH_DELETE, null, null, body);
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
        final EntityQueryBody body = new EntityQueryBody();
        body.setQuery(query);

        if(query.getSelections() != null && query.getSelections().size() > 0)
        {
            QueryResultResponseBody response = (QueryResultResponseBody)this.performCall(getURL() + EXECUTE_QUERY, HashMap.class, QueryResultResponseBody.class, body);
            query.setResultsCount(response.getMaxResults());
            return response.getResultList();
        }

        QueryResultResponseBody response = (QueryResultResponseBody)this.performCall(getURL() + EXECUTE_QUERY, query.getEntityType(), QueryResultResponseBody.class, body);
        query.setResultsCount(response.getMaxResults());
        return response.getResultList();
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
     * @throws EntityException Error while executing query
     */
    @Override
    public List executeLazyQuery(Query query) throws EntityException
    {
        final EntityQueryBody body = new EntityQueryBody();
        body.setQuery(query);
        return (List)this.performCall(getURL() + EXECUTE_QUERY, query.getEntityType(), List.class, body);
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
    public int executeUpdate(Query query) throws EntityException
    {
        final EntityQueryBody body = new EntityQueryBody();
        body.setQuery(query);

        QueryResultResponseBody response = (QueryResultResponseBody)this.performCall(getURL() + EXECUTE_UPDATE_QUERY, null, QueryResultResponseBody.class, body);
        query.setResultsCount(response.getMaxResults());
        return response.getResults();
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
        final EntityQueryBody body = new EntityQueryBody();
        body.setQuery(query);

        QueryResultResponseBody response = (QueryResultResponseBody)this.performCall(getURL() + EXECUTE_DELETE_QUERY, null, QueryResultResponseBody.class, body);
        query.setResultsCount(response.getMaxResults());
        return response.getResults();
    }

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
     * @throws EntityException Error when hydrating entity
     */
    public IManagedEntity find(IManagedEntity entity) throws EntityException
    {
        final EntityRequestBody body = new EntityRequestBody();
        body.setEntity(entity);
        body.setType(entity.getClass().getCanonicalName());
        Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, context);
        if(partitionValue != null)
        {
            body.setPartitionId(String.valueOf(partitionValue));
        }

        ObjectUtil.copy((IManagedEntity)this.performCall(getURL() + FIND, null, entity.getClass(), body), entity, context.getDescriptorForEntity(entity));

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
    public IManagedEntity findById(Class clazz, Object id) throws EntityException
    {
        final EntityRequestBody body = new EntityRequestBody();
        body.setId(id);
        body.setType(clazz.getCanonicalName());
        return (IManagedEntity)this.performCall(getURL() + FIND, null, clazz, body);
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
     * @param partitionId Partition value for entity
     * @return Managed Entity
     * @throws EntityException Error when finding entity within partition specified
     */
    public IManagedEntity findByIdInPartition(Class clazz, Object id, Object partitionId) throws EntityException
    {
        final EntityRequestBody body = new EntityRequestBody();
        body.setId(id);
        body.setPartitionId(String.valueOf(partitionId));
        body.setType(clazz.getCanonicalName());
        return (IManagedEntity)this.performCall(getURL() + FIND, null, clazz, body);
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
     * @param partitionId - Partition Identifier.  Not to be confused with partition value.  This is a unique id within the partition System table
     * @return Managed Entity
     *
     * @throws EntityException error occurred while attempting to retrieve entity.
     */
    @Override
    public IManagedEntity findByIdWithPartitionId(Class clazz, Object id, long partitionId) throws EntityException
    {
        final EntityRequestBody body = new EntityRequestBody();
        body.setId(id);
        body.setPartitionId(String.valueOf(partitionId));
        body.setType(clazz.getCanonicalName());
        return (IManagedEntity)this.performCall(getURL() + FIND_WITH_PARTITION_ID, null, clazz, body);
    }

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
     * @throws EntityException Error when finding entity within partition specified
     */
    public boolean exists(IManagedEntity entity) throws EntityException
    {
        final EntityRequestBody body = new EntityRequestBody();
        body.setEntity(entity);
        body.setType(entity.getClass().getCanonicalName());
        Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, context);
        if(partitionValue != null)
        {
            body.setPartitionId(String.valueOf(partitionValue));
        }
        return (boolean)this.performCall(getURL() + EXISTS, null, Boolean.class, body);
    }

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
     * @throws EntityException Error when finding entity within partition specified
     */
    public boolean exists(IManagedEntity entity, Object partitionId) throws EntityException
    {
        final EntityRequestBody body = new EntityRequestBody();
        body.setEntity(entity);
        body.setType(entity.getClass().getCanonicalName());
        body.setPartitionId(String.valueOf(partitionId));
        return (boolean)this.performCall(getURL() + EXISTS, null, Boolean.class, body);
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
    public void initialize(IManagedEntity entity, String attribute) throws EntityException
    {
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);

        final RelationshipDescriptor relationshipDescriptor = descriptor.getRelationships().get(attribute);

        if(relationshipDescriptor == null)
        {
            throw new RelationshipNotFoundException(RelationshipNotFoundException.RELATIONSHIP_NOT_FOUND, attribute, entity.getClass().getCanonicalName());
        }
        Class attributeType = relationshipDescriptor.getInverseClass();

        EntityInitializeBody body = new EntityInitializeBody();
        body.setEntityId(AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier()));
        body.setAttribute(attribute);
        body.setEntityType(entity.getClass().getCanonicalName());
        Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, context);
        if(partitionValue != null)
        {
            body.setPartitionId(String.valueOf(partitionValue));
        }
        List relationship =  (List<IManagedEntity>)this.performCall(getURL() + INITIALIZE, attributeType, List.class, body);
        objectUtil.setAttribute(entity, relationship, new AttributeField(objectUtil.getField(entity.getClass(), attribute)));
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
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria) throws EntityException
    {
        return list(clazz, criteria, new QueryOrder[0]);
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
     * @param orderBy Array of sort objects
     *
     * @return Sorted List of results matching criteria
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy) throws EntityException
    {
        return list(clazz, criteria, 0, -1, orderBy);
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
     * @param orderBy A single sort specification
     *
     * @return Sorted List of results matching criteria
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy) throws EntityException
    {
        QueryOrder[] queryOrders = {orderBy};
        return list(clazz, criteria, queryOrders);
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
     * @param partitionId Partition value for entities
     *
     * @return Unsorted List of results matching criteria within a partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, Object partitionId) throws EntityException
    {
        return list(clazz, criteria, new QueryOrder[0], partitionId);
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
     * @param orderBy Array of sort order specifications
     *
     * @param partitionId Partition value for entities
     *
     * @return Sorted List of results matching criteria within a partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, QueryOrder[] orderBy, Object partitionId) throws EntityException
    {
        return list(clazz, criteria, 0, -1, orderBy, partitionId);
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
     * @param orderBy A single order specification
     *
     * @param partitionId Partition value for entities
     *
     * @return Sorted List of results matching criteria within a partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, QueryOrder orderBy, Object partitionId) throws EntityException
    {
        QueryOrder[] queryOrders = {orderBy};
        return list(clazz, criteria, queryOrders, partitionId);
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
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy) throws EntityException
    {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final Query tmpQuery = new Query(clazz, criteria);
        tmpQuery.setMaxResults(maxResults);
        tmpQuery.setFirstRow(start);
        if (orderBy != null)
        {
            tmpQuery.setQueryOrders(Arrays.asList(orderBy));
        }
        return executeQuery(tmpQuery);
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
     * @param partitionId Partition value to filter results
     *
     * @return Sorted List of results matching criteria within range and partition
     *
     * @throws EntityException Exception occurred while filtering results
     */
    @Override
    public List list(Class clazz, QueryCriteria criteria, int start, int maxResults, QueryOrder[] orderBy, Object partitionId) throws EntityException
    {
        if (context.getKillSwitch())
            throw new InitializationException(InitializationException.DATABASE_SHUTDOWN);

        final Query tmpQuery = new Query(clazz, criteria);
        tmpQuery.setPartition(partitionId);
        tmpQuery.setMaxResults(maxResults);
        tmpQuery.setFirstRow(start);
        if (orderBy != null)
        {
            tmpQuery.setQueryOrders(Arrays.asList(orderBy));
        }

        return executeQuery(tmpQuery);
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

        Class attributeType = descriptor.getRelationships().get(relationship).getType();

        SaveRelationshipRequestBody body = new SaveRelationshipRequestBody();
        body.setEntity(entity);
        body.setRelationship(relationship);
        body.setIdentifiers(relationshipIdentifiers);
        body.setType(attributeType.getCanonicalName());

        this.performCall(getURL() + SAVE_RELATIONSHIPS, null, null, body);
    }

    /**
     * Get entity with Reference Id.  This is used within the LazyResultsCollection and LazyQueryResults to fetch entities with file record ids.
     *
     * @since 1.0.0
     * @param referenceId Reference location within database
     * @return Managed Entity
     * @throws EntityException The reference does not exist for that type
     */
    @Override
    public IManagedEntity getWithReferenceId(Class entityType, long referenceId) throws EntityException
    {
        final EntityRequestBody body = new EntityRequestBody();
        body.setId(referenceId);
        body.setType(entityType.getCanonicalName());
        return (IManagedEntity)this.performCall(getURL() + FIND_BY_REFERENCE_ID, null, entityType, body);
    }

}
