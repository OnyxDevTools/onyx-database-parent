package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.fetch.PartitionReference;
import com.onyx.util.map.CompatHashMap;
import com.onyx.exception.OnyxException;
import com.onyx.fetch.TableScanner;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.interactors.record.RecordInteractor;
import com.onyx.diskmap.MapBuilder;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by timothy.osborn on 1/3/15.
 * <p>
 * Scan identifier values
 */
public class IdentifierScanner extends AbstractTableScanner implements TableScanner {

    /**
     * Constructor
     *
     * @param criteria    Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor  Entity descriptor of entity type to scan
     * @throws OnyxException Cannot find entity information
     */
    public IdentifierScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws OnyxException {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
    }

    /**
     * Full scan with ids
     *
     * @return Identifiers matching criteria
     * @throws OnyxException Cannot scan records
     */
    @Override
    public Map<PartitionReference, PartitionReference> scan() throws OnyxException {
        final Map<PartitionReference, PartitionReference> returnValue = new CompatHashMap<>();

        final RecordInteractor recordInteractor = getContext().getRecordInteractor(getDescriptor());

        // If it is an in clause
        if (getCriteria().getValue() instanceof List) {
            for (Object idValue : (List) getCriteria().getValue()) {
                if (getQuery().isTerminated())
                    return returnValue;

                PartitionReference referenceId = new PartitionReference(getPartitionId(), recordInteractor.getReferenceId(idValue));

                // The id does exist, lets add it to the results
                if (referenceId.reference > 0L) {
                    returnValue.put(referenceId, referenceId);
                }
            }
        }


        // Its an equals, if the object exists, add it to the results
        else {

            Set<Long> values = null;

            if (getCriteria().getOperator() == QueryCriteriaOperator.GREATER_THAN)
                values = recordInteractor.findAllAbove(getCriteria().getValue(), false);
            else if (getCriteria().getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                values = recordInteractor.findAllAbove(getCriteria().getValue(), true);
            else if (getCriteria().getOperator() == QueryCriteriaOperator.LESS_THAN)
                values = recordInteractor.findAllBelow(getCriteria().getValue(), false);
            else if (getCriteria().getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                values = recordInteractor.findAllBelow(getCriteria().getValue(), true);
            else {
                PartitionReference referenceId = new PartitionReference(getPartitionId(), recordInteractor.getReferenceId(getCriteria().getValue()));
                if (referenceId.reference > 0L) {
                    returnValue.put(referenceId, referenceId);
                }
            }

            if (values != null) {
                for (Long aLong : values)
                    returnValue.put(new PartitionReference(getPartitionId(), aLong), new PartitionReference(getPartitionId(), aLong));
            }

        }

        return returnValue;
    }

    /**
     * Scan existing values for identifiers
     *
     * @param existingValues Existing values to check
     * @return Existing values that meed additional criteria
     * @throws OnyxException Cannot scan records
     */
    @Override
    public Map<PartitionReference, PartitionReference> scan(Map<PartitionReference, ? extends PartitionReference> existingValues) throws OnyxException {
        final Map<PartitionReference, PartitionReference> returnValue = new CompatHashMap<>();

        final RecordInteractor recordInteractor = getContext().getRecordInteractor(getDescriptor());

        Iterator<PartitionReference> iterator = existingValues.keySet().iterator();

        PartitionReference key;

        while (iterator.hasNext()) {
            key = iterator.next();

            // If it is an in clause
            if (getCriteria().getValue() instanceof List) {
                for (Object idValue : (List) getCriteria().getValue()) {
                    if (getQuery().isTerminated())
                        return returnValue;

                    PartitionReference referenceId = new PartitionReference(getPartitionId(), recordInteractor.getReferenceId(idValue));

                    if (key.equals(referenceId)) {
                        returnValue.put(referenceId, referenceId);
                    }
                }
            }
            // Its an equals, if the object exists, add it to the results
            else {

                Set<Long> values = null;

                if (getCriteria().getOperator() == QueryCriteriaOperator.GREATER_THAN)
                    values = recordInteractor.findAllAbove(getCriteria().getValue(), false);
                else if (getCriteria().getOperator() == QueryCriteriaOperator.GREATER_THAN_EQUAL)
                    values = recordInteractor.findAllAbove(getCriteria().getValue(), true);
                else if (getCriteria().getOperator() == QueryCriteriaOperator.LESS_THAN)
                    values = recordInteractor.findAllBelow(getCriteria().getValue(), false);
                else if (getCriteria().getOperator() == QueryCriteriaOperator.LESS_THAN_EQUAL)
                    values = recordInteractor.findAllBelow(getCriteria().getValue(), true);
                else {
                    PartitionReference referenceId = new PartitionReference(getPartitionId(), recordInteractor.getReferenceId(getCriteria().getValue()));

                    if (referenceId.reference > 0L) {
                        returnValue.put(referenceId, referenceId);
                    }
                }

                if (values != null) {
                    for (PartitionReference aLong : existingValues.values()) {
                        if (values.contains(aLong.reference))
                            returnValue.put(aLong, aLong);
                    }
                }
            }
        }

        return returnValue;
    }


}
