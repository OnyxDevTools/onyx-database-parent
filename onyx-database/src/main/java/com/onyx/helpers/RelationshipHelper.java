package com.onyx.helpers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.interactors.relationship.data.RelationshipTransaction;
import com.onyx.interactors.relationship.RelationshipInteractor;

/**
 * Created by timothy.osborn on 12/23/14.
 *
 * Helper methds for a relationship
 */
@Deprecated
public class RelationshipHelper
{

    /**
     * Delete all relationships for an entity
     *
     * @param entity Entity to save relationships for
     * @param relationshipManager Relationship manager keeping track of what was already done
     * @param context Schema context
     */
    @Deprecated
    public static void deleteAllRelationshipsForEntity(IManagedEntity entity, RelationshipTransaction relationshipManager, SchemaContext context) throws OnyxException
    {
        String partitionValue = String.valueOf(PartitionHelper.getPartitionFieldValue(entity, context));
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionValue);

        for(RelationshipDescriptor relationshipDescriptor : descriptor.getRelationships().values())
        {
            final RelationshipInteractor relationshipInteractor = context.getRelationshipInteractor(relationshipDescriptor);
            relationshipInteractor.deleteRelationshipForEntity(entity, relationshipManager);
        }
    }

    /**
     * Hydrate all relationships for an entity
     *
     * @param entity Entity to save relationships for
     * @param relationshipManager Relationship manager keeping track of what was already done
     */
    @Deprecated
    public static void hydrateAllRelationshipsForEntity(IManagedEntity entity, RelationshipTransaction relationshipManager, SchemaContext context) throws OnyxException
    {
        String partitionValue = String.valueOf(PartitionHelper.getPartitionFieldValue(entity, context));
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity, partitionValue);

        if(!relationshipManager.contains(entity, context)) {
            relationshipManager.add(entity, context);

            for (RelationshipDescriptor relationshipDescriptor : descriptor.getRelationships().values()) {
                final RelationshipInteractor relationshipInteractor = context.getRelationshipInteractor(relationshipDescriptor);
                relationshipInteractor.hydrateRelationshipForEntity(entity, relationshipManager, false);
            }
        }
    }

}
