package com.onyx.helpers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.*;
import com.onyx.index.IndexController;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;

/**
 * Created by timothy.osborn on 12/23/14.
 */
public class IndexHelper
{

    /**
     * Save all indexes for an entity
     *
     * @param context
     * @param descriptor
     * @param entity
     * @throws EntityException
     */
    public static void saveAllIndexesForEntity(SchemaContext context, EntityDescriptor descriptor, Object identifier, long oldReferenceId, IManagedEntity entity) throws EntityException
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
     * @param context
     * @param descriptor
     * @param referenceId
     * @throws EntityException
     */
    public static void deleteAllIndexesForEntity(SchemaContext context, EntityDescriptor descriptor, long referenceId) throws EntityException
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
