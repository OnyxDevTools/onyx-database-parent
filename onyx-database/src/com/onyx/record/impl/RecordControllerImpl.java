package com.onyx.record.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityCallbackException;
import com.onyx.exception.EntityException;
import com.onyx.helpers.ValidationHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by timothy.osborn on 2/5/15.
 */
public class RecordControllerImpl extends AbstractRecordController implements RecordController
{

    /**
     * Constructor including the entity descriptor
     *
     * @param entityDescriptor
     */
    public RecordControllerImpl(EntityDescriptor entityDescriptor, SchemaContext context)
    {
        super(entityDescriptor, context);
    }

    /**
     * Save an entity
     *
     * @param entity
     * @return
     * @throws EntityException
     */
    @Override
    public Object save(IManagedEntity entity) throws EntityException
    {
        final Object identifierValue = getIndexValueFromEntity(entity, entityDescriptor.getIdentifier()); // Get the Identifier value

        ValidationHelper.validateEntity(entityDescriptor, entity); // Validate the entity, this wil throw an exception if not valid so no need to continue

        invokePrePersistCallback(entity); // Invoke Pre persist callback for entity

        final AtomicBoolean isNew = new AtomicBoolean(false); // Keeps track of whether the record is new or not

        records.compute(identifierValue, (o, current) -> {
            try
            {
                if (current == null)
                {
                    isNew.set(true);
                    invokePreInsertCallback(entity);
                } else
                {
                    invokePreUpdateCallback(entity);
                }
            } catch (EntityCallbackException e)
            { }
            return entity;
        });

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

        // Return the id
        return identifierValue;
    }


}
