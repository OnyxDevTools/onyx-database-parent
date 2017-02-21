package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.fetch.TableScanner;
import com.onyx.index.IndexController;
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

    private IndexController indexController = null;

    /**
     * Constructor
     *
     * @param criteria Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor Entity descriptor of entity type to scan
     * @param temporaryDataFile Temproary data file to put results into
     * @throws EntityException Cannot scan index
     */
    public IndexScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);

        final IndexDescriptor indexDescriptor = descriptor.getIndexes().get(criteria.getAttribute());
        indexController = context.getIndexController(indexDescriptor);
    }

    /**
     * Scan indexes
     *
     * @return Indexes meeting criteria
     * @throws EntityException Cannot scan index
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, Long> scan() throws EntityException
    {
        final Map<Long, Long> returnValue = new HashMap<>();
        final List<Long> references = new ArrayList<>();

        if(criteria.getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                if(QueryCriteriaOperator.GREATER_THAN.equals(criteria.getOperator()))
                    indexController.findAllAbove(idValue, false).forEach(references::add);
                else if(QueryCriteriaOperator.GREATER_THAN_EQUAL.equals(criteria.getOperator()))
                    indexController.findAllAbove(idValue, true).forEach(references::add);
                else if(QueryCriteriaOperator.LESS_THAN.equals(criteria.getOperator()))
                    indexController.findAllBelow(idValue, false).forEach(references::add);
                else if(QueryCriteriaOperator.LESS_THAN_EQUAL.equals(criteria.getOperator()))
                    indexController.findAllBelow(idValue, true).forEach(references::add);
                else
                    indexController.findAll(idValue).keySet().forEach(o -> references.add((long)o));
            }
        }
        else
        {

            if(QueryCriteriaOperator.GREATER_THAN.equals(criteria.getOperator()))
                indexController.findAllAbove(criteria.getValue(), false).forEach(references::add);
            else if(QueryCriteriaOperator.GREATER_THAN_EQUAL.equals(criteria.getOperator()))
                indexController.findAllAbove(criteria.getValue(), true).forEach(references::add);
            else if(QueryCriteriaOperator.LESS_THAN.equals(criteria.getOperator()))
                indexController.findAllBelow(criteria.getValue(), false).forEach(references::add);
            else if(QueryCriteriaOperator.LESS_THAN_EQUAL.equals(criteria.getOperator()))
                indexController.findAllBelow(criteria.getValue(), true).forEach(references::add);
            else
                indexController.findAll(criteria.getValue()).keySet().forEach(o -> references.add((long)o));

        }

        references.forEach(val -> returnValue.put(val, val));

        return returnValue;
    }

    /**
     * Scan indexes that are within the existing values
     *
     * @param existingValues Existing values to check
     * @return Existing values matching criteria
     * @throws EntityException Cannot scan index
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, Long> scan(Map<Long, Long> existingValues) throws EntityException
    {
        final Map<Long, Long> returnValue = new HashMap<>();

        if(criteria.getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                Set<Long> results;

                if(QueryCriteriaOperator.GREATER_THAN.equals(criteria.getOperator()))
                    results = indexController.findAllAbove(idValue, false);
                else if(QueryCriteriaOperator.GREATER_THAN_EQUAL.equals(criteria.getOperator()))
                    results = indexController.findAllAbove(idValue, true);
                else if(QueryCriteriaOperator.LESS_THAN.equals(criteria.getOperator()))
                    results = indexController.findAllBelow(idValue, false);
                else if(QueryCriteriaOperator.LESS_THAN_EQUAL.equals(criteria.getOperator()))
                    results = indexController.findAllBelow(idValue, true);
                else
                    results = indexController.findAll(idValue).keySet();


                results.forEach(reference ->
                {
                    if (existingValues.containsKey(reference)) {
                        returnValue.put(reference, reference);
                    }
                });
            }
        }
        else
        {
            Set<Long> results;

            if(QueryCriteriaOperator.GREATER_THAN.equals(criteria.getOperator()))
                results = indexController.findAllAbove(criteria.getValue(), false);
            else if(QueryCriteriaOperator.GREATER_THAN_EQUAL.equals(criteria.getOperator()))
                results = indexController.findAllAbove(criteria.getValue(), true);
            else if(QueryCriteriaOperator.LESS_THAN.equals(criteria.getOperator()))
                results = indexController.findAllBelow(criteria.getValue(), false);
            else if(QueryCriteriaOperator.LESS_THAN_EQUAL.equals(criteria.getOperator()))
                results = indexController.findAllBelow(criteria.getValue(), true);
            else
                results = indexController.findAll(criteria.getValue()).keySet();



            results.forEach(reference ->
            {
                if (existingValues.containsKey(reference)) {
                    returnValue.put(reference, reference);
                }
            });
        }

        return returnValue;
    }
}
