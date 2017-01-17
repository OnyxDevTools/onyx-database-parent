package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.fetch.TableScanner;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.record.RecordController;
import com.onyx.structure.MapBuilder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by timothy.osborn on 1/3/15.
 */
public class IdentifierScanner extends AbstractTableScanner implements TableScanner
{

    /**
     * Constructor
     *
     * @param criteria
     * @param classToScan
     * @param descriptor
     * @throws EntityException
     */
    public IdentifierScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
    }

    /**
     * Full scan with ids
     *
     * @return
     * @throws EntityException
     */
    @Override
    public Map<Long, Long> scan() throws EntityException
    {
        final Map<Long, Long> returnValue = new HashMap();

        final RecordController recordController = context.getRecordController(descriptor);

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
                    returnValue.put(referenceId, referenceId);
                }
            }
        }


        // Its an equals, if the object exists, add it to the results
        else
        {


            if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN)
                recordController.findAllAbove(criteria.getValue(), false).forEach(aLong -> returnValue.put(aLong, aLong));
            else if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                recordController.findAllAbove(criteria.getValue(), true).forEach(aLong -> returnValue.put(aLong, aLong));
            else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN)
                recordController.findAllBelow(criteria.getValue(), false).forEach(aLong -> returnValue.put(aLong, aLong));
            else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                recordController.findAllBelow(criteria.getValue(), true).forEach(aLong -> returnValue.put(aLong, aLong));
            else
            {
                long referenceId = recordController.getReferenceId(criteria.getValue());
                if(referenceId > -1)
                {
                    returnValue.put(referenceId, referenceId);
                }
            }

        }

        return returnValue;
    }

    /**
     * Scan existing values for identifiers
     *
     * @param existingValues
     * @return
     * @throws EntityException
     */
    @Override
    public Map<Long, Long> scan(Map<Long, Long> existingValues) throws EntityException
    {
        final Map<Long, Long> returnValue = new HashMap();

        final RecordController recordController = context.getRecordController(descriptor);

        Iterator<Long> iterator = existingValues.keySet().iterator();

        Long key = null;

        while (iterator.hasNext())
        {
            key = iterator.next();

            // If it is an in clause
            if(criteria.getValue() instanceof List)
            {
                for (Object idValue : (List)criteria.getValue())
                {
                    if(query.isTerminated())
                        return returnValue;

                    long referenceId = recordController.getReferenceId(idValue);

                    if(referenceId == key)
                    {
                        returnValue.put(referenceId, referenceId);
                    }
                }
            }
            // Its an equals, if the object exists, add it to the results
            else
            {
                if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN)
                    recordController.findAllAbove(criteria.getValue(), false).forEach(aLong -> returnValue.put(aLong, aLong));
                else if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                    recordController.findAllAbove(criteria.getValue(), true).forEach(aLong -> returnValue.put(aLong, aLong));
                else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN)
                    recordController.findAllBelow(criteria.getValue(), false).forEach(aLong -> returnValue.put(aLong, aLong));
                else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                    recordController.findAllBelow(criteria.getValue(), true).forEach(aLong -> returnValue.put(aLong, aLong));
                else
                {
                    long referenceId = recordController.getReferenceId(criteria.getValue());
                    if(referenceId > -1)
                    {
                        returnValue.put(referenceId, referenceId);
                    }
                }
            }
        }

        return returnValue;
    }


}
