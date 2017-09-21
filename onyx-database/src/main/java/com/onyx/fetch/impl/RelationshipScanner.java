package com.onyx.fetch.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;
import com.onyx.diskmap.node.SkipListNode;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InvalidConstructorException;
import com.onyx.exception.InvalidQueryException;
import com.onyx.fetch.PartitionReference;
import com.onyx.fetch.ScannerFactory;
import com.onyx.fetch.TableScanner;
import com.onyx.helpers.PartitionHelper;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryPartitionMode;
import com.onyx.interactors.record.RecordInteractor;
import com.onyx.interactors.relationship.RelationshipInteractor;
import com.onyx.interactors.relationship.data.RelationshipReference;
import com.onyx.util.ReflectionUtil;
import com.onyx.util.map.CompatHashMap;

import java.util.*;

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * Scan relationships for matching criteria
 */
public class RelationshipScanner extends AbstractTableScanner implements TableScanner {

    @SuppressWarnings("WeakerAccess")
    protected RelationshipDescriptor relationshipDescriptor;

    /**
     * Constructor
     *
     * @param criteria Query Criteria
     * @param classToScan Class type to scan
     * @param descriptor Entity descriptor of entity type to scan
     */
    public RelationshipScanner(QueryCriteria criteria, Class classToScan, EntityDescriptor descriptor, MapBuilder temporaryDataFile, Query query, SchemaContext context, PersistenceManager persistenceManager) throws OnyxException
    {
        super(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager);
    }

    /**
     * Full Table get all relationships
     *
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<PartitionReference, PartitionReference> scan() throws OnyxException
    {

        Map<PartitionReference, PartitionReference> startingPoint = new HashMap();

        // We do not support querying relationships by all partitions.
        // This would be about the most rediculous non optimized query
        // and is frowned upon
        if (this.getQuery().getPartition() == QueryPartitionMode.ALL)
        {
            throw new InvalidQueryException();
        }

        // Added the ability to start with a partition
        if (this.getDescriptor().getPartition() != null) {
            // Get the partition ID
            IManagedEntity temp;
            try {
                temp = (IManagedEntity) ReflectionUtil.instantiate(getDescriptor().getEntityClass());
            } catch (IllegalAccessException | InstantiationException e) {
                throw new InvalidConstructorException(InvalidConstructorException.CONSTRUCTOR_NOT_FOUND, e);
            }
            PartitionHelper.setPartitionValueForEntity(temp, getQuery().getPartition(), getContext());
            long partitionId = getPartitionId(temp);

            for (SkipListNode reference : (Set<SkipListNode>) ((DiskMap) getRecords()).referenceSet()) {
                startingPoint.put(new PartitionReference(partitionId, reference.recordId), new PartitionReference(partitionId, reference.recordId));
            }
        } else {
            // Hydrate the entire reference set of parent entity before scanning the relationship
            for (SkipListNode reference : (Set<SkipListNode>) ((DiskMap) getRecords()).referenceSet()) {
                startingPoint.put(new PartitionReference(0L, reference.recordId), new PartitionReference(0L, reference.recordId));
            }
        }

        return scan(startingPoint);
    }

    /**
     * Full Scan with existing values
     *
     * @param existingValues Existing values to check criteria
     * @return filterd map of results matching additional criteria
     * @throws OnyxException Cannot scan relationship values
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<PartitionReference, PartitionReference> scan(Map<PartitionReference, ? extends PartitionReference> existingValues) throws OnyxException
    {
        // Retain the original attribute
        final String originalAttribute = getCriteria().getAttribute();

        // Get the attribute name.  If it has multiple tokens, that means it is another relationship.
        // If that is the case, we gotta find that one
        final String[] segments = originalAttribute.split("\\.");

        // Map <ChildIndex, ParentIndex> // Inverted list so we can use it to scan using an normal full table scanner or index scanner
        final Map<PartitionReference, PartitionReference> relationshipIndexes = getRelationshipIndexes(segments[0], existingValues);
        final Map<PartitionReference,PartitionReference> returnValue = new CompatHashMap();

        // We are going to set the attribute name so we can continue going down the chain.  We are going to remove the
        // processed token through
        getCriteria().setAttribute(getCriteria().getAttribute().replaceFirst(segments[0] + "\\.", ""));

        // Get the next scanner because we are not at the end of the line.  Otherwise, we would not have gotten to this place
        final TableScanner tableScanner = ScannerFactory.getInstance(getContext()).getScannerForQueryCriteria(getCriteria(), relationshipDescriptor.getInverseClass(), getTemporaryDataFile(), getQuery(), getPersistenceManager());

        // Sweet, lets get the scanner.  Note, this very well can be recursive, but sooner or later it will get to the
        // other scanners
        final Map<PartitionReference,PartitionReference> childIndexes = tableScanner.scan(relationshipIndexes);

        // Swap parent / child after getting results.  This is because we can use the child when hydrating stuff
        for (PartitionReference childIndex : childIndexes.keySet())
        {
            // Gets the parent
            returnValue.put(relationshipIndexes.get(childIndex), childIndex);
        }

        getCriteria().setAttribute(originalAttribute);

        return returnValue;
    }

    /**
     * Get Relationship Indexes
     *
     * @param attribute Attribute match
     * @param existingValues Existing values to check
     * @return References that match criteria
     */
    @SuppressWarnings("unchecked")
    private Map<PartitionReference, PartitionReference> getRelationshipIndexes(String attribute, Map<PartitionReference, ? extends PartitionReference> existingValues) throws OnyxException {
        final Map<PartitionReference, PartitionReference> allResults = new CompatHashMap();

        final Iterator<PartitionReference> iterator = existingValues.keySet().iterator();

        if (this.getQuery().getPartition() == QueryPartitionMode.ALL) {
            throw new InvalidQueryException();
        }

        relationshipDescriptor = this.getDescriptor().getRelationships().get(attribute);
        final RelationshipInteractor relationshipInteractor = getContext().getRelationshipInteractor(relationshipDescriptor);

        List<RelationshipReference> relationshipIdentifiers;
        PartitionReference keyValue;

        while (iterator.hasNext()) {
            if (getQuery().isTerminated())
                return allResults;

            keyValue = iterator.next();

            relationshipIdentifiers = relationshipInteractor.getRelationshipIdentifiersWithReferenceId((PartitionReference) keyValue);
            for (RelationshipReference id : relationshipIdentifiers) {
                RecordInteractor recordInteractorForPartition = null;
                if(id.getPartitionId() == 0L)
                    recordInteractorForPartition= getDefaultInverseRecordInteractor();
                else
                    recordInteractorForPartition = getRecordInteractorForPartition(id.getPartitionId());

                PartitionReference reference = new PartitionReference(id.getPartitionId(), recordInteractorForPartition.getReferenceId(id.getIdentifier()));
                allResults.put(reference, keyValue);
            }
        }

        return allResults;
    }

    /**
     * Grabs the inverse record controller
     *
     * @return Record controller for inverse relationship
     * @throws OnyxException Cannot get record controller for inverse relationship
     */
    private RecordInteractor getDefaultInverseRecordInteractor() throws OnyxException
    {
        final EntityDescriptor inverseDescriptor = getContext().getBaseDescriptorForEntity(relationshipDescriptor.getInverseClass());
        return getContext().getRecordInteractor(inverseDescriptor);
    }
}
