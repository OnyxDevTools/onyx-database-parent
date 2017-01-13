package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.entity.SystemEntity;
import com.onyx.exception.EntityException;
import com.onyx.exception.EntityExceptionWrapper;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.TableScanner;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryPartitionMode;
import com.onyx.record.RecordController;
import com.onyx.structure.MapBuilder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by timothy.osborn on 1/3/15.
 */
public class PartitionIdentifierScanner extends IdentifierScanner implements TableScanner
{

    protected SystemEntity systemEntity = null;

    /**
     * Constructor
     *
     * @param criteria
     * @param classToScan
     * @param descriptor
     * @throws EntityException
     */
    public PartitionIdentifierScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
        systemEntity = context.getSystemEntityByName(query.getEntityType().getName());
    }

    /**
     * Full scan with ids
     *
     * @return
     * @throws EntityException
     */
    public Map<Long, Long> scanPartition(RecordController recordController, long partitionId) throws EntityException
    {
        final Map returnValue = new HashMap();

        // If it is an in clause
        if(criteria.getValue() instanceof List)
        {
            for (Object idValue : (List)criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                long referenceId = recordController.getReferenceId(idValue);
                // The id does exist, lets add it to the results
                if(referenceId > -1)
                {
                    final PartitionReference reference = new PartitionReference(partitionId, referenceId);
                    returnValue.put(reference, reference);
                }
            }
        }


        // Its an equals, if the object exists, add it to the results
        else
        {
            long referenceId = recordController.getReferenceId(criteria.getValue());
            if(referenceId > -1)
            {
                final PartitionReference reference = new PartitionReference(partitionId, referenceId);
                returnValue.put(reference, reference);
            }
        }

        return returnValue;
    }

    /**
     * Scan existing values for identifiers
     *
     * @return
     * @throws EntityException
     */
    @Override
    public Map scan() throws EntityException
    {
        final EntityExceptionWrapper wrapper = new EntityExceptionWrapper();
        Map<PartitionReference, PartitionReference> results = new ConcurrentHashMap();

        if(query.getPartition() == QueryPartitionMode.ALL)
        {
            systemEntity.getPartition().getEntries().stream().forEach(partition ->
            {
                try
                {
                    final EntityDescriptor partitionDescriptor = context.getDescriptorForEntity(query.getEntityType(), partition.getValue());
                    final RecordController recordController = context.getRecordController(partitionDescriptor);
                    Map partitionResults = scanPartition(recordController, partition.getIndex());
                    results.putAll(partitionResults);
                } catch (EntityException e)
                {
                    wrapper.exception = e;
                }
            });

            if (wrapper.exception != null)
            {
                throw wrapper.exception;
            }
        }
        else
        {
            return super.scan();
        }

        return results;
    }


    /**
     * Scan existing values for identifiers
     *
     * @param existingValues
     * @return
     * @throws EntityException
     */
    @Override
    public Map scan(Map existingValues) throws EntityException
    {
        final Map returnValue = new HashMap();

        final RecordController recordController = context.getRecordController(descriptor);

        Iterator iterator = existingValues.keySet().iterator();

        Object key = null;

        while (iterator.hasNext())
        {
            key = iterator.next();

            if(key instanceof PartitionReference)
            {
                RecordController recordController1 = this.getRecordControllerForPartition(((PartitionReference) key).partition);
                // If it is an in clause
                if(criteria.getValue() instanceof List)
                {
                    for (Object idValue : (List)criteria.getValue())
                    {
                        if(query.isTerminated())
                            return returnValue;

                        long referenceId = recordController1.getReferenceId(idValue);

                        if(referenceId == ((PartitionReference)key).reference)
                        {
                            returnValue.put(key, key);
                        }
                    }
                }
                // Its an equals, if the object exists, add it to the results
                else
                {
                    long referenceId = recordController1.getReferenceId(criteria.getValue());

                    if(referenceId ==  ((PartitionReference)key).reference)
                    {
                        returnValue.put(key, key);
                    }
                }
            }
            else
            {
                // If it is an in clause
                if(criteria.getValue() instanceof List)
                {
                    for (Object idValue : (List)criteria.getValue())
                    {
                        if(query.isTerminated())
                            return returnValue;

                        long referenceId = recordController.getReferenceId(idValue);

                        if(referenceId == (Long)key)
                        {
                            returnValue.put(referenceId, referenceId);
                        }
                    }
                }
                // Its an equals, if the object exists, add it to the results
                else
                {
                    long referenceId = recordController.getReferenceId(criteria.getValue());

                    if(referenceId ==  (Long)key)
                    {
                        returnValue.put(referenceId, referenceId);
                    }
                }
            }
        }

        return returnValue;
    }
}
