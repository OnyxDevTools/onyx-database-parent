package com.onyx.fetch;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.record.RecordController;
import com.onyx.relationship.RelationshipController;
import com.onyx.relationship.RelationshipReference;
import com.onyx.util.map.CompatMap;
import com.onyx.util.map.CompatWeakHashMap;
import com.onyx.exception.EntityException;
import com.onyx.exception.InvalidDataTypeForOperator;
import com.onyx.helpers.PartitionContext;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.util.CompareUtil;

import java.util.*;

/**
 * Created by timothy.osborn on 2/11/15.
 * This class sorts query results only hydrating what it needs without putting attributes and entities in memory.
 */
class PartitionSortCompare<T> extends PartitionContext implements Comparator<T>
{
    @SuppressWarnings("unused")
    protected final Query query;
    @SuppressWarnings("unused")
    protected String context;

    private List<ScannerProperties> scanObjects = null;
    private QueryOrder[] orderBy = null;

    private final List<CompatMap<Object, Object>> parentObjects = new ArrayList<>();

    /**
     * Constructor
     *
     * @param orderBy Order By criteria
     * @param descriptor Entity descriptor
     * @param context Schema context
     *
     */
    @SuppressWarnings("unchecked")
    PartitionSortCompare(Query query, QueryOrder[] orderBy, EntityDescriptor descriptor, SchemaContext context)
    {
        super(context, descriptor);

        this.contextId = context.getContextId();
        this.query = query;
        final String attributes[] = new String[orderBy.length];
        int i = 0;
        for (QueryOrder order : orderBy)
        {
            attributes[i] = order.getAttribute();
            parentObjects.add(new CompatWeakHashMap<>());
            i++;
        }

        try
        {
            scanObjects = ScannerProperties.getScannerProperties(attributes, descriptor, query, context);
        } catch (EntityException ignore)
        {}

        this.orderBy = orderBy;
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compare(T t1, T t2)
    {
        Object attribute1 = null;
        Object attribute2 = null;

        QueryOrder queryOrder;

        for (int i = 0; i < scanObjects.size(); i++)
        {

            queryOrder = orderBy[i];
            final ScannerProperties scannerProperties = scanObjects.get(i);

            try
            {
                final CompatMap attributeValues = parentObjects.get(i);

                attribute1 = attributeValues.computeIfAbsent(t1, (reference) -> {
                    try
                    {
                        return getValue(scannerProperties, reference);
                    } catch (Exception e)
                    {
                        return null;
                    }

                });

                attribute2 = attributeValues.computeIfAbsent(t2, (reference) -> {
                    try
                    {
                        return getValue(scannerProperties, reference);
                    } catch (Exception e)
                    {
                        return null;
                    }
                });
            } catch (Exception e)
            {
                e.printStackTrace();
            }

            try
            {
                // Use the generic comparison utility to see if attribute 1 is greater than attribute 2
                if (CompareUtil.compare(attribute2, attribute1, QueryCriteriaOperator.GREATER_THAN))
                {
                    // If the first one is greater, lets see if we are sorting in ascending
                    return queryOrder.isAscending() ? 1 : -1;
                }
                // Check less than
                else if (CompareUtil.compare(attribute2, attribute1, QueryCriteriaOperator.LESS_THAN))
                {
                    return queryOrder.isAscending() ? -1 : 1;
                }
            } catch (InvalidDataTypeForOperator invalidDataTypeForOperator)
            {
                return 0;
            }
        }

        return 1;
    }

    /**
     * Get an attribute value
     *
     * @param scannerProperties Scanner property
     * @param reference Object reference
     * @return The attribute value.  Can also be a relationship attribute value
     * @throws EntityException Exception when trying to hydrate attribute
     */
    @SuppressWarnings("WeakerAccess")
    public Object getValue(ScannerProperties scannerProperties, Object reference) throws EntityException
    {
        if(scannerProperties.relationshipDescriptor != null)
            return getRelationshipValue(reference, scannerProperties);

        if(reference instanceof PartitionReference)
        {
            PartitionReference ref = (PartitionReference) reference;
            return getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(scannerProperties.attributeDescriptor.getField(), ref.reference);
        }

        return scannerProperties.recordController.getAttributeWithReferenceId(scannerProperties.attributeDescriptor.getField(), (long) reference);
    }

    /**
     * Hydrates a to many relationship and formats in the shape of a map
     * @param entry Query reference entry
     * @param properties Scanner properties
     * @return List of to many relationships
     * @throws EntityException General exception
     *
     * @since 1.3.0
     */
    private Object getRelationshipValue(Object entry, ScannerProperties properties) throws EntityException
    {
        // Get Relationship controller
        final RelationshipController relationshipController = getContext().getRelationshipController(properties.relationshipDescriptor);

        List<RelationshipReference> relationshipReferences;

        // Get relationship references
        if (entry instanceof PartitionReference) {
            relationshipReferences = relationshipController.getRelationshipIdentifiersWithReferenceId((PartitionReference)entry);
        }
        else
        {
            relationshipReferences = relationshipController.getRelationshipIdentifiersWithReferenceId((long)entry);
        }

        if(relationshipReferences.size() == 0)
            return null;

        // Iterate through relationship references and get the values of the relationship
        final RelationshipReference ref = relationshipReferences.get(0);
        Object relationshipAttributeValue;

        if(ref.partitionId > 0)
        {
            final PartitionContext partitionContext = new PartitionContext(getContext(), properties.descriptor);
            final RecordController recordController = partitionContext.getRecordControllerForPartition(ref.partitionId);
            relationshipAttributeValue = recordController.getAttributeWithReferenceId(properties.attributeDescriptor.getField(), recordController.getReferenceId(ref.identifier));
        }
        else
        {
            relationshipAttributeValue = properties.recordController.getAttributeWithReferenceId(properties.attributeDescriptor.getField(), properties.recordController.getReferenceId(ref.identifier));
        }

        return relationshipAttributeValue;
    }
}