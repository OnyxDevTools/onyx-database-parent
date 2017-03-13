package com.onyx.record;

import com.onyx.descriptor.BaseDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.exception.EntityCallbackException;
import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;
import com.onyx.diskmap.OrderedDiskMap;
import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.util.Map;
import java.util.Set;

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * Base implementation of the record controller
 */
@SuppressWarnings("unchecked")
public abstract class AbstractRecordController
{
    protected DiskMap<Object, IManagedEntity> records = null;
    protected final EntityDescriptor entityDescriptor;
    @SuppressWarnings("unused")
    protected final String contextId;

    /**
     * Constructor
     *
     * @param descriptor Entity Descriptor
     * @param context Schema context
     */
    @SuppressWarnings("WeakerAccess")
    public AbstractRecordController(EntityDescriptor descriptor, SchemaContext context)
    {
        this.contextId = context.getContextId();
        this.entityDescriptor = descriptor;
        MapBuilder dataFile = context.getDataFile(entityDescriptor);
        records = (DiskMap)dataFile.getHashMap(entityDescriptor.getClazz().getName(), descriptor.getIdentifier().getLoadFactor());
    }

    /**
     * Get an entity by primary key
     *
     * @param primaryKey Identifier of an entity
     * @return Entity if it exist
     */
    @SuppressWarnings({"WeakerAccess", "RedundantThrows"})
    public IManagedEntity getWithId(Object primaryKey) throws EntityException
    {
        return records.get(primaryKey);
    }

    /**
     * Returns true if the record exists in database
     *
     * @param entity Entity to check
     * @return Whether it exists
     */
    @SuppressWarnings("unused")
    public boolean exists(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier key
        final Object identifierValue = getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

        return records.containsKey(identifierValue);
    }

    /**
     * Returns true if the records contain a primary key
     *
     * @param primaryKey Idnetifier of entity
     * @return Whether that id is taken
     */
    @SuppressWarnings("unused")
    public boolean existsWithId(Object primaryKey) throws EntityException
    {
        return records.containsKey(primaryKey);
    }

    /**
     * Delete
     *
     * @param entity Entity to delete
     * @throws EntityException Error deleting an entity
     */
    @SuppressWarnings("unused")
    public void delete(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier key
        final Object identifierValue = getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

        invokePreRemoveCallback(entity);

        this.deleteWithId(identifierValue);

        invokePostRemoveCallback(entity);
    }

    /**
     * Delete with ID
     *
     * @param primaryKey Identifier of an entity
     */
    @SuppressWarnings("WeakerAccess")
    public void deleteWithId(Object primaryKey)
    {
        records.remove(primaryKey);
    }

    /**
     * Get an entity by the entity with populated primary key
     *
     * @param entity Entity to get.  Its id must be defined
     * @return Hydrated entity
     */
    @SuppressWarnings("unused")
    public IManagedEntity get(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier key
        final Object identifierValue = getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

        return getWithId(identifierValue);
    }

    /**
     * Retrieves the index key from the entity using reflection
     *
     * @param entity Entity to retrieve index value of
     * @return Index value of entity
     * @throws com.onyx.exception.AttributeMissingException Attribute does not exist
     */
    public static Object getIndexValueFromEntity(IManagedEntity entity, BaseDescriptor indexDescriptor) throws AttributeMissingException
    {
        try {
            return ReflectionUtil.getAny(entity, indexDescriptor.getField());
        } catch (AttributeTypeMismatchException e) {
            throw new AttributeMissingException(AttributeMissingException.ILLEGAL_ACCESS_ATTRIBUTE, e);
        }
    }

    /**
     * Invoke Pre Insert callback
     *
     * @param entity Entity to invoke pre insert callback upon
     * @throws EntityCallbackException Error happened during callback
     */
    protected void invokePreInsertCallback(IManagedEntity entity) throws EntityCallbackException
    {
        this.invokePrePersistCallback(entity);
        if(this.entityDescriptor.getPreInsertCallback() != null
                && !((ManagedEntity)entity).ignoreListeners)
        {
            try
            {
                this.entityDescriptor.getPreInsertCallback().invoke(entity);
            } catch (Exception e)
            {
                throw(new EntityCallbackException(this.entityDescriptor.getPreInsertCallback().getName(), EntityCallbackException.INVOCATION, e));
            }
        }
    }

    /**
     * Invoke Pre Update Callback on entity
     *
     * @param entity Entity to invoke callback on
     * @throws EntityCallbackException Error happened during callback
     */
    protected void invokePreUpdateCallback(IManagedEntity entity) throws EntityCallbackException
    {
        this.invokePrePersistCallback(entity);
        if(this.entityDescriptor.getPreUpdateCallback() != null
                && !((ManagedEntity)entity).ignoreListeners)
        {
            try
            {
                this.entityDescriptor.getPreUpdateCallback().invoke(entity);
            } catch (Exception e)
            {
                throw(new EntityCallbackException(this.entityDescriptor.getPreUpdateCallback().getName(), EntityCallbackException.INVOCATION, e));
            }
        }
    }

    /**
     * Invoke Pre Remove callback on entity
     *
     * @param entity Entity to invoke callback on
     * @throws EntityCallbackException Error happened during callback
     */
    protected void invokePreRemoveCallback(IManagedEntity entity) throws EntityCallbackException
    {
        if(this.entityDescriptor.getPreRemoveCallback() != null
                && !((ManagedEntity)entity).ignoreListeners)
        {
            try
            {
                this.entityDescriptor.getPreRemoveCallback().invoke(entity);
            } catch (Exception e)
            {
                throw(new EntityCallbackException(this.entityDescriptor.getPreRemoveCallback().getName(), EntityCallbackException.INVOCATION, e));
            }
        }
    }

    /**
     * Invoke Pre Persist callback on entity
     *
     * @param entity Entity to invoke callback on
     * @throws EntityCallbackException Error happened during callback
     */
    protected void invokePrePersistCallback(IManagedEntity entity) throws EntityCallbackException
    {
        if(this.entityDescriptor.getPrePersistCallback() != null
                && !((ManagedEntity)entity).ignoreListeners)
        {
            try
            {
                this.entityDescriptor.getPrePersistCallback().invoke(entity);
            } catch (Exception e)
            {
                throw(new EntityCallbackException(this.entityDescriptor.getPrePersistCallback().getName(), EntityCallbackException.INVOCATION, e));
            }
        }
    }

    /**
     * Invoke Post insert callback on entity
     *
     * @param entity Entity to invoke callback on
     * @throws EntityCallbackException Error happened during callback
     */
    protected void invokePostInsertCallback(IManagedEntity entity) throws EntityCallbackException
    {
        if(this.entityDescriptor.getPostInsertCallback() != null
                && !((ManagedEntity)entity).ignoreListeners)
        {
            try
            {
                this.entityDescriptor.getPostInsertCallback().invoke(entity);
            } catch (Exception e)
            {
                throw(new EntityCallbackException(this.entityDescriptor.getPostInsertCallback().getName(), EntityCallbackException.INVOCATION, e));
            }
        }
    }

    /**
     * Invoke Post Update callback on entity
     *
     * @param entity Entity to invoke callback on
     * @throws EntityCallbackException Error happened during callback
     */
    protected void invokePostUpdateCallback(IManagedEntity entity) throws EntityCallbackException
    {
        if(this.entityDescriptor.getPostUpdateCallback() != null
                && !((ManagedEntity)entity).ignoreListeners)
        {
            try
            {
                this.entityDescriptor.getPostUpdateCallback().invoke(entity);
            } catch (Exception e)
            {
                throw(new EntityCallbackException(this.entityDescriptor.getPostUpdateCallback().getName(), EntityCallbackException.INVOCATION, e));
            }
        }
    }

    /**
     * Invoke Post Remove Callback on entity
     *
     * @param entity Entity to invoke callback on
     * @throws EntityCallbackException Error happened during callback
     */
    protected void invokePostRemoveCallback(IManagedEntity entity) throws EntityCallbackException
    {
        if(this.entityDescriptor.getPostRemoveCallback() != null
                && !((ManagedEntity)entity).ignoreListeners)
        {
            try
            {
                this.entityDescriptor.getPostRemoveCallback().invoke(entity);
            } catch (Exception e)
            {
                throw(new EntityCallbackException(this.entityDescriptor.getPostRemoveCallback().getName(), EntityCallbackException.INVOCATION, e));
            }
        }
    }

    /**
     * Invoke Post Persist callback on entity
     *
     * @param entity Entity to invoke callback on
     * @throws EntityCallbackException Error happened during callback
     */
    protected void invokePostPersistCallback(IManagedEntity entity) throws EntityCallbackException
    {
        if(this.entityDescriptor.getPostPersistCallback() != null
                && !((ManagedEntity)entity).ignoreListeners)
        {
            try
            {
                this.entityDescriptor.getPostPersistCallback().invoke(entity);
            } catch (Exception e)
            {
                throw(new EntityCallbackException(this.entityDescriptor.getPostPersistCallback().getName(), EntityCallbackException.INVOCATION, e));
            }
        }
    }

    /**
     * Retrieves the index key from the entity using reflection
     *
     * @param entity Entity to set index value for
     * @throws AttributeMissingException Attribute does not exist
     */
    protected void setIndexValueForEntity(IManagedEntity entity, Object value) throws AttributeMissingException
    {
        final OffsetField field = entityDescriptor.getIdentifier().getField();
        if(field == null)
        {
            // Hmmm, setting accessable didnt work, must not have permission
            throw new AttributeMissingException(AttributeMissingException.ENTITY_MISSING_ATTRIBUTE);
        }
        ReflectionUtil.setAny(entity, value, field);
    }

    /**
     * Retrieves the index key from the entity using reflection
     *
     * @param entity Entity to set attribute
     * @throws AttributeMissingException Attribute does not exist
     */
    @SuppressWarnings("unused")
    public static void setIndexValueForEntity(IManagedEntity entity, Object value, SchemaContext context) throws EntityException
    {

        // Use reflection to get the key
        final OffsetField field = context.getDescriptorForEntity(entity).getIdentifier().getField();

        try
        {
            ReflectionUtil.setAny(entity, value, field);
        }  catch (ClassCastException e)
        {
            throw new AttributeTypeMismatchException(AttributeTypeMismatchException.ATTRIBUTE_TYPE_MISMATCH, field.type, value.getClass(), field.name);
        }
    }

    /**
     * Returns the record reference ID
     *
     * @param primaryKey Identifier for entity
     * @return Entity reference id
     */
    @SuppressWarnings("unused")
    public long getReferenceId(Object primaryKey) throws EntityException
    {
        return records.getRecID(primaryKey);
    }


    /**
     * Returns the object using the reference ID
     *
     * @param referenceId Entity reference id
     * @return Hydrated entity
     */
    @SuppressWarnings("unused")
    public IManagedEntity getWithReferenceId(long referenceId) throws EntityException
    {
        return records.getWithRecID(referenceId);
    }

    /**
     * Returns a structure of the entity with a reference id
     *
     * @param referenceId Entity reference id
     * @return Entity as a map
     */
    @SuppressWarnings("unused")
    public Map getMapWithReferenceId(long referenceId) throws EntityException
    {
        return records.getMapWithRecID(referenceId);
    }

    /**
     * Get a specific attribute with reference Id
     *
     * @param attribute Name of attribute to get
     * @param referenceId location of record within storage
     * @return Attribute key
     */
    @SuppressWarnings("unused")
    public Object getAttributeWithReferenceId(OffsetField attribute, long referenceId) throws AttributeTypeMismatchException {
        return records.getAttributeWithRecID(attribute, referenceId);
    }

    /**
     * Find all objects greater than the key parameter.  The underlying data
     * structure should be sorted
     *
     * @param indexValue The value to compare
     * @param includeValue Include whether the keys match what you pass in as index value
     * @return A set of REFERENCES not the actual values
     * @throws EntityException Error when reading the store
     */
    @SuppressWarnings("unused")
    public Set<Long> findAllAbove(Object indexValue, boolean includeValue) throws EntityException
    {
        return ((OrderedDiskMap)records).above(indexValue, includeValue);
    }

    /**
     * Find all objects less than the key parameter.  The underlying data
     * structure should be sorted
     *
     * @param indexValue The value to compare
     * @param includeValue Include whether the keys match what you pass in as index value
     * @return A set of REFERENCES not the actual values
     * @throws EntityException Error when reading the store
     */
    @SuppressWarnings("unused")
    public Set<Long> findAllBelow(Object indexValue, boolean includeValue) throws EntityException
    {
        return ((OrderedDiskMap)records).below(indexValue, includeValue);
    }
}
