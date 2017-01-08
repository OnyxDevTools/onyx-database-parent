package com.onyx.helpers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryPartitionMode;
import com.onyx.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by timothy.osborn on 3/5/15.
 */
public class PartitionHelper
{

    public static final String NULL_PARTITION = "";

    /**
     * Helper for identifiying whether the field is a partition field
     * @param fieldName
     * @param entity
     * @param baseDescriptor
     * @return
     */
    public static boolean isPartitionField(String fieldName, IManagedEntity entity, EntityDescriptor baseDescriptor)
    {
        return (baseDescriptor.getPartition() != null && baseDescriptor.getPartition().getName().equals(fieldName));
    }

    /**
     * Helper for detecting whether an entity is partitionable
     * @param entity
     * @param context
     * @return
     * @throws EntityException
     */
    public static boolean hasPartitionField(IManagedEntity entity, SchemaContext context) throws EntityException
    {
        final EntityDescriptor baseDescriptor = context.getDescriptorForEntity(entity, "");
        return (baseDescriptor.getPartition() != null);
    }

    /**
     * Helper for detecting whether an entity is partition-able
     * @param type
     * @param context
     * @return
     * @throws EntityException
     */
    public static boolean hasPartitionField(Class type, SchemaContext context) throws EntityException
    {
        final EntityDescriptor baseDescriptor = context.getBaseDescriptorForEntity(type);
        return (baseDescriptor.getPartition() != null);
    }

    /**
     * Helper for getting the partition key from an entity
     *
     * @param entity
     * @param context
     * @return
     * @throws EntityException
     */
    public static Object getPartitionFieldValue(IManagedEntity entity, SchemaContext context) throws EntityException
    {
        final EntityDescriptor baseDescriptor = context.getDescriptorForEntity(entity, "");
        if(baseDescriptor.getPartition() == null)
        {
            return NULL_PARTITION;
        }

        Object val = ReflectionUtil.getAny(entity, baseDescriptor.getPartition().getPartitionField());

        if(val == null)
        {
            return NULL_PARTITION;
        }
        return val;
    }

    /**
     * Set the partition field on a query based on the query criteria
     * @param query
     * @param context
     * @throws EntityException
     */
    public static void setPartitionIdForQuery(Query query, SchemaContext context) throws EntityException
    {
        if(hasPartitionField(query.getEntityType(), context) && (query.getPartition() == null || query.getPartition().equals("")))
        {
            Class queryClass = query.getEntityType();
            IManagedEntity entity = EntityDescriptor.createNewEntity(queryClass);
            final EntityDescriptor baseDescriptor = context.getDescriptorForEntity(entity, "");

            QueryCriteria criteria = query.getCriteria();
            setPartitionIdFromCriteria(criteria, query, baseDescriptor);
        }
    }

    /**
     * Recursive call to get and set the partition key from the query criteria
     *
     * @param criteria
     * @param query
     * @param baseDescriptor
     * @return
     */
    protected static boolean setPartitionIdFromCriteria(QueryCriteria criteria, Query query, EntityDescriptor baseDescriptor)
    {
        if(baseDescriptor.getPartition() != null || query.getPartition().equals(""))
        {
            if(baseDescriptor.getPartition().getName().equals(criteria.getAttribute()))
            {
                query.setPartition(criteria.getValue());
                return true;
            }
            else
            {
                for(QueryCriteria andCriteria : criteria.getAndCriteria())
                {
                    if(setPartitionIdFromCriteria(andCriteria, query, baseDescriptor))
                        return true;
                }

                for(QueryCriteria orCriteria : criteria.getOrCriteria())
                {
                    if(setPartitionIdFromCriteria(orCriteria, query, baseDescriptor))
                        return true;
                }
            }
        }

        if(query.getPartition() == null || query.getPartition().equals(""))
        {
            query.setPartition(QueryPartitionMode.ALL);
        }

        return false;
    }

    /**
     * Retrieves the index key from the entity using reflection
     *
     * @param entity
     * @return
     * @throws com.onyx.exception.AttributeMissingException
     */
    public static void setPartitionValueForEntity(IManagedEntity entity, Object value, SchemaContext context) throws EntityException
    {
        try
        {
            final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
            if(descriptor.getPartition() == null || value == null)
                return;

            // Use reflection to get the key
            final Field field = ReflectionUtil.getField(entity.getClass(), context.getDescriptorForEntity(entity).getPartition().getName());
            // If it is a private field, lets set it accessible
            if (!field.isAccessible())
                field.setAccessible(true);

            if(value instanceof String)
            {
                if (field.getType() == long.class)
                    field.set(entity, (long)Long.valueOf((String)value));
                else if (field.getType() == int.class)
                    field.set(entity, (int)Integer.valueOf((String)value));
                else if (field.getType() == Long.class)
                    field.set(entity, Long.valueOf((String)value));
                else if (field.getType() == Integer.class)
                    field.set(entity, Integer.valueOf((String)value));
                else if (field.getType() == Double.class)
                    field.set(entity, Double.valueOf((String)value));
                else if (field.getType() == double.class)
                    field.set(entity, (double)Double.valueOf((String)value));
                else if (field.getType() == Date.class)
                {
                    DateFormat format = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH);
                    try
                    {
                        field.set(entity, format.parse((String) value));
                    } catch (ParseException ignore)
                    {}
                }
                else
                    field.set(entity, field.getType().cast(value));
            }
            else
            {
                if (field.getType() == long.class)
                    field.set(entity, (long) value);
                else if (field.getType() == int.class && value != null && value.getClass() == Long.class)
                    field.set(entity, ((Long) value).intValue());
                else if (field.getType() == int.class)
                    field.set(entity, (int) value);
                else if (field.getType() == Long.class && value instanceof Integer)
                    field.set(entity, ((Integer) value).longValue());
                else if (field.getType() == Long.class)
                    field.set(entity, (Long) value);
                else if (field.getType() == Integer.class && value != null && value.getClass() == Long.class)
                    field.set(entity, ((Long) value).intValue());
                else if (field.getType() == Integer.class)
                    field.set(entity, (Integer) value);
                else if (field.getType() == Double.class)
                    field.set(entity, (Double) value);
                else if (field.getType() == double.class)
                    field.set(entity, (double) value);
                else if (field.getType() == Date.class)
                    field.set(entity, (Date) value);
                else
                    field.set(entity, field.getType().cast(value));
            }

        } catch (IllegalAccessException e)
        {
            // Hmmm, setting accessable didnt work, must not have permission
            throw new AttributeMissingException(AttributeMissingException.ILLEGAL_ACCESS_ATTRIBUTE, e);
        }
    }
}
