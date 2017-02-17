package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.ScannerFactory;
import com.onyx.fetch.TableScanner;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.record.RecordController;
import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.RelationshipReference;
import com.onyx.structure.MapBuilder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by timothy.osborn on 1/3/15.
 */
public class RelationshipScanner extends AbstractTableScanner implements TableScanner {

    protected RelationshipDescriptor relationshipDescriptor;

    /**
     * Constructor
     *
     * @param criteria
     * @param classToScan
     * @param descriptor
     */
    public RelationshipScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws EntityException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);

    }

    /**
     * Full Table get all relationships
     *
     */
    @Override
    public Map<Long, Long> scan() throws EntityException
    {
        return scan(records);
    }

    /**
     * Full Scan with existing values
     *
     * @param existingValues
     * @return
     * @throws EntityException
     */
    @Override
    public Map scan(Map existingValues) throws EntityException
    {
        // Retain the original attribute
        final String originalAttribute = criteria.getAttribute();

        // Get the attribute name.  If it has multiple tokens, that means it is another relationship.
        // If that is the case, we gotta find that one
        final String[] segments = originalAttribute.split("\\.");

        // Map <ChildIndex, ParentIndex> // Inverted list so we can use it to scan using an normal full table scanner or index scanner
        final Map relationshipIndexes = getRelationshipIndexes(segments[0], existingValues);
        final Map returnValue = new HashMap();

        // We are going to set the attribute name so we can continue going down the chain.  We are going to remove the
        // processed token through
        criteria.setAttribute(criteria.getAttribute().replaceFirst(segments[0] + "\\.", ""));

        // Get the next scanner because we are not at the end of the line.  Otherwise, we would not have gotten to this place
        final TableScanner tableScanner = ScannerFactory.getInstance(getContext()).getScannerForQueryCriteria(criteria, relationshipDescriptor.getInverseClass(), temporaryDataFile, query, persistenceManager);

        // Sweet, lets get the scanner.  Note, this very well can be recursive, but sooner or later it will get to the
        // other scanners
        final Map childIndexes = tableScanner.scan(relationshipIndexes);

        // Swap parent / child after getting results.  This is because we can use the child when hydrating stuff
        for (Object childIndex : childIndexes.keySet())
        {
            // Gets the parent
            returnValue.put(relationshipIndexes.get(childIndex), childIndex);
        }

        criteria.setAttribute(originalAttribute);

        return returnValue;
    }

    /**
     * Get Relationship Indexes
     *
     * @param attribute
     * @param existingValues
     * @return
     * @throws EntityException
     */
    protected Map getRelationshipIndexes(String attribute, Map existingValues) throws EntityException
    {
        final Map allResults = new HashMap();

        final Iterator iterator = existingValues.keySet().iterator();

        relationshipDescriptor = descriptor.getRelationships().get(attribute);
        final RelationshipController relationshipController = getContext().getRelationshipController(relationshipDescriptor);
        final RecordController inverseRecordController = getDefaultInverseRecordController();

        List<RelationshipReference> relationshipIdentifiers = null;
        Object keyValue = null;

        while(iterator.hasNext())
        {
            if(query.isTerminated())
                return allResults;

            keyValue = iterator.next();

            if(keyValue instanceof PartitionReference)
            {
                relationshipIdentifiers = relationshipController.getRelationshipIdentifiersWithReferenceId((PartitionReference)keyValue);
            }
            else
            {
                relationshipIdentifiers = relationshipController.getRelationshipIdentifiersWithReferenceId((long)keyValue);
            }
            for(RelationshipReference id : relationshipIdentifiers)
            {

                if(id.partitionId == 0)
                {
                    allResults.put(inverseRecordController.getReferenceId(id.identifier), keyValue);
                }
                else
                {
                    RecordController recordController = getRecordControllerForPartition(id.partitionId);
                    PartitionReference reference = new PartitionReference(id.partitionId, recordController.getReferenceId(id.identifier));
                    allResults.put(reference, keyValue);
                }

            }
        }

        return allResults;
    }

    /**
     * Grabs the inverse record controller
     *
     * @return
     * @throws EntityException
     */
    protected RecordController getDefaultInverseRecordController() throws EntityException
    {
        final EntityDescriptor inverseDescriptor = getContext().getBaseDescriptorForEntity(relationshipDescriptor.getInverseClass());
        return getContext().getRecordController(inverseDescriptor);
    }
}
