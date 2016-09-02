package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.exception.EntityExceptionWrapper;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.TableScanner;
import com.onyx.index.IndexController;
import com.onyx.map.MapBuilder;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryPartitionMode;

import java.util.*;
import java.util.concurrent.*;

/**
 * Created by timothy.osborn on 2/10/15.
 */
public class PartitionIndexScanner extends IndexScanner implements TableScanner {

    protected IndexController indexController = null;
    protected SystemEntity systemEntity = null;

    /**
     * Constructor
     *
     * @param criteria
     * @param classToScan
     * @param descriptor
     * @param temporaryDataFile
     * @throws EntityException
     */
    public PartitionIndexScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);

        final IndexDescriptor indexDescriptor = descriptor.getIndexes().get(criteria.getAttribute());
        indexController = context.getIndexController(indexDescriptor);
        systemEntity = context.getSystemEntityByName(query.getEntityType().getName());
    }

    /**
     * Full Table Scan
     *
     * @return
     * @throws EntityException
     */
    public Map scan() throws EntityException
    {

        final EntityExceptionWrapper wrapper = new EntityExceptionWrapper();
        Map<PartitionReference, PartitionReference> results = new ConcurrentHashMap();

        if(query.getPartition() == QueryPartitionMode.ALL)
        {
            Iterator<SystemPartitionEntry> it = systemEntity.getPartition().getEntries().iterator();
            final CountDownLatch countDownLatch = new CountDownLatch(systemEntity.getPartition().getEntries().size());

            while(it.hasNext())
            {
                final SystemPartitionEntry partition = it.next();

                executorService.execute(() -> {
                    try
                    {
                        final EntityDescriptor partitionDescriptor = context.getDescriptorForEntity(query.getEntityType(), partition.getValue());
                        final IndexController partitionIndexController = context.getIndexController(partitionDescriptor.getIndexes().get(criteria.getAttribute()));
                        Map partitionResults = scanPartition(partitionIndexController, partition.getIndex());
                        results.putAll(partitionResults);
                        countDownLatch.countDown();
                    } catch (EntityException e)
                    {
                        countDownLatch.countDown();
                        wrapper.exception = e;
                    }

                });

            }

            try {
                countDownLatch.await();
            } catch (InterruptedException e) {}

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
     * Scan indexes
     *
     * @return
     * @throws EntityException
     */

    public Map scanPartition(IndexController partitionIndexController, long partitionId) throws EntityException
    {
        final Map returnValue = new HashMap();
        final List<Long> references = new ArrayList<>();

        if(criteria.getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                partitionIndexController.findAll(idValue).forEach(o -> references.add(o));
            }
        }
        else
        {
            partitionIndexController.findAll(criteria.getValue()).forEach(o -> references.add(o));
        }

        references.stream().forEach(val->
        {
            returnValue.put(new PartitionReference(partitionId, val), new PartitionReference(partitionId, val));
        });

        return returnValue;
    }

    /**
     * Scan indexes that are within the existing values
     *
     * @param existingValues
     * @return
     * @throws EntityException
     */
    @Override
    public Map scan(Map existingValues) throws EntityException
    {
        final Map returnValue = new HashMap();

        final EntityExceptionWrapper wrapper = new EntityExceptionWrapper();
        Map<PartitionReference, PartitionReference> results = new ConcurrentHashMap();


        if(criteria.getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                if(query.getPartition() == QueryPartitionMode.ALL)
                {
                    systemEntity.getPartition().getEntries().stream().forEach(partition ->
                    {
                        try
                        {
                            final EntityDescriptor partitionDescriptor = context.getDescriptorForEntity(query.getEntityType(), partition.getValue());
                            final IndexController partitionIndexController = context.getIndexController(partitionDescriptor.getIndexes().get(criteria.getAttribute()));
                            Map partitionResults = scanPartition(partitionIndexController, partition.getIndex());

                            results.putAll(partitionResults);

                            results.keySet().stream().forEach(reference ->
                            {
                                if (existingValues.containsKey(reference))
                                {
                                    returnValue.put(reference, reference);
                                }
                            });

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
                    return super.scan(existingValues);
                }
            }
        }
        else
        {

            if(query.getPartition() == QueryPartitionMode.ALL)
            {
                systemEntity.getPartition().getEntries().stream().forEach(partition ->
                {
                    try
                    {
                        final EntityDescriptor partitionDescriptor = context.getDescriptorForEntity(query.getEntityType(), partition.getValue());
                        final IndexController partitionIndexController = context.getIndexController(partitionDescriptor.getIndexes().get(criteria.getAttribute()));
                        Map partitionResults = scanPartition(partitionIndexController, partition.getIndex());

                        results.putAll(partitionResults);

                        results.keySet().stream().forEach(reference ->
                        {
                            if (existingValues.containsKey(reference))
                            {
                                returnValue.put(reference, reference);
                            }
                        });

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
                return super.scan(existingValues);
            }

        }

        return returnValue;
    }
}
