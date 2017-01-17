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
import com.onyx.structure.MapBuilder;

import java.util.*;

/**
 * Created by timothy.osborn on 2/10/15.
 */
public class IndexScanner extends AbstractTableScanner implements TableScanner {

    protected IndexController indexController = null;

    /**
     * Constructor
     *
     * @param criteria
     * @param classToScan
     * @param descriptor
     * @param temporaryDataFile
     * @throws EntityException
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
     * @return
     * @throws EntityException
     */
    @Override
    public Map<Long, Long> scan() throws EntityException
    {
        final Map<Long, Long> returnValue = new HashMap();
        final List<Long> references = new ArrayList<>();

        if(criteria.getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;

                if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN)
                    indexController.findAllAbove(idValue, false).forEach(o -> references.add(o));
                else if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                    indexController.findAllAbove(idValue, true).forEach(o -> references.add(o));
                else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN)
                    indexController.findAllBelow(idValue, false).forEach(o -> references.add(o));
                else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                    indexController.findAllBelow(idValue, true).forEach(o -> references.add(o));
                else
                    indexController.findAll(idValue).forEach(o -> references.add(o));
            }
        }
        else
        {

            if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN)
                indexController.findAllAbove(criteria.getValue(), false).forEach(o -> references.add(o));
            else if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                indexController.findAllAbove(criteria.getValue(), true).forEach(o -> references.add(o));
            else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN)
                indexController.findAllBelow(criteria.getValue(), false).forEach(o -> references.add(o));
            else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                indexController.findAllBelow(criteria.getValue(), true).forEach(o -> references.add(o));
            else
                indexController.findAll(criteria.getValue()).forEach(o -> references.add(o));
        }

        references.stream().forEach(val->
        {
            returnValue.put(val, val);
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
    public Map<Long, Long> scan(Map<Long, Long> existingValues) throws EntityException
    {
        final Map<Long, Long> returnValue = new HashMap();

        if(criteria.getValue() instanceof List)
        {
            for(Object idValue : (List<Object>) criteria.getValue())
            {
                if(query.isTerminated())
                    return returnValue;


                Set<Long> results = null;

                if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN)
                    results = indexController.findAllAbove(idValue, false);
                else if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                    results = indexController.findAllAbove(idValue, true);
                else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN)
                    results = indexController.findAllBelow(idValue, false);
                else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                    results = indexController.findAllBelow(idValue, true);
                else
                    results = indexController.findAll(idValue);

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
            Set<Long> results = null;

            if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN)
                results = indexController.findAllAbove(criteria.getValue(), false);
            else if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                results = indexController.findAllAbove(criteria.getValue(), true);
            else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN)
                results = indexController.findAllBelow(criteria.getValue(), false);
            else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                results = indexController.findAllBelow(criteria.getValue(), true);
            else
                results = indexController.findAll(criteria.getValue());

            results.stream().forEach(reference ->
            {
                if (existingValues.containsKey(reference)) {
                    returnValue.put(reference, reference);
                }
            });
        }

        return returnValue;
    }
}
