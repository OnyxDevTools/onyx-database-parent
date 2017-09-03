package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.util.map.CompatHashMap;
import com.onyx.exception.EntityException;
import com.onyx.fetch.TableScanner;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.record.RecordController;
import com.onyx.diskmap.MapBuilder;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * Scan identifier values
 */
public class IdentifierScanner extends AbstractTableScanner implements TableScanner
{

    /**
     * Constructor
     *
     * @param criteria Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor Entity descriptor of entity type to scan
     *
     * @throws EntityException Cannot find entity information
     */
    public IdentifierScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
    }

    /**
     * Full scan with ids
     *
     * @return Identifiers matching criteria
     * @throws EntityException Cannot scan records
     */
    @Override
    public Map<Long, Long> scan() throws EntityException
    {
        final Map<Long, Long> returnValue = new CompatHashMap<>();

        final RecordController recordController = getContext().getRecordController(descriptor);

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

            Set<Long> values = null;

            if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN)
                values = recordController.findAllAbove(criteria.getValue(), false);
            else if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                values = recordController.findAllAbove(criteria.getValue(), true);
            else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN)
                values = recordController.findAllBelow(criteria.getValue(), false);
            else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                values = recordController.findAllBelow(criteria.getValue(), true);
            else
            {
                long referenceId = recordController.getReferenceId(criteria.getValue());
                if(referenceId > -1)
                {
                    returnValue.put(referenceId, referenceId);
                }
            }

            if(values != null) {
                for (Long aLong : values)
                    returnValue.put(aLong, aLong);
            }

        }

        return returnValue;
    }

    /**
     * Scan existing values for identifiers
     *
     * @param existingValues Existing values to check
     * @return Existing values that meed additional criteria
     * @throws EntityException Cannot scan records
     */
    @Override
    public Map<Long, Long> scan(Map<Long, Long> existingValues) throws EntityException
    {
        final Map<Long, Long> returnValue = new CompatHashMap<>();

        final RecordController recordController = getContext().getRecordController(descriptor);

        Iterator<Long> iterator = existingValues.keySet().iterator();

        Long key;

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

                Set<Long> values = null;

                if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN)
                    values = recordController.findAllAbove(criteria.getValue(), false);
                else if(criteria.getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                    values = recordController.findAllAbove(criteria.getValue(), true);
                else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN)
                    values = recordController.findAllBelow(criteria.getValue(), false);
                else if(criteria.getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                    values = recordController.findAllBelow(criteria.getValue(), true);
                else
                {
                    long referenceId = recordController.getReferenceId(criteria.getValue());
                    if(referenceId > -1)
                    {
                        returnValue.put(referenceId, referenceId);
                    }
                }

                if(values != null) {
                    for (Long aLong : existingValues.values())
                    {
                        if(values.contains(aLong))
                            returnValue.put(aLong, aLong);
                    }
                }
            }
        }

        return returnValue;
    }


}
