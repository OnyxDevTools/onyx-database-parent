package com.onyx.endpoint;

/**
 * Created by tosborn1 on 12/31/15.
 */

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
import com.onyx.util.ObjectUtil;

import java.io.IOException;
import java.util.List;


/**
 * Created by timothy.osborn on 4/8/15.
 *
 * This class handles JSON serialization for a persistence web service
 */
final public class WebPersistenceEndpoint
{

    protected final PersistenceManager persistenceManager;

    protected final ObjectMapper objectMapper; // Used for entity serialization

    protected final SchemaContext context;

    protected ObjectUtil reflection = ObjectUtil.getInstance();

    /**
     * Constructor
     * @param persistenceManager
     * @param schemaContext
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
     * @param request
     * @return
     * @throws EntityException
     * @throws ClassNotFoundException
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
     * @param request
     * @return
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws EntityException
     */
    public IManagedEntity findByReferenceId(EntityRequestBody request) throws ClassNotFoundException, IllegalAccessException, InstantiationException, EntityException {
        Class clazz = Class.forName(request.getType());

        return persistenceManager.getWithReferenceId(clazz, (long)request.getId());
    }


    /**
     * Find Entity
     *
     * @param request
     * @return
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws EntityException
     */
    public IManagedEntity get(EntityRequestBody request) throws ClassNotFoundException, IllegalAccessException, InstantiationException, EntityException {
        Class clazz = Class.forName(request.getType());
        IManagedEntity entity = null;

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

        entity = (IManagedEntity)persistenceManager.find(entity);
        return entity;
    }

    /**
     * Find Entity
     *
     * @param request
     * @return
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws EntityException
     */
    public IManagedEntity findWithPartitionId(EntityRequestBody request) throws ClassNotFoundException, IllegalAccessException, InstantiationException, EntityException {
        Class clazz = Class.forName(request.getType());
        long partitionId = Long.valueOf(request.getPartitionId());

        return (IManagedEntity)persistenceManager.findByIdWithPartitionId(clazz, request.getId(), partitionId);
    }



    /**
     * Delete Entity
     *
     * @param request
     * @return
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws EntityException
     */
    public boolean delete(EntityRequestBody request) throws ClassNotFoundException, IllegalAccessException, InstantiationException, EntityException {
        Class clazz = Class.forName(request.getType());
        IManagedEntity entity = null;

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
     * @param request
     * @return
     * @throws EntityException
     */
    public QueryResultResponseBody executeQuery(EntityQueryBody request) throws EntityException
    {
        final List results = persistenceManager.executeQuery(request.getQuery());
        return new QueryResultResponseBody(request.getQuery().getResultsCount(), results);
    }

    /**
     * Initialize Relationship
     *
     * @param request
     * @return
     * @throws EntityException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws ClassNotFoundException
     */
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
        return (List)reflection.getAttribute(reflection.getAttributeField(clazz, request.getAttribute()), entity);
    }

    /**
     * Batch Save
     *
     * @param request
     * @throws EntityException
     * @throws ClassNotFoundException
     */
    public void saveEntities(EntityListRequestBody request) throws EntityException, ClassNotFoundException
    {
        final Class clazz = Class.forName(request.getType());

        final CollectionType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
        List<IManagedEntity> entities = null;

        try
        {
            entities = objectMapper.readValue((String)request.getEntities(), javaType);
        } catch (IOException e)
        {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.UNKNOWN_EXCEPTION);
        }

        persistenceManager.saveEntities(entities);
    }

    /**
     * Batch Delete
     *
     * @param request
     * @throws EntityException
     * @throws ClassNotFoundException
     */
    public void deleteEntities(EntityListRequestBody request) throws EntityException, ClassNotFoundException
    {
        final Class clazz = Class.forName(request.getType());

        final CollectionType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
        List entities = null;

        try
        {
            entities = objectMapper.readValue((String)request.getEntities(), javaType);
        } catch (IOException e)
        {
            throw new EntityClassNotFoundException(EntityClassNotFoundException.UNKNOWN_EXCEPTION);
        }

        persistenceManager.deleteEntities(entities);
    }

    /**
     * Execute Update
     *
     * @param request
     * @return
     * @throws EntityException
     */
    public QueryResultResponseBody executeUpdate(EntityQueryBody request) throws EntityException
    {
        final int results = persistenceManager.executeUpdate(request.getQuery());
        return new QueryResultResponseBody(request.getQuery().getMaxResults(), results);
    }

    /**
     * Execute Delete
     *
     * @param request
     * @return
     * @throws EntityException
     */
    public QueryResultResponseBody executeDelete(EntityQueryBody request) throws EntityException
    {
        final int results = persistenceManager.executeDelete(request.getQuery());
        return new QueryResultResponseBody(request.getQuery().getMaxResults(), results);
    }

    /**
     * Exists
     *
     * @param body
     * @return
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws EntityException
     */
    public boolean exists(EntityRequestBody body) throws ClassNotFoundException, IllegalAccessException, InstantiationException, EntityException {
        Class clazz = Class.forName(body.getType());

        IManagedEntity entity = null;
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
     * @param request
     * @throws EntityException
     * @throws ClassNotFoundException
     */
    public void saveRelationshipsForEntity(SaveRelationshipRequestBody request) throws EntityException, ClassNotFoundException
    {
        final Class clazz = Class.forName(request.getType());
        IManagedEntity entity = (IManagedEntity)objectMapper.convertValue(request.getEntity(), clazz);

        persistenceManager.saveRelationshipsForEntity(entity, request.getRelationship(), request.getIdentifiers());
    }
}
