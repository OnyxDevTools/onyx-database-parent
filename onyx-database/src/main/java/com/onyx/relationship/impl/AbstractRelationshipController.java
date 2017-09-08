package com.onyx.relationship.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.OnyxException;
import com.onyx.helpers.PartitionContext;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.RelationshipType;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.RecordController;
import com.onyx.relationship.RelationshipReference;
import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * Base class for handling relationships
 */
class AbstractRelationshipController extends PartitionContext
{

    @SuppressWarnings("WeakerAccess")
    protected RelationshipDescriptor relationshipDescriptor;
    @SuppressWarnings("WeakerAccess")
    protected EntityDescriptor entityDescriptor;
    @SuppressWarnings("WeakerAccess")
    protected RecordController recordController;

    private RelationshipDescriptor defaultInverseRelationshipDescriptor;

    static final int RELATIONSHIP_MAP_LOAD_FACTOR = 2;

    AbstractRelationshipController(EntityDescriptor entityDescriptor, RelationshipDescriptor relationshipDescriptor, SchemaContext context) throws OnyxException
    {
        super(context, context.getBaseDescriptorForEntity(relationshipDescriptor.getInverseClass()));

        // Assign the Entity Details
        this.relationshipDescriptor = relationshipDescriptor;
        this.entityDescriptor = entityDescriptor;
        this.recordController = context.getRecordController(entityDescriptor);

        // Get the inverse entity details

        // If there is an inverse get the inverse relationship descriptor for saving the inverse
        if(relationshipDescriptor.getInverse() != null && relationshipDescriptor.getInverse().length() > 0)
        {
            this.defaultInverseRelationshipDescriptor = defaultDescriptor.getRelationships().get(relationshipDescriptor.getInverse());
        }
    }

    /**
     * Save the inverse relationship, this will handle both to many relationships and to one relationships
     *
     * @param parentIdentifier Parent entity identifier
     * @param childIdentifier Child entity identifier
     */
    @SuppressWarnings({"unchecked", "SynchronizationOnLocalVariableOrMethodParameter"})
    void saveInverseRelationship(IManagedEntity parentEntity, IManagedEntity childEntity, RelationshipReference parentIdentifier, RelationshipReference childIdentifier) throws OnyxException
    {
        // There is no inverse defined, nothing left to do
        if(defaultInverseRelationshipDescriptor == null)
            return;

        // This is a to many relationship
        if(defaultInverseRelationshipDescriptor.getRelationshipType() == RelationshipType.MANY_TO_MANY
                || defaultInverseRelationshipDescriptor.getRelationshipType() == RelationshipType.ONE_TO_MANY )
        {
            // Get the Data Map that corresponds to the inverse relationship
            final Map<Object, Set<Object>> relationshipMap = getDataFileForEntity(childEntity).getHashMap(defaultDescriptor.getEntityClass().getName() + defaultInverseRelationshipDescriptor.getName(), RELATIONSHIP_MAP_LOAD_FACTOR);

            // Synchronized since we are saving the entire set
            synchronized (relationshipMap)
            {
                // Push it on the toManyRelationships
                Object toManyRelationshipsObj = relationshipMap.get(childIdentifier);
                Set<Object> toManyRelationships;

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
                synchronized (toManyRelationships) {
                    toManyRelationships.add(parentIdentifier);
                }

                // Save the relationship by
                relationshipMap.put(childIdentifier, toManyRelationships);
            }
        }
        // It is a to One Relationship
        else
        {
            final Map<Object, Object> relationshipMap = getDataFileForEntity(childEntity).getHashMap(defaultDescriptor.getEntityClass().getName() + defaultInverseRelationshipDescriptor.getName(), RELATIONSHIP_MAP_LOAD_FACTOR);
            relationshipMap.put(childIdentifier, parentIdentifier);

            setRelationshipValue(defaultInverseRelationshipDescriptor, childEntity, parentEntity);
        }
    }

    /**
     * Save the inverse relationship, this will handle both to many relationships and to one relationships
     *
     * @param parentIdentifier Parent entity identifier
     * @param childIdentifier Child entity identifier
     */
    @SuppressWarnings({"unchecked", "SynchronizationOnLocalVariableOrMethodParameter"})
    void deleteInverseRelationshipReference(RelationshipReference parentIdentifier, RelationshipReference childIdentifier) throws OnyxException
    {
        // There is no inverse defined, nothing left to do
        if(defaultInverseRelationshipDescriptor == null)
            return;

        // This is a to many relationship
        if(defaultInverseRelationshipDescriptor.getRelationshipType() == RelationshipType.MANY_TO_MANY
                || defaultInverseRelationshipDescriptor.getRelationshipType() == RelationshipType.ONE_TO_MANY )
        {
            // Get the Data Map that corresponds to the inverse relationship
            final Map<Object, Set<Object>> relationshipMap = getDataFileWithPartitionId(childIdentifier.partitionId).getHashMap(defaultDescriptor.getEntityClass().getName() + defaultInverseRelationshipDescriptor.getName(), RELATIONSHIP_MAP_LOAD_FACTOR);

            // Synchronized since we are saving the entire set
            synchronized (relationshipMap)
            {
                // Push it on the toManyRelationships
                Set<Object> toManyRelationships = relationshipMap.get(childIdentifier);

                if(toManyRelationships != null)
                {
                    synchronized (toManyRelationships) {
                        toManyRelationships.remove(parentIdentifier);
                    }

                    // Save the relationship by
                    relationshipMap.put(childIdentifier, toManyRelationships);
                }

            }
        }
        // It is a to One Relationship
        else
        {
            final Map<Object, Object> relationshipMap = getDataFileWithPartitionId(parentIdentifier.partitionId).getHashMap(defaultDescriptor.getEntityClass().getName() + defaultInverseRelationshipDescriptor.getName(), RELATIONSHIP_MAP_LOAD_FACTOR);
            relationshipMap.remove(childIdentifier);
        }
    }

    /**
     * Helper that uses reflection to get the relationship object, for a to one relationship
     *
     * @param entity Entity to reflect
     * @return Entity relationship value
     * @throws com.onyx.exception.AttributeMissingException relationship property does not exist
     */
    IManagedEntity getRelationshipValue(IManagedEntity entity) throws AttributeMissingException
    {
        try
        {
            final OffsetField relationshipField = relationshipDescriptor.getField();
            return (IManagedEntity)ReflectionUtil.getAny(entity, relationshipField);
        } catch (OnyxException e)
        {
            throw new AttributeMissingException(AttributeMissingException.ILLEGAL_ACCESS_ATTRIBUTE);
        }
    }

    /**
     * Helper that uses reflection to get the relationship object
     *
     * @param entity Entity to set relationship value on
     * @throws com.onyx.exception.AttributeMissingException property does not exsit
     */
    static void setRelationshipValue(RelationshipDescriptor relationshipDescriptor, IManagedEntity entity, Object child) throws AttributeMissingException
    {
        final OffsetField relationshipField = relationshipDescriptor.getField();
        ReflectionUtil.setAny(entity, child, relationshipField);
    }

    /**
     * Get Relationship Values for to many relationship
     *
     * @param relationshipDescriptor Entity relationship descriptor
     * @param entity Entity to get relationship list
     * @return Relationship list
     * @throws AttributeMissingException Property does not exist
     */
    static List getRelationshipListValue(RelationshipDescriptor relationshipDescriptor, IManagedEntity entity) throws AttributeMissingException
    {
        try
        {
            final OffsetField relationshipField = relationshipDescriptor.getField();
            return (List)ReflectionUtil.getAny(entity, relationshipField);
        } catch (OnyxException e)
        {
            throw new AttributeMissingException(AttributeMissingException.ILLEGAL_ACCESS_ATTRIBUTE);
        }
    }

}
