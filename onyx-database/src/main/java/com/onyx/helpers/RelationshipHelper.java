package com.onyx.helpers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.diskmap.node.SkipListNode;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InvalidRelationshipTypeException;
import com.onyx.fetch.PartitionReference;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;
import com.onyx.relationship.EntityRelationshipManager;
import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.RelationshipReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by timothy.osborn on 12/23/14.
 *
 * Helper methds for a relationship
 */
@Deprecated
public class RelationshipHelper
{

    /**
     * Save all relationships for an entity
     *
     * @param entity Entity to save relationships for
     * @param manager Relationship manager keeping track of what was already done
     */
    @Deprecated
    public static void saveAllRelationshipsForEntity(IManagedEntity entity, EntityRelationshipManager manager, SchemaContext context) throws OnyxException
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
    @Deprecated
    public static void deleteAllRelationshipsForEntity(IManagedEntity entity, EntityRelationshipManager relationshipManager, SchemaContext context) throws OnyxException
    {
        String partitionValue = String.valueOf(PartitionHelper.getPartitionFieldValue(entity, context));
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionValue);

        RelationshipReference entityId;

        if (!PartitionHelper.NULL_PARTITION.equals(partitionValue) && partitionValue != null)
        {
            final SystemPartitionEntry partition = context.getPartitionWithValue(descriptor.getEntityClass(), partitionValue);
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
    @Deprecated
    public static void hydrateAllRelationshipsForEntity(IManagedEntity entity, EntityRelationshipManager relationshipManager, SchemaContext context) throws OnyxException
    {
        String partitionValue = String.valueOf(PartitionHelper.getPartitionFieldValue(entity, context));
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionValue);

        RelationshipReference entityId;

        if (!PartitionHelper.NULL_PARTITION.equals(partitionValue) && partitionValue != null)
        {
            final SystemPartitionEntry partition = context.getPartitionWithValue(descriptor.getEntityClass(), partitionValue);
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

    /**
     * Helper method to grab a relationship value from the store
     *
     * @param entity Parent entity
     * @param entityReference Parent entity reference
     * @param attribute Relationship field name
     * @param context Schema context
     * @return A list of relationship entities
     * @throws OnyxException Could not pull relationship
     *
     * @since 1.3.0 Used to dynamically pull a relationship regardless of relationship type and partition information
     *              Supports insertion criteria checking.
     */
    @Deprecated
    public static List<IManagedEntity> getRelationshipForValue(IManagedEntity entity, Object entityReference, String attribute, SchemaContext context) throws OnyxException
    {
        String[] slices = attribute.split("\\.");

        String partitionValue = String.valueOf(PartitionHelper.getPartitionFieldValue(entity, context));
        EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionValue);

        RelationshipDescriptor relationshipDescriptor = null;

        // Iterate through and grab the right descriptor
        try {
            for (int i = 0; i < slices.length - 1; i++) {
                relationshipDescriptor = descriptor.getRelationships().get(slices[i]);
                descriptor = context.getBaseDescriptorForEntity(relationshipDescriptor.getInverseClass());
            }
        } catch (NullPointerException e)
        {
            return null;
        }


        final RelationshipController relationshipController = context.getRelationshipController(relationshipDescriptor);
        List<RelationshipReference> relationshipReferences;
        RecordController recordController;

        // Get relationship references
        if (entityReference instanceof PartitionReference) {
            relationshipReferences = relationshipController.getRelationshipIdentifiersWithReferenceId((PartitionReference)entityReference);
        }
        else if(entityReference instanceof Long)
        {
            relationshipReferences = relationshipController.getRelationshipIdentifiersWithReferenceId((Long)entityReference);
        }
        else
        {
            relationshipReferences = relationshipController.getRelationshipIdentifiersWithReferenceId(((SkipListNode)entityReference).recordId);
        }

        RecordController defaultRecordController = context.getRecordController(descriptor);

        PartitionContext partitionContext = null;

        List<IManagedEntity> entities = new ArrayList<>();
        for(RelationshipReference reference : relationshipReferences)
        {
            if(reference.partitionId <= 0) {
                IManagedEntity relationshipEntity = defaultRecordController.getWithId(reference.identifier);
                if(relationshipEntity != null)
                    entities.add(relationshipEntity);
            }
            else
            {
                if(partitionContext == null)
                    partitionContext = new PartitionContext(context, descriptor);

                recordController = partitionContext.getRecordControllerForPartition(reference.partitionId);
                IManagedEntity relationshipEntity = recordController.getWithId(reference.identifier);
                if(relationshipEntity != null)
                    entities.add(relationshipEntity);
            }
        }

        return entities;
    }

}
