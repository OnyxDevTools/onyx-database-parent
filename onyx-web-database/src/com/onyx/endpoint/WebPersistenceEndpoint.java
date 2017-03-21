package com.onyx.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.onyx.exception.EntityClassNotFoundException;
import com.onyx.exception.EntityException;
import com.onyx.helpers.PartitionHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.record.AbstractRecordController;
import com.onyx.request.pojo.*;
import com.onyx.util.ReflectionUtil;

import java.io.IOException;
import java.util.List;

/**
 * Created by timothy.osborn on 4/8/15.
 *
 * This class handles JSON serialization for a persistence web service
 */
final public class WebPersistenceEndpoint
{

    private final PersistenceManager persistenceManager;

    private final ObjectMapper objectMapper; // Used for entity serialization

    private final SchemaContext context;

    /**
     * Constructor
     * @param persistenceManager Internal persistence manager.  Make this a proxy in order to point to remote databases.
     * @param schemaContext Schema Context used
     */
    public WebPersistenceEndpoint(final PersistenceManager persistenceManager, final ObjectMapper objectMapper, final SchemaContext schemaContext)
    {
        this.persistenceManager = persistenceManager;
        this.objectMapper = objectMapper;
        this.context = schemaContext;
    }

    /**
     * Save Entity
     *
     * @param request Entity Request Body
     * @return Managed entity after save with populated id
     * @throws EntityException Generic exception
     * @throws ClassNotFoundException Not found when attempting to reflect
     */
    public IManagedEntity save(EntityRequestBody request) throws EntityException, ClassNotFoundException {
        final Class clazz = Class.forName(request.getType());
        IManagedEntity entity = (IManagedEntity)objectMapper.convertValue(request.getEntity(), clazz);
        persistenceManager.saveEntity(entity);

        return entity;
    }

    /**
     * Find Entity with Reference Id
     *
     * @param request Entity Request Body
     * @return Populated entity or null if not found
     * @throws EntityException General entity exception
     */
    public IManagedEntity findByReferenceId(EntityRequestBody request) throws EntityException {
        Class clazz = null;
        try {
            clazz = Class.forName(request.getType());
        } catch (ClassNotFoundException e) {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND, clazz);
        }

        return persistenceManager.getWithReferenceId(clazz, (long)request.getId());
    }


    /**
     * Find Entity
     *
     * @param request Entity Request Body
     * @return Hydrated entity
     * @throws ClassNotFoundException Class wasn't found during reflection
     * @throws IllegalAccessException Could not reflect on private method
     * @throws InstantiationException Cannot instantiate entity
     * @throws EntityException General entity exception
     */
    public IManagedEntity get(EntityRequestBody request) throws ClassNotFoundException, IllegalAccessException, InstantiationException, EntityException {
        Class clazz = Class.forName(request.getType());
        IManagedEntity entity;

        if(request.getEntity() == null)
            entity = (IManagedEntity)clazz.newInstance();
        else
            entity = (IManagedEntity)objectMapper.convertValue(request.getEntity(), clazz);

        if(request.getId() != null)
        {
            AbstractRecordController.setIndexValueForEntity(entity, request.getId(), context);
        }
        if(request.getPartitionId() != null && !request.getPartitionId().equals(""))
        {
            PartitionHelper.setPartitionValueForEntity(entity, request.getPartitionId(), context);
        }

        entity = persistenceManager.find(entity);
        return entity;
    }

    /**
     * Find Entity within a partition
     *
     * @param request Entity Request Body
     * @return Hydrated entity
     * @throws EntityException General entity exception
     */
    public IManagedEntity findWithPartitionId(EntityRequestBody request) throws EntityException {
        Class clazz;
        try {
            clazz = Class.forName(request.getType());
        } catch (ClassNotFoundException e) {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.ENTITY_NOT_FOUND);
        }
        long partitionId = Long.valueOf(request.getPartitionId());

        return persistenceManager.findByIdWithPartitionId(clazz, request.getId(), partitionId);
    }



    /**
     * Delete Entity
     *
     * @param request Entity Request Body
     * @return Whether the entity was deleted or not
     * @throws ClassNotFoundException Class wasn't found during reflection
     * @throws IllegalAccessException Could not reflect on private method
     * @throws InstantiationException Cannot instantiate entity
     * @throws EntityException General entity exception
     */
    public boolean delete(EntityRequestBody request) throws ClassNotFoundException, IllegalAccessException, InstantiationException, EntityException {
        Class clazz = Class.forName(request.getType());
        IManagedEntity entity;

        if(request.getEntity() == null)
            entity = (IManagedEntity)clazz.newInstance();
        else
            entity = (IManagedEntity)objectMapper.convertValue(request.getEntity(), clazz);

        if(request.getId() != null)
        {
            AbstractRecordController.setIndexValueForEntity(entity, request.getId(), context);
        }
        if(request.getPartitionId() != null && !request.getPartitionId().equals(""))
        {
            PartitionHelper.setPartitionValueForEntity(entity, request.getPartitionId(), context);
        }

        return persistenceManager.deleteEntity(entity);
    }

    /**
     * Execute Query
     *
     * @param request Query Body
     * @return Query Result body
     * @throws EntityException Entity exception while executing query
     */
    public QueryResultResponseBody executeQuery(EntityQueryBody request) throws EntityException
    {
        final List results = persistenceManager.executeQuery(request.getQuery());
        return new QueryResultResponseBody(request.getQuery().getResultsCount(), results);
    }

    /**
     * Initialize Relationship.  Called upon a to many relationship
     *
     * @param request Initialize Body Error
     * @return List of relationship objects
     * @throws ClassNotFoundException Class wasn't found during reflection
     * @throws IllegalAccessException Could not reflect on private method
     * @throws InstantiationException Cannot instantiate entity
     * @throws EntityException General entity exception
     */
    @SuppressWarnings("unchecked")
    public List<IManagedEntity> initialize(EntityInitializeBody request) throws EntityException, IllegalAccessException, InstantiationException, ClassNotFoundException
    {
        Class clazz = Class.forName(request.getEntityType());
        IManagedEntity entity = (IManagedEntity)clazz.newInstance();

        if(request.getEntityId() != null)
        {
            AbstractRecordController.setIndexValueForEntity(entity, request.getEntityId(), context);
        }

        if(request.getPartitionId() != null && !request.getPartitionId().equals(""))
        {
            PartitionHelper.setPartitionValueForEntity(entity, request.getPartitionId(), context);
        }


        persistenceManager.initialize(entity, request.getAttribute());
        return (List<IManagedEntity>)ReflectionUtil.getObject(entity, context.getDescriptorForEntity(entity).getRelationships().get(request.getAttribute()).getField());
    }

    /**
     * Batch Save
     *
     * @param request List of entity body
     * @throws EntityException Error saving entities
     * @throws ClassNotFoundException Cannot reflect cause entity not found
     */
    public void saveEntities(EntityListRequestBody request) throws EntityException, ClassNotFoundException
    {
        final Class clazz = Class.forName(request.getType());

        final CollectionType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
        List<IManagedEntity> entities;

        try
        {
            entities = objectMapper.readValue(request.getEntities(), javaType);
        } catch (IOException e)
        {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.UNKNOWN_EXCEPTION);
        }

        persistenceManager.saveEntities(entities);
    }

    /**
     * Batch Delete
     *
     * @param request Entity List body
     * @throws EntityException Exception when deleting entities
     * @throws ClassNotFoundException Cannot find entity type
     */
    public void deleteEntities(EntityListRequestBody request) throws EntityException, ClassNotFoundException
    {
        final Class clazz = Class.forName(request.getType());

        final CollectionType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
        List<IManagedEntity> entities;

        try
        {
            entities = objectMapper.readValue(request.getEntities(), javaType);
        } catch (IOException e)
        {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.UNKNOWN_EXCEPTION);
        }

        persistenceManager.deleteEntities(entities);
    }

    /**
     * Execute Update
     *
     * @param request Entity Query Body
     * @return Query Result body
     * @throws EntityException Error executing update
     */
    public QueryResultResponseBody executeUpdate(EntityQueryBody request) throws EntityException
    {
        final int results = persistenceManager.executeUpdate(request.getQuery());
        return new QueryResultResponseBody(request.getQuery().getMaxResults(), results);
    }

    /**
     * Execute Delete
     *
     * @param request Entity Query Body
     * @return Query Result body
     * @throws EntityException Error executing delete
     */
    public QueryResultResponseBody executeDelete(EntityQueryBody request) throws EntityException
    {
        final int results = persistenceManager.executeDelete(request.getQuery());
        return new QueryResultResponseBody(request.getQuery().getMaxResults(), results);
    }

    /**
     * Exists
     *
     * @param body Entity Request Body
     * @return Whether the entity exists or not
     * @throws ClassNotFoundException Class wasn't found during reflection
     * @throws IllegalAccessException Could not reflect on private method
     * @throws InstantiationException Cannot instantiate entity
     * @throws EntityException General entity exception
     */
    public boolean exists(EntityRequestBody body) throws ClassNotFoundException, IllegalAccessException, InstantiationException, EntityException {
        Class clazz = Class.forName(body.getType());

        IManagedEntity entity;
        if(body.getEntity() != null)
            entity = (IManagedEntity)objectMapper.convertValue(body.getEntity(), clazz);
        else
            entity = (IManagedEntity)clazz.newInstance();

        if(body.getId() != null)
        {
            AbstractRecordController.setIndexValueForEntity(entity, body.getId(), context);
        }

        if(body.getPartitionId() != null && !body.getPartitionId().equals(""))
        {
            PartitionHelper.setPartitionValueForEntity(entity, body.getPartitionId(), context);
        }

        return persistenceManager.exists(entity);
    }

    /**
     * Save Deferred Relationships
     *
     * @param request Save Relationship Request Body
     * @throws EntityException Generic Entity exception trying to save relationships
     * @throws ClassNotFoundException Entity type not found
     */
    public void saveRelationshipsForEntity(SaveRelationshipRequestBody request) throws EntityException, ClassNotFoundException
    {
        final Class clazz = Class.forName(request.getType());
        IManagedEntity entity = (IManagedEntity)objectMapper.convertValue(request.getEntity(), clazz);

        persistenceManager.saveRelationshipsForEntity(entity, request.getRelationship(), request.getIdentifiers());
    }

    /**
     * Returns the number of items matching the query criteria
     *
     * @param body Query request body
     * @return long value of number of items matching criteria
     * @throws EntityException General query exception
     * @since 1.3.0 Added as enhancement for git issue #71
     */
    public long countForQuery(EntityQueryBody body) throws EntityException {
        return persistenceManager.countForQuery(body.getQuery());
    }
}
