package com.onyx.record;

import com.onyx.descriptor.BaseDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.exception.EntityCallbackException;
import com.onyx.exception.EntityException;
import com.onyx.map.DiskMap;
import com.onyx.map.MapBuilder;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.Map;

/**
 * Created by timothy.osborn on 2/5/15.
 */
public abstract class AbstractRecordController
{
    protected DiskMap<Object, IManagedEntity> records = null;
    protected EntityDescriptor entityDescriptor;
    protected MapBuilder dataFile;
    protected SchemaContext context;

    /**
     * Constructor
     *
     * @param descriptor
     */
    public AbstractRecordController(EntityDescriptor descriptor, SchemaContext context)
    {
        this.context = context;
        this.entityDescriptor = descriptor;
        dataFile = context.getDataFile(entityDescriptor);
        records = (DiskMap)dataFile.getHashMap(entityDescriptor.getClazz().getCanonicalName());
    }

    /**
     * Get an entity by primary key
     *
     * @param primaryKey
     * @return
     */
    public IManagedEntity getWithId(Object primaryKey) throws EntityException
    {
        return records.get(primaryKey);
    }

    /**
     * Returns true if the record exists in database
     *
     * @param entity
     * @return
     */
    public boolean exists(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier value
        final Object identifierValue = getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

        return records.containsKey(identifierValue);
    }

    /**
     * Returns true if the records contain a primary key
     *
     * @param primaryKey
     * @return
     */
    public boolean existsWithId(Object primaryKey) throws EntityException
    {
        return records.containsKey(primaryKey);
    }

    /**
     * Delete
     *
     * @param entity
     * @throws EntityException
     */
    public void delete(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier value
        final Object identifierValue = getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

        invokePreRemoveCallback(entity);

        this.deleteWithId(identifierValue);

        invokePostRemoveCallback(entity);
    }

    /**
     * Delete with ID
     *
     * @param primaryKey
     */
    public void deleteWithId(Object primaryKey)
    {
        records.remove(primaryKey);
    }

    /**
     * Get an entity by the entity with populated primary key
     *
     * @param entity
     * @return
     */
    public IManagedEntity get(IManagedEntity entity) throws EntityException
    {
        // Get the Identifier value
        final Object identifierValue = getIndexValueFromEntity(entity, entityDescriptor.getIdentifier());

        return getWithId(identifierValue);
    }

    /**
     * Retrieves the index value from the entity using reflection
     *
     * @param entity
     * @return
     * @throws com.onyx.exception.AttributeMissingException
     */
    public static Object getIndexValueFromEntity(IManagedEntity entity, BaseDescriptor indexDescriptor) throws AttributeMissingException
    {
        try
        {
            // Use reflection to get the value
            final Field field = ReflectionUtil.getField(entity.getClass(), indexDescriptor.getName());
            // If it is a private field, lets set it accessible
            if (!field.isAccessible())
                field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException e)
        {
            // Hmmm, setting accessible didnt work, must not have permission
            throw new AttributeMissingException(AttributeMissingException.ILLEGAL_ACCESS_ATTRIBUTE, e);
        }
    }

    /**
     * Invoke Pre Insert callback
     *
     * @param entity
     * @throws EntityCallbackException
     */
    public void invokePreInsertCallback(IManagedEntity entity) throws EntityCallbackException
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
     * @param entity
     * @throws EntityCallbackException
     */
    public void invokePreUpdateCallback(IManagedEntity entity) throws EntityCallbackException
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
     * @param entity
     * @throws EntityCallbackException
     */
    public void invokePreRemoveCallback(IManagedEntity entity) throws EntityCallbackException
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
     * @param entity
     * @throws EntityCallbackException
     */
    public void invokePrePersistCallback(IManagedEntity entity) throws EntityCallbackException
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
     * @param entity
     * @throws EntityCallbackException
     */
    public void invokePostInsertCallback(IManagedEntity entity) throws EntityCallbackException
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
     * @param entity
     * @throws EntityCallbackException
     */
    public void invokePostUpdateCallback(IManagedEntity entity) throws EntityCallbackException
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
     * @param entity
     * @throws EntityCallbackException
     */
    public void invokePostRemoveCallback(IManagedEntity entity) throws EntityCallbackException
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
     * @param entity
     * @throws EntityCallbackException
     */
    public void invokePostPersistCallback(IManagedEntity entity) throws EntityCallbackException
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
     * Retrieves the index value from the entity using reflection
     *
     * @param entity
     * @return
     * @throws AttributeMissingException
     */
    public void setIndexValueForEntity(IManagedEntity entity, Object value) throws AttributeMissingException
    {
        try
        {
            // Use reflection to get the value
            final Field field = ReflectionUtil.getField(entity.getClass(), entityDescriptor.getIdentifier().getName());
            // If it is a private field, lets set it accessible
            if (!field.isAccessible())
                field.setAccessible(true);

            if(field.getType() == long.class)
                field.set(entity, (long)value);
            else if(field.getType() == int.class && value != null && value.getClass() == Long.class)
                field.set(entity, ((Long)value).intValue());
            else if(field.getType() == int.class)
                field.set(entity, (int)value);
            else if(field.getType() == Long.class && value instanceof Integer)
                field.set(entity, ((Integer)value).longValue());
            else if(field.getType() == Long.class)
                field.set(entity, (Long)value);
            else if(field.getType() == Integer.class && value != null && value.getClass() == Long.class )
                field.set(entity, ((Long)value).intValue());
            else if(field.getType() == Integer.class)
                field.set(entity, (Integer)value);
            else if(field.getType() == Double.class)
                field.set(entity, (Double)value);
            else if(field.getType() == double.class)
                field.set(entity, (double)value);
            else if(field.getType() == Date.class)
                field.set(entity, (Date)value);
            else
                field.set(entity, field.getType().cast(value));

        } catch (IllegalAccessException e)
        {
            // Hmmm, setting accessable didnt work, must not have permission
            throw new AttributeMissingException(AttributeMissingException.ILLEGAL_ACCESS_ATTRIBUTE, e);
        }
    }


    /**
     * Converts a value from a String to a type casted object
     *
     * @param type
     * @param value
     * @return casted object
     */
    public static Object convertValueTo(Class type, Object value)
    {
        if(value == null)
            return value;
        if (type == long.class || type == Long.class)
            return Long.valueOf((String)value);
        else if(type == int.class || type == Integer.class)
            return Integer.valueOf((String) value);
        else if(type == double.class || type == Double.class)
            return Double.valueOf((String) value);
        else if(type == Date.class)
            return new Date(Long.valueOf((String) value));

        return null;
    }

    /**
     * Retrieves the index value from the entity using reflection
     *
     * @param entity
     * @return
     * @throws AttributeMissingException
     */
    public static void setIndexValueForEntity(IManagedEntity entity, Object value, SchemaContext context) throws EntityException
    {

        // Use reflection to get the value
        final Field field = ReflectionUtil.getField(entity.getClass(), context.getDescriptorForEntity(entity).getIdentifier().getName());

        try
        {
            if(value instanceof String && field.getType() != String.class)
                value = convertValueTo(field.getType(), value);

            // If it is a private field, lets set it accessible
            if (!field.isAccessible())
                field.setAccessible(true);

            if(field.getType() == long.class)
                field.set(entity, (long) value);
            else if(field.getType() == int.class && value != null && value.getClass() == Long.class)
                field.set(entity, ((Long)value).intValue());
            else if(field.getType() == int.class)
                field.set(entity, (int)value);
            else if(field.getType() == Long.class && value instanceof Integer)
                field.set(entity, ((Integer)value).longValue());
            else if(field.getType() == Long.class)
                field.set(entity, (Long)value);
            else if(field.getType() == Integer.class && value != null && value.getClass() == Long.class )
                field.set(entity, ((Long)value).intValue());
            else if(field.getType() == Integer.class)
                field.set(entity, (Integer)value);
            else if(field.getType() == Double.class)
                field.set(entity, (Double)value);
            else if(field.getType() == double.class)
                field.set(entity, (double)value);
            else if(field.getType() == Date.class)
                field.set(entity, (Date)value);
            else
                field.set(entity, field.getType().cast(value));

        } catch (IllegalAccessException e)
        {
            // Hmmm, setting accessable didnt work, must not have permission
            throw new AttributeMissingException(AttributeMissingException.ILLEGAL_ACCESS_ATTRIBUTE, e);
        } catch (ClassCastException e)
        {
            throw new AttributeTypeMismatchException(AttributeTypeMismatchException.ATTRIBUTE_TYPE_MISMATCH, field.getType(), value.getClass(), field.getName());
        }
    }

    /**
     * Returns the record reference ID
     *
     * @param primaryKey
     * @return
     * @throws EntityException
     */
    public long getReferenceId(Object primaryKey) throws EntityException
    {
        return records.getRecID(primaryKey);
    }


    /**
     * Returns the object using the reference ID
     *
     * @param referenceId
     * @return
     */
    public IManagedEntity getWithReferenceId(long referenceId) throws EntityException
    {
        return records.getWithRecID(referenceId);
    }

    /**
     * Returns a map of the entity with a reference id
     *
     * @param referenceId
     * @return
     * @throws EntityException
     */
    public Map getMapWithReferenceId(long referenceId) throws EntityException
    {
        return records.getMapWithRecID(referenceId);
    }

    /**
     * Get a specific attribute with reference Id
     *
     * @param attribute Name of attribute to get
     * @param referenceId location of record within storage
     * @return Attribute value
     */
    public Object getAttributeWithReferenceId(String attribute, long referenceId) throws AttributeTypeMismatchException {
        return records.getAttributeWithRecID(attribute, referenceId);
    }


}
