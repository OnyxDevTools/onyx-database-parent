package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.exception.EntityExceptionWrapper;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.TableScanner;
import com.onyx.index.IndexController;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryPartitionMode;
import com.onyx.diskmap.MapBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Scan a partition for matching index values
 */
public class PartitionIndexScanner extends IndexScanner implements TableScanner {

    private SystemEntity systemEntity = null;

    /**
     * Constructor
     *
     * @param criteria Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor Entity descriptor of entity type to scan
     * @param temporaryDataFile Temproary data file to put results into
     */
    public PartitionIndexScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);

        systemEntity = context.getSystemEntityByName(query.getEntityType().getName());
    }

    /**
     * Full Table Scan
     *
     * @return Matching references for criteria
     * @throws EntityException Cannot scan partition
     */
    @SuppressWarnings("unchecked")
    public Map scan() throws EntityException
    {

        final EntityExceptionWrapper wrapper = new EntityExceptionWrapper();
        Map<PartitionReference, PartitionReference> results = new ConcurrentHashMap<>();

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
                        final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), partition.getValue());
                        final IndexController partitionIndexController = getContext().getIndexController(partitionDescriptor.getIndexes().get(criteria.getAttribute()));
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
            } catch (InterruptedException ignore) {}

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
     * @return Matching values meeting criteria
     * @throws EntityException Cannot scan partition
     */
    @SuppressWarnings("unchecked")
    private Map scanPartition(IndexController partitionIndexController, long partitionId) throws EntityException
    {
        final Map returnValue = new HashMap();
        final List<Long> references = new ArrayList<>();

        if(criteria.getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                partitionIndexController.findAll(idValue).keySet().forEach(o -> references.add((long)o));
            }
        }
        else
        {

            if(QueryCriteriaOperator.GREATER_THAN.equals(criteria.getOperator()))
                partitionIndexController.findAllAbove(criteria.getValue(), false).forEach(references::add);
            else if(QueryCriteriaOperator.GREATER_THAN_EQUAL.equals(criteria.getOperator()))
                partitionIndexController.findAllAbove(criteria.getValue(), true).forEach(references::add);
            else if(QueryCriteriaOperator.LESS_THAN.equals(criteria.getOperator()))
                partitionIndexController.findAllBelow(criteria.getValue(), false).forEach(references::add);
            else if(QueryCriteriaOperator.LESS_THAN_EQUAL.equals(criteria.getOperator()))
                partitionIndexController.findAllBelow(criteria.getValue(), true).forEach(references::add);
            else
                partitionIndexController.findAll(criteria.getValue()).keySet().forEach(o -> references.add((long)o));

        }

        references.forEach(val -> returnValue.put(new PartitionReference(partitionId, val), new PartitionReference(partitionId, val)));

        return returnValue;
    }

    /**
     * Scan indexes that are within the existing values
     *
     * @param existingValues Existing values to match criteria
     * @return Existing values meeting additional criteria
     * @throws EntityException Cannot scan partition
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map scan(Map existingValues) throws EntityException
    {
        final Map returnValue = new HashMap();

        final EntityExceptionWrapper wrapper = new EntityExceptionWrapper();
        Map<PartitionReference, PartitionReference> results = new ConcurrentHashMap<>();


        if(criteria.getValue() instanceof List)
        {
            for(Object ignored : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                if(query.getPartition() == QueryPartitionMode.ALL)
                {
                    systemEntity.getPartition().getEntries().forEach(partition ->
                    {
                        try {
                            final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), partition.getValue());
                            final IndexController partitionIndexController = getContext().getIndexController(partitionDescriptor.getIndexes().get(criteria.getAttribute()));
                            Map partitionResults = scanPartition(partitionIndexController, partition.getIndex());

                            results.putAll(partitionResults);

                            results.keySet().forEach(reference ->
                            {
                                if (existingValues.containsKey(reference)) {
                                    returnValue.put(reference, reference);
                                }
                            });

                        } catch (EntityException e) {
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
                systemEntity.getPartition().getEntries().forEach(partition ->
                {
                    try {
                        final EntityDescriptor partitionDescriptor = getContext().getDescriptorForEntity(query.getEntityType(), partition.getValue());
                        final IndexController partitionIndexController = getContext().getIndexController(partitionDescriptor.getIndexes().get(criteria.getAttribute()));
                        Map partitionResults = scanPartition(partitionIndexController, partition.getIndex());

                        results.putAll(partitionResults);

                        results.keySet().forEach(reference ->
                        {
                            if (existingValues.containsKey(reference)) {
                                returnValue.put(reference, reference);
                            }
                        });

                    } catch (EntityException e) {
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
