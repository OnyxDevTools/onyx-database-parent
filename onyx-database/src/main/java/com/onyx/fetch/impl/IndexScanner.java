package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.fetch.PartitionReference;
import com.onyx.util.map.CompatHashMap;
import com.onyx.exception.OnyxException;
import com.onyx.fetch.TableScanner;
import com.onyx.interactors.index.IndexInteractor;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.diskmap.MapBuilder;

import java.util.*;

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Scan index values for given criteria
 */
public class IndexScanner extends AbstractTableScanner implements TableScanner {

    private IndexInteractor indexInteractor = null;
    private long partitionId = 0L;

    /**
     * Constructor
     *
     * @param criteria Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor Entity descriptor of entity type to scan
     * @param temporaryDataFile Temproary data file to put results into
     * @throws OnyxException Cannot scan index
     */
    public IndexScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws OnyxException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);

        if(descriptor.getHasPartition())
            partitionId = context.getPartitionWithValue(classToScan, descriptor.getPartition().getPartitionValue()).getPrimaryKey();

        final IndexDescriptor indexDescriptor = descriptor.getIndexes().get(criteria.getAttribute());
        indexInteractor = context.getIndexInteractor(indexDescriptor);
    }

    /**
     * Scan indexes
     *
     * @return Indexes meeting criteria
     * @throws OnyxException Cannot scan index
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<PartitionReference, PartitionReference> scan() throws OnyxException
    {
        final Map<PartitionReference, PartitionReference> returnValue = new CompatHashMap<>();
        final List<Long> references = new ArrayList<>();

        if(getCriteria().getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) getCriteria().getValue())
            {
                if(getQuery().isTerminated())
                    return returnValue;

                Set<Long> values;

                if(getCriteria().getOperator() == QueryCriteriaOperator.GREATER_THAN)
                    values = indexInteractor.findAllAbove(idValue, false);
                else if(getCriteria().getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                    values = indexInteractor.findAllAbove(idValue, true);
                else if(getCriteria().getOperator() == QueryCriteriaOperator.LESS_THAN)
                    values = indexInteractor.findAllBelow(idValue, false);
                else if(getCriteria().getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                    values = indexInteractor.findAllBelow(idValue, true);
                else
                    values = indexInteractor.findAll(idValue).keySet();

                references.addAll(values);

            }
        }
        else
        {

            Set<Long> values;

            if(getCriteria().getOperator() == QueryCriteriaOperator.GREATER_THAN)
                values = indexInteractor.findAllAbove(getCriteria().getValue(), false);
            else if(getCriteria().getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                values = indexInteractor.findAllAbove(getCriteria().getValue(), true);
            else if(getCriteria().getOperator() == QueryCriteriaOperator.LESS_THAN)
                values = indexInteractor.findAllBelow(getCriteria().getValue(), false);
            else if(getCriteria().getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                values = indexInteractor.findAllBelow(getCriteria().getValue(), true);
            else
                values = indexInteractor.findAll(getCriteria().getValue()).keySet();

            references.addAll(values);

        }

        for(Long val : references)
            returnValue.put(new PartitionReference(partitionId,val), new PartitionReference(partitionId,val));

        return returnValue;
    }

    /**
     * Scan indexes that are within the existing values
     *
     * @param existingValues Existing values to check
     * @return Existing values matching criteria
     * @throws OnyxException Cannot scan index
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<PartitionReference, PartitionReference> scan(Map<PartitionReference, ? extends PartitionReference> existingValues) throws OnyxException
    {
        final Map<PartitionReference, PartitionReference> returnValue = new CompatHashMap<>();

        if(getCriteria().getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) getCriteria().getValue())
            {
                if(getQuery().isTerminated())
                    return returnValue;

                Set<Long> results;

                if(QueryCriteriaOperator.GREATER_THAN.equals(getCriteria().getOperator()))
                    results = indexInteractor.findAllAbove(idValue, false);
                else if(QueryCriteriaOperator.GREATER_THAN_EQUAL.equals(getCriteria().getOperator()))
                    results = indexInteractor.findAllAbove(idValue, true);
                else if(QueryCriteriaOperator.LESS_THAN.equals(getCriteria().getOperator()))
                    results = indexInteractor.findAllBelow(idValue, false);
                else if(QueryCriteriaOperator.LESS_THAN_EQUAL.equals(getCriteria().getOperator()))
                    results = indexInteractor.findAllBelow(idValue, true);
                else
                    results = indexInteractor.findAll(idValue).keySet();


                //noinspection Convert2streamapi
                for(Long reference : results)
                {
                    if (existingValues.containsKey(new PartitionReference(partitionId,reference))) {
                        returnValue.put(new PartitionReference(partitionId,reference), new PartitionReference(partitionId,reference));
                    }
                }
            }
        }
        else
        {
            Set<Long> results;

            if(QueryCriteriaOperator.GREATER_THAN.equals(getCriteria().getOperator()))
                results = indexInteractor.findAllAbove(getCriteria().getValue(), false);
            else if(QueryCriteriaOperator.GREATER_THAN_EQUAL.equals(getCriteria().getOperator()))
                results = indexInteractor.findAllAbove(getCriteria().getValue(), true);
            else if(QueryCriteriaOperator.LESS_THAN.equals(getCriteria().getOperator()))
                results = indexInteractor.findAllBelow(getCriteria().getValue(), false);
            else if(QueryCriteriaOperator.LESS_THAN_EQUAL.equals(getCriteria().getOperator()))
                results = indexInteractor.findAllBelow(getCriteria().getValue(), true);
            else
                results = indexInteractor.findAll(getCriteria().getValue()).keySet();


            //noinspection Convert2streamapi
            for(Long reference : results)
            {
                if (existingValues.containsKey(new PartitionReference(partitionId,reference))) {
                    returnValue.put(new PartitionReference(partitionId,reference), new PartitionReference(partitionId,reference));
                }
            }

        }

        return returnValue;
    }
}
