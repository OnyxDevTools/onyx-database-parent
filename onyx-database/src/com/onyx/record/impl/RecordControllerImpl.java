package com.onyx.record.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityCallbackException;
import com.onyx.exception.EntityException;
import com.onyx.helpers.ValidationHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.query.QueryListenerEvent;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * This controls the crud for a record
 */
public class RecordControllerImpl extends AbstractRecordController implements RecordController
{

    /**
     * Constructor including the entity descriptor
     *
     * @param entityDescriptor Entity descriptor for record set
     * @param context Schema context
     */
    public RecordControllerImpl(EntityDescriptor entityDescriptor, SchemaContext context)
    {
        super(entityDescriptor, context);
    }

    /**
     * Save an entity
     *
     * @param entity Entity to save
     * @return The identifier value
     * @throws EntityException Error saving entity
     *
     * @since 1.2.3 Optimized to only do a put if there are not pre persist callbacks
     */
    @Override
    public Object save(IManagedEntity entity) throws EntityException
    {
        final Object identifierValue = getIndexValueFromEntity(entity, entityDescriptor.getIdentifier()); // Get the Identifier key

        ValidationHelper.validateEntity(entityDescriptor, entity); // Validate the entity, this wil throw an exception if not valid so no need to continue

        invokePrePersistCallback(entity); // Invoke Pre persist callback for entity

        final AtomicBoolean isNew = new AtomicBoolean(false); // Keeps track of whether the record is new or not

        if (this.entityDescriptor.getPreInsertCallback() != null || this.entityDescriptor.getPreUpdateCallback() != null) {

            records.compute(identifierValue, (o, current) -> {
                try {
                    if (current == null) {
                        isNew.set(true);
                        invokePreInsertCallback(entity);
                    } else {
                        long recordId = records.getRecID(identifierValue);
                        if(recordId > 0L)
                        {
                            // Update Cached queries
                            context.getQueryCacheController().updateCachedQueryResultsForEntity(entity, this.entityDescriptor, recordId, QueryListenerEvent.PRE_UPDATE);
                        }
                        invokePreUpdateCallback(entity);
                    }
                } catch (EntityCallbackException ignore) {
                }
                return entity;
            });
        } else {
            long recordId = records.getRecID(identifierValue);
            if(recordId > 0L)
            {
                isNew.set(false);
                // Update Cached queries
                context.getQueryCacheController().updateCachedQueryResultsForEntity(entity, this.entityDescriptor, recordId, QueryListenerEvent.PRE_UPDATE);
            }
            else
            {
                isNew.set(true);
            }
            records.put(identifierValue, entity);
        }

        // Invoke Post insert or update callback
        if(isNew.get())
        {
            invokePostInsertCallback(entity);
        }
        else
        {
            invokePostUpdateCallback(entity);
        }

        invokePostPersistCallback(entity); // Always invoke Post persist callback

        // Update Cached queries
        context.getQueryCacheController().updateCachedQueryResultsForEntity(entity, this.entityDescriptor, records.getRecID(identifierValue),(isNew.get()) ? QueryListenerEvent.INSERT : QueryListenerEvent.UPDATE);

        // Return the id
        return identifierValue;
    }

}
