package com.onyx.helpers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.exception.InvalidRelationshipTypeException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.relationship.EntityRelationshipManager;
import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.RelationshipReference;

/**
 * Created by timothy.osborn on 12/23/14.
 *
 * Helper methds for a relationship
 */
public class RelationshipHelper
{

    /**
     * Save all relationships for an entity
     *
     * @param entity Entity to save relationships for
     * @param manager Relationship manager keeping track of what was already done
     */
    public static void saveAllRelationshipsForEntity(IManagedEntity entity, EntityRelationshipManager manager, SchemaContext context) throws EntityException
    {
        String partitionValue = String.valueOf(PartitionHelper.getPartitionFieldValue(entity, context));

        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionValue);
        if(descriptor.getRelationships().size() == 0)
        {
            return;
        }

        if(!manager.contains(entity, descriptor.getIdentifier()))
        {
            manager.add(entity, descriptor.getIdentifier());
            for (RelationshipDescriptor relationshipDescriptor : descriptor.getRelationships().values())
            {
                final RelationshipController relationshipController = context.getRelationshipController(relationshipDescriptor);
                if(relationshipController == null)
                {
                    throw new InvalidRelationshipTypeException(InvalidRelationshipTypeException.INVERSE_RELATIONSHIP_INVALID + " Class:" + relationshipDescriptor.getParentClass().getName() + " Inverse:" + relationshipDescriptor.getInverse());
                }
                relationshipController.saveRelationshipForEntity(entity, manager);
            }
        }
    }

    /**
     * Delete all relationships for an entity
     *
     * @param entity Entity to save relationships for
     * @param relationshipManager Relationship manager keeping track of what was already done
     * @param context Schema context
     */
    public static void deleteAllRelationshipsForEntity(IManagedEntity entity, EntityRelationshipManager relationshipManager, SchemaContext context) throws EntityException
    {
        String partitionValue = String.valueOf(PartitionHelper.getPartitionFieldValue(entity, context));
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionValue);

        RelationshipReference entityId;

        if (!PartitionHelper.NULL_PARTITION.equals(partitionValue) && partitionValue != null)
        {
            final SystemPartitionEntry partition = context.getPartitionWithValue(descriptor.getClazz(), partitionValue);
            entityId = new RelationshipReference(AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier()), partition.getIndex());
        } else
        {
            entityId = new RelationshipReference(AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier()), 0);
        }

        for(RelationshipDescriptor relationshipDescriptor : descriptor.getRelationships().values())
        {
            final RelationshipController relationshipController = context.getRelationshipController(relationshipDescriptor);
            relationshipController.deleteRelationshipForEntity(entityId, relationshipManager);
        }
    }

    /**
     * Hydrate all relationships for an entity
     *
     * @param entity Entity to save relationships for
     * @param relationshipManager Relationship manager keeping track of what was already done
     */
    public static void hydrateAllRelationshipsForEntity(IManagedEntity entity, EntityRelationshipManager relationshipManager, SchemaContext context) throws EntityException
    {
        String partitionValue = String.valueOf(PartitionHelper.getPartitionFieldValue(entity, context));
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionValue);

        RelationshipReference entityId;

        if (!PartitionHelper.NULL_PARTITION.equals(partitionValue) && partitionValue != null)
        {
            final SystemPartitionEntry partition = context.getPartitionWithValue(descriptor.getClazz(), partitionValue);
            entityId = new RelationshipReference(AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier()), partition.getIndex());
        } else
        {
            entityId = new RelationshipReference(AbstractRecordController.getIndexValueFromEntity(entity, descriptor.getIdentifier()), 0);
        }


        for(RelationshipDescriptor relationshipDescriptor : descriptor.getRelationships().values())
        {
            final RelationshipController relationshipController = context.getRelationshipController(relationshipDescriptor);
            relationshipController.hydrateRelationshipForEntity(entityId, entity, relationshipManager, false);
        }
    }

}
