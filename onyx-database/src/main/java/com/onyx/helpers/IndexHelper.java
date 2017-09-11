package com.onyx.helpers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.OnyxException;
import com.onyx.index.IndexController;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;

/**
 * Created by timothy.osborn on 12/23/14.
 *
 * Static helper methods for entity indexes
 */
@Deprecated
public class IndexHelper
{

    /**
     * Save all indexes for an entity
     *
     * @param context Schema Context
     * @param descriptor Entity Descriptor
     * @param entity Entity to save indexes for
     */
    @Deprecated
    public static void saveAllIndexesForEntity(SchemaContext context, EntityDescriptor descriptor, Object identifier, long oldReferenceId, IManagedEntity entity) throws OnyxException
    {
        if(descriptor.getIndexes().size() > 0)
        {
            final RecordController recordController = context.getRecordController(descriptor);
            long newReferenceId = recordController.getReferenceId(identifier);

            // Save all indexes
            for (IndexDescriptor indexDescriptor : descriptor.getIndexes().values())
            {
                final Object indexValue = AbstractRecordController.getIndexValueFromEntity(entity, indexDescriptor);
                final IndexController controller = context.getIndexController(indexDescriptor);
                controller.save(indexValue, oldReferenceId, newReferenceId);
            }
        }
    }

    /**
     * Delete all indexes for an entity
     *
     * @param context Schema Context
     * @param descriptor Entity Descriptor
     * @param referenceId Entity reference
     */
    @Deprecated
    public static void deleteAllIndexesForEntity(SchemaContext context, EntityDescriptor descriptor, long referenceId) throws OnyxException
    {
        if(descriptor.getIndexes().size() > 0)
        {
            // Save all indexes
            for (IndexDescriptor indexDescriptor : descriptor.getIndexes().values())
            {
                final IndexController controller = context.getIndexController(indexDescriptor);
                controller.delete(referenceId);
            }
        }
    }
}
