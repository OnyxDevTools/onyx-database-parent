package com.onyx.relationship.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.EntityException;
import com.onyx.helpers.PartitionContext;
import com.onyx.map.MapBuilder;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.annotations.RelationshipType;
import com.onyx.record.RecordController;
import com.onyx.relationship.RelationshipReference;
import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Created by timothy.osborn on 2/5/15.
 */
public class AbstractRelationshipController extends PartitionContext
{

    protected RelationshipDescriptor relationshipDescriptor;
    protected EntityDescriptor entityDescriptor;
    protected RecordController recordController;

    protected RelationshipDescriptor defaultInverseRelationshipDescriptor;

    protected MapBuilder dataFile = null;
    protected MapBuilder defaultInverseDataFile = null;

    protected SchemaContext context;

    public AbstractRelationshipController(EntityDescriptor entityDescriptor, RelationshipDescriptor relationshipDescriptor, SchemaContext context) throws EntityException
    {
        super(context, context.getBaseDescriptorForEntity(relationshipDescriptor.getInverseClass()));
        this.context = context;

        // Assign the Entity Details
        this.relationshipDescriptor = relationshipDescriptor;
        this.entityDescriptor = entityDescriptor;
        this.recordController = context.getRecordController(entityDescriptor);

        IManagedEntity tmpEntity = EntityDescriptor.createNewEntity(relationshipDescriptor.getInverseClass());

        // Get the inverse entity details

        // If there is an inverse get the inverse relationship descriptor for saving the inverse
        if(relationshipDescriptor.getInverse() != null && relationshipDescriptor.getInverse().length() > 0)
        {
            this.defaultInverseRelationshipDescriptor = defaultDescriptor.getRelationships().get(relationshipDescriptor.getInverse());
        }

        // Get the data files
        this.dataFile = context.getDataFile(entityDescriptor);
        this.defaultInverseDataFile = context.getDataFile(defaultDescriptor);
    }

    /**
     * Save the inverse relationship, this will handle both to many relationships and to one relationships
     *
     * @param parentIdentifier
     * @param childIdentifier
     */
    protected void saveInverseRelationship(IManagedEntity parentEntity, IManagedEntity childEntity, RelationshipReference parentIdentifier, RelationshipReference childIdentifier) throws EntityException
    {
        // There is no inverse defined, nothing left to do
        if(defaultInverseRelationshipDescriptor == null)
            return;

        // This is a to many relationship
        if(defaultInverseRelationshipDescriptor.getRelationshipType() == RelationshipType.MANY_TO_MANY
                || defaultInverseRelationshipDescriptor.getRelationshipType() == RelationshipType.ONE_TO_MANY )
        {
            // Get the Data Map that corresponds to the inverse relationship
            final Map<Object, Set<Object>> relationshipMap = getDataFileForEntity(childEntity).getHashMap(defaultDescriptor.getClazz().getCanonicalName() + defaultInverseRelationshipDescriptor.getName());

            // Synchronized since we are saving the entire set
            synchronized (relationshipMap)
            {
                // Push it on the toManyRelationships
                Object toManyRelationshipsObj = relationshipMap.get(childIdentifier);
                Set<Object> toManyRelationships = null;

                // The purpose of this check is to ensure a relationship that has gone from a to one relationship
                // and is now a to many can have a fallback and convert it from a to one to a to many.
                if(toManyRelationshipsObj instanceof RelationshipReference)
                {
                    toManyRelationships = new HashSet();
                    toManyRelationships.add(toManyRelationshipsObj);
                }
                else
                {
                    toManyRelationships = (Set<Object>) toManyRelationshipsObj;
                }

                // If it does not exist create the relationship reference set
                if(toManyRelationships == null)
                {
                    toManyRelationships = new HashSet();
                }
                toManyRelationships.add(parentIdentifier);

                // Save the relationship by
                relationshipMap.put(childIdentifier, toManyRelationships);
            }
        }
        // It is a to One Relationship
        else
        {
            final Map<Object, Object> relationshipMap = getDataFileForEntity(childEntity).getHashMap(defaultDescriptor.getClazz().getCanonicalName() + defaultInverseRelationshipDescriptor.getName());
            relationshipMap.put(childIdentifier, parentIdentifier);

            setRelationshipValue(defaultInverseRelationshipDescriptor, childEntity, parentEntity);
        }
    }

    /**
     * Save the inverse relationship, this will handle both to many relationships and to one relationships
     *
     * @param parentIdentifier
     * @param childIdentifier
     */
    protected void deleteInverseRelationshipReference(RelationshipReference parentIdentifier, RelationshipReference childIdentifier) throws EntityException
    {
        // There is no inverse defined, nothing left to do
        if(defaultInverseRelationshipDescriptor == null)
            return;

        // This is a to many relationship
        if(defaultInverseRelationshipDescriptor.getRelationshipType() == RelationshipType.MANY_TO_MANY
                || defaultInverseRelationshipDescriptor.getRelationshipType() == RelationshipType.ONE_TO_MANY )
        {
            // Get the Data Map that corresponds to the inverse relationship
            final Map<Object, Set<Object>> relationshipMap = getDataFileWithPartitionId(childIdentifier.partitionId).getHashMap(defaultDescriptor.getClazz().getCanonicalName() + defaultInverseRelationshipDescriptor.getName());

            // Synchronized since we are saving the entire set
            synchronized (relationshipMap)
            {
                // Push it on the toManyRelationships
                Set<Object> toManyRelationships = relationshipMap.get(childIdentifier);

                if(toManyRelationships != null)
                {
                    toManyRelationships.remove(parentIdentifier);

                    // Save the relationship by
                    relationshipMap.put(childIdentifier, toManyRelationships);
                }

            }
        }
        // It is a to One Relationship
        else
        {
            final Map<Object, Object> relationshipMap = getDataFileWithPartitionId(parentIdentifier.partitionId).getHashMap(defaultDescriptor.getClazz().getCanonicalName() + defaultInverseRelationshipDescriptor.getName());
            relationshipMap.remove(childIdentifier);
        }
    }

    /**
     * Helper that uses reflection to get the relationship object, for a to one relationship
     *
     * @param entity
     * @return
     * @throws com.onyx.exception.AttributeMissingException
     */
    protected IManagedEntity getRelationshipValue(IManagedEntity entity) throws AttributeMissingException
    {
        try
        {
            final Field relationshipField = ReflectionUtil.getField(entity.getClass(), relationshipDescriptor.getName());
            if (!relationshipField.isAccessible())
            {
                relationshipField.setAccessible(true);
            }
            IManagedEntity inverse = (IManagedEntity) relationshipField.get(entity);
            return inverse;
        } catch (IllegalAccessException e)
        {
            throw new AttributeMissingException(AttributeMissingException.ILLEGAL_ACCESS_ATTRIBUTE);
        }
    }

    /**
     * Helper that uses reflection to get the relationship object
     *
     * @param entity
     * @return
     * @throws com.onyx.exception.AttributeMissingException
     */
    public static void setRelationshipValue(RelationshipDescriptor relationshipDescriptor, IManagedEntity entity, Object child) throws AttributeMissingException
    {
        final OffsetField relationshipField = ReflectionUtil.getOffsetField(entity.getClass(), relationshipDescriptor.getName());
        ReflectionUtil.setAny(entity, child, relationshipField);
    }

    /**
     * Get Relationship Values for to many relationship
     *
     * @param relationshipDescriptor
     * @param entity
     * @return
     * @throws AttributeMissingException
     */
    public static List getRelationshipListValue(RelationshipDescriptor relationshipDescriptor, IManagedEntity entity) throws AttributeMissingException
    {
        try
        {
            final Field relationshipField = ReflectionUtil.getField(entity.getClass(), relationshipDescriptor.getName());
            if(!relationshipField.isAccessible())
            {
                relationshipField.setAccessible(true);
            }
            List inverse = (List)relationshipField.get(entity);
            return inverse;
        } catch (IllegalAccessException e)
        {
            throw new AttributeMissingException(AttributeMissingException.ILLEGAL_ACCESS_ATTRIBUTE);
        }
    }

}
