package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
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
    public Map<Long, Long> scan() throws OnyxException
    {
        final Map<Long, Long> returnValue = new CompatHashMap<>();
        final List<Long> references = new ArrayList<>();

        if(criteria.getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                Set<Long> values;

                if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN)
                    values = indexInteractor.findAllAbove(idValue, false);
                else if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                    values = indexInteractor.findAllAbove(idValue, true);
                else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN)
                    values = indexInteractor.findAllBelow(idValue, false);
                else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                    values = indexInteractor.findAllBelow(idValue, true);
                else
                    values = indexInteractor.findAll(idValue).keySet();

                references.addAll(values);

            }
        }
        else
        {

            Set<Long> values;

            if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN)
                values = indexInteractor.findAllAbove(criteria.getValue(), false);
            else if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                values = indexInteractor.findAllAbove(criteria.getValue(), true);
            else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN)
                values = indexInteractor.findAllBelow(criteria.getValue(), false);
            else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                values = indexInteractor.findAllBelow(criteria.getValue(), true);
            else
                values = indexInteractor.findAll(criteria.getValue()).keySet();

            references.addAll(values);

        }

        for(Long val : references)
            returnValue.put(val, val);

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
    public Map<Long, Long> scan(Map<Long, Long> existingValues) throws OnyxException
    {
        final Map<Long, Long> returnValue = new CompatHashMap<>();

        if(criteria.getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                Set<Long> results;

                if(QueryCriteriaOperator.GREATER_THAN.equals(criteria.getOperator()))
                    results = indexInteractor.findAllAbove(idValue, false);
                else if(QueryCriteriaOperator.GREATER_THAN_EQUAL.equals(criteria.getOperator()))
                    results = indexInteractor.findAllAbove(idValue, true);
                else if(QueryCriteriaOperator.LESS_THAN.equals(criteria.getOperator()))
                    results = indexInteractor.findAllBelow(idValue, false);
                else if(QueryCriteriaOperator.LESS_THAN_EQUAL.equals(criteria.getOperator()))
                    results = indexInteractor.findAllBelow(idValue, true);
                else
                    results = indexInteractor.findAll(idValue).keySet();


                //noinspection Convert2streamapi
                for(Long reference : results)
                {
                    if (existingValues.containsKey(reference)) {
                        returnValue.put(reference, reference);
                    }
                }
            }
        }
        else
        {
            Set<Long> results;

            if(QueryCriteriaOperator.GREATER_THAN.equals(criteria.getOperator()))
                results = indexInteractor.findAllAbove(criteria.getValue(), false);
            else if(QueryCriteriaOperator.GREATER_THAN_EQUAL.equals(criteria.getOperator()))
                results = indexInteractor.findAllAbove(criteria.getValue(), true);
            else if(QueryCriteriaOperator.LESS_THAN.equals(criteria.getOperator()))
                results = indexInteractor.findAllBelow(criteria.getValue(), false);
            else if(QueryCriteriaOperator.LESS_THAN_EQUAL.equals(criteria.getOperator()))
                results = indexInteractor.findAllBelow(criteria.getValue(), true);
            else
                results = indexInteractor.findAll(criteria.getValue()).keySet();


            //noinspection Convert2streamapi
            for(Long reference : results)
            {
                if (existingValues.containsKey(reference)) {
                    returnValue.put(reference, reference);
                }
            }

        }

        return returnValue;
    }
}
