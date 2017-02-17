package com.onyx.helpers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.exception.EntityExceptionWrapper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.record.RecordController;
import com.onyx.structure.MapBuilder;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;

/**
 * Created by timothy.osborn on 3/19/15.
 *
 * The purpose of this class is to act as a helper to cache the partition information regarding an entity.
 * It is to make it more efficient
 *
 */
public class PartitionContext
{

    protected String contextId = null;
    protected RecordController defaultRecordController = null;
    protected EntityDescriptor defaultDescriptor = null;

    /**
     * Constructor
     *
     * @param context
     * @param descriptor
     */
    public PartitionContext(SchemaContext context, EntityDescriptor descriptor)
    {
        this.contextId = context.getContextId();
        this.defaultDescriptor = descriptor;
        this.defaultRecordController = context.getRecordController(this.defaultDescriptor);
    }

    /**
     * Cached Partition Files
     */
    protected Map<Long, MapBuilder> cachedPartitionFiles = Collections.synchronizedMap(new WeakHashMap<Long, MapBuilder>());

    /**
     * Cached Data Files for partition ids, return default if there is no partition.  Otherwise insert into cache.
     *
     */
    public MapBuilder getDataFileWithPartitionId(long partitionId) throws EntityException
    {
        if(partitionId == 0)
        {
            return this.getContext().getDataFile(this.defaultDescriptor);
        }

        final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

        MapBuilder retVal = cachedPartitionFiles.compute(partitionId, (aLong, db) -> {
            if(db == null)
            {
                try
                {
                    return getContext().getPartitionDataFile(defaultDescriptor, partitionId);
                } catch (EntityException e)
                {
                    exceptionWrapper.exception = e;
                }
            }
            return db;
        });

        if(exceptionWrapper.exception != null)
        {
            throw exceptionWrapper.exception;
        }

        return retVal;
    }




    /**
     * Cached Partition Files
     */

    class PartitionKey
    {
        public PartitionKey(){}

        public PartitionKey(IManagedEntity entity) throws EntityException
        {
            this.entityType = entity.getClass();
            this.partitionVal = String.valueOf(PartitionHelper.getPartitionFieldValue(entity, getContext()));
        }

        @Override
        public int hashCode()
        {
            return this.entityType.getName().hashCode() + this.partitionVal.hashCode();
        }

        public boolean equals(Object val)
        {
            if(val instanceof PartitionKey)
            {
                final PartitionKey key = (PartitionKey) val;
                return (key.entityType == this.entityType && key.partitionVal.equals(partitionVal));
            }
            return false;
        }

        public Class entityType;
        public String partitionVal;
    }

    protected Map<PartitionKey, EntityDescriptor> cachedDescriptorsPerEntity = Collections.synchronizedMap(new WeakHashMap<PartitionKey, EntityDescriptor>());

    public EntityDescriptor getDescriptorForEntity(IManagedEntity entity) throws EntityException
    {
        if(PartitionHelper.hasPartitionField(entity, getContext()))
        {

            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            EntityDescriptor retVal = cachedDescriptorsPerEntity.compute(new PartitionKey(entity), new BiFunction<PartitionKey, EntityDescriptor, EntityDescriptor>() {
                @Override
                public EntityDescriptor apply(PartitionKey partitionKey, EntityDescriptor descriptor)
                {
                    if(descriptor == null)
                    {
                        try
                        {
                            return getContext().getDescriptorForEntity(entity);
                        }
                        catch (EntityException e)
                        {
                            exceptionWrapper.exception = e;
                        }
                    }
                    return descriptor;
                }
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            return retVal;
        }
        return defaultDescriptor;
    }



    protected Map<Long, EntityDescriptor> cachedDescriptorsPerPartition = Collections.synchronizedMap(new WeakHashMap<Long, EntityDescriptor>());

    public EntityDescriptor getDescriptorWithPartitionId(long partitionId) throws EntityException
    {
        if(partitionId != 0)
        {

            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            EntityDescriptor retVal = cachedDescriptorsPerPartition.compute(partitionId, new BiFunction<Long, EntityDescriptor, EntityDescriptor>() {
                @Override
                public EntityDescriptor apply(Long partitionKey, EntityDescriptor descriptor)
                {
                    if(descriptor == null)
                    {
                        try
                        {
                            SystemPartitionEntry partitionEntry = getContext().getPartitionWithId(defaultDescriptor.getClazz(), partitionId);
                            if(partitionEntry == null)
                            {
                                return defaultDescriptor;
                            }

                            return getContext().getDescriptorForEntity(defaultDescriptor.getClazz(), partitionEntry.getValue());
                        }
                        catch (EntityException e)
                        {
                            exceptionWrapper.exception = e;
                        }
                    }
                    return descriptor;
                }
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            return retVal;
        }
        return defaultDescriptor;
    }

    protected Map<PartitionKey, RecordController> cachedControllersPerEntity = Collections.synchronizedMap(new WeakHashMap<PartitionKey, RecordController>());

    public RecordController getRecordControllerForEntity(IManagedEntity entity) throws EntityException
    {
        if(PartitionHelper.hasPartitionField(entity, getContext()))
        {

            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            final RecordController retVal = cachedControllersPerEntity.compute(new PartitionKey(entity), new BiFunction<PartitionKey, RecordController, RecordController>() {
                @Override
                public RecordController apply(PartitionKey partitionKey, RecordController controller)
                {
                    if(controller == null)
                    {
                        try
                        {
                            return getContext().getRecordController(getDescriptorForEntity(entity));
                        }
                        catch (EntityException e)
                        {
                            exceptionWrapper.exception = e;
                        }
                    }
                    return controller;
                }
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            return retVal;
        }
        return defaultRecordController;
    }


    protected Map<Long, RecordController> cachedControllersPerPartition = Collections.synchronizedMap(new WeakHashMap<Long, RecordController>());

    public RecordController getRecordControllerForPartition(long partitionId) throws EntityException
    {
        if(partitionId != 0)
        {
            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            RecordController retVal = cachedControllersPerPartition.compute(partitionId, new BiFunction<Long, RecordController, RecordController>() {
                @Override
                public RecordController apply(Long partitionKey, RecordController controller)
                {
                    if(controller == null)
                    {
                        try
                        {
                            EntityDescriptor inverseDescriptor = getDescriptorWithPartitionId(partitionId);
                            return getContext().getRecordController(inverseDescriptor);
                        }
                        catch (EntityException e)
                        {
                            exceptionWrapper.exception = e;
                        }
                    }
                    return controller;
                }
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            return retVal;
        }
        return defaultRecordController;
    }


    protected Map<PartitionKey, MapBuilder> cachedDataFilesPerEntity = Collections.synchronizedMap(new WeakHashMap<PartitionKey, MapBuilder>());

    public MapBuilder getDataFileForEntity(IManagedEntity entity) throws EntityException
    {
        if (PartitionHelper.hasPartitionField(entity, getContext()))
        {
            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            MapBuilder dataFile = cachedDataFilesPerEntity.compute(new PartitionKey(entity), (partitionKey, db) -> {
                if(db != null)
                {
                    return db;
                }
                try
                {
                    return getContext().getDataFile(getDescriptorForEntity(entity));
                } catch (EntityException e)
                {
                    exceptionWrapper.exception = e;
                }
                return null;
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            if(dataFile == null)
                return this.getContext().getDataFile(this.defaultDescriptor);
            return dataFile;
        }
        else
        {
            return this.getContext().getDataFile(this.defaultDescriptor);
        }
    }



    protected Map<PartitionKey, Long> cachedPartitionIds = Collections.synchronizedMap(new WeakHashMap<PartitionKey, Long>());

    public long getPartitionId(IManagedEntity entity) throws EntityException
    {
        if (PartitionHelper.hasPartitionField(entity, getContext()))
        {
            final EntityExceptionWrapper exceptionWrapper = new EntityExceptionWrapper();

            Long partitionId = cachedPartitionIds.compute(new PartitionKey(entity), new BiFunction<PartitionKey, Long, Long>() {
                @Override
                public Long apply(PartitionKey partitionKey, Long id)
                {
                    if(id != null)
                    {
                        return id;
                    }
                    try
                    {
                        Object partitionValue = PartitionHelper.getPartitionFieldValue(entity, getContext());
                        if (partitionValue == null || partitionValue == PartitionHelper.NULL_PARTITION)
                        {
                            return 0l;
                        }
                        return getContext().getPartitionWithValue(partitionKey.entityType, partitionKey.partitionVal).getIndex();
                    }
                    catch (EntityException e)
                    {
                        exceptionWrapper.exception = e;
                        return 0l;
                    }
                }
            });

            if(exceptionWrapper.exception != null)
            {
                throw exceptionWrapper.exception;
            }

            return partitionId;
        }
        else
        {
            return 0;
        }
    }

    protected SchemaContext getContext()
    {
        return DefaultSchemaContext.registeredSchemaContexts.get(contextId);
    }
}
