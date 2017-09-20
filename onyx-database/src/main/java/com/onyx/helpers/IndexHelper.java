package com.onyx.helpers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.OnyxException;
import com.onyx.interactors.index.IndexInteractor;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.interactors.record.RecordInteractor;
import com.onyx.interactors.record.impl.DefaultRecordInteractor;

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
            final RecordInteractor recordInteractor = context.getRecordInteractor(descriptor);
            long newReferenceId = recordInteractor.getReferenceId(identifier);

            // Save all indexes
            for (IndexDescriptor indexDescriptor : descriptor.getIndexes().values())
            {
                final Object indexValue = DefaultRecordInteractor.Companion.getIndexValueFromEntity(entity, indexDescriptor);
                final IndexInteractor indexInteractor = context.getIndexInteractor(indexDescriptor);
                indexInteractor.save(indexValue, oldReferenceId, newReferenceId);
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
                final IndexInteractor indexInteractor = context.getIndexInteractor(indexDescriptor);
                indexInteractor.delete(referenceId);
            }
        }
    }
}
