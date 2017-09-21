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
