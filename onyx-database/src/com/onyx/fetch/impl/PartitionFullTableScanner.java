package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.EntityException;
import com.onyx.exception.EntityExceptionWrapper;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.TableScanner;
import com.onyx.map.DiskMap;
import com.onyx.map.MapBuilder;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryPartitionMode;
import com.onyx.util.CompareUtil;
import com.onyx.util.ReflectionUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * It can either scan the entire table or a subset of index values
 */
public class PartitionFullTableScanner extends FullTableScanner implements TableScanner
{

    private SystemEntity systemEntity = null;

    /**
     * Constructor
     *
     * @param criteria
     * @param classToScan
     * @param descriptor
     */
    public PartitionFullTableScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
        systemEntity = context.getSystemEntityByName(query.getEntityType().getCanonicalName());
    }


    /**
     * Scan records with existing values
     *
     * @param existingValues
     * @return
     * @throws EntityException
     */
    public Map scanPartition(DiskMap existingValues, long partitionId) throws EntityException
    {
        final Map allResults = new HashMap();

        final Iterator<Long> iterator = existingValues.keySet().iterator();
        IManagedEntity entity = null;
        Object attributeValue = null;
        Object keyValue = null;

        while(iterator.hasNext())
        {
            if(query.isTerminated())
                return allResults;

            keyValue = iterator.next();

            entity = (IManagedEntity)existingValues.get(keyValue);

            // Ensure entity still exists
            if(entity == null)
            {
                continue;
            }

            // Get the attribute value
            attributeValue = ReflectionUtil.getAny(entity, fieldToGrab);

            // Compare and add
            if (CompareUtil.compare(criteria.getValue(), attributeValue, criteria.getOperator()))
            {
                long ref = existingValues.getRecID(keyValue);
                allResults.put(new PartitionReference(partitionId, ref), new PartitionReference(partitionId, ref));
            }

        }

        return allResults;
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
            List<Future> futures = new ArrayList<>();
            while(it.hasNext())
            {
                final SystemPartitionEntry partition = it.next();

                futures.add(executorService.submit(() -> {
                try
                {
                    final EntityDescriptor partitionDescriptor = context.getDescriptorForEntity(query.getEntityType(), partition.getValue());

                    final MapBuilder dataFile = context.getDataFile(partitionDescriptor);
                    DiskMap recs = (DiskMap)dataFile.getHashMap(partitionDescriptor.getClazz().getCanonicalName());

                    Map partitionResults = scanPartition(recs, partition.getIndex());
                    results.putAll(partitionResults);
                } catch (EntityException e)
                {
                    wrapper.exception = e;
                }

                }));

            }

            try
            {
                for(Future future : futures)
                {
                    future.get();
                }
            } catch (Exception e){}

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

}