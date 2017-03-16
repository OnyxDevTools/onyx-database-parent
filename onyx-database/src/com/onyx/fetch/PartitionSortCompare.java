package com.onyx.fetch;

import com.onyx.descriptor.EntityDescriptor;
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
    private final Map<Object, Object> indexValues;

    private final List<CompatMap<Object, Object>> parentObjects = new ArrayList<>();
    private final List<CompatMap<Object, Object>> childrenObjects = new ArrayList<>();

    /**
     * Constructor
     *
     * @param orderBy Order By criteria
     * @param indexValues Index values to sort
     * @param descriptor Entity descriptor
     * @param context Schema context
     *
     */
    @SuppressWarnings("unchecked")
    PartitionSortCompare(Query query, QueryOrder[] orderBy, Map indexValues, EntityDescriptor descriptor, SchemaContext context)
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
            childrenObjects.add(new CompatWeakHashMap<>());
            i++;
        }

        try
        {
            scanObjects = ScannerProperties.getScannerProperties(attributes, descriptor, query, context);
        } catch (EntityException ignore)
        {}

        this.orderBy = orderBy;
        this.indexValues = indexValues;
    }

    @SuppressWarnings("unchecked")
    @Override
    public int compare(T t1, T t2)
    {

        Object o1 = t1;
        Object o2 = t2;

        Object attribute1 = null;
        Object attribute2 = null;

        QueryOrder queryOrder;

        for (int i = 0; i < scanObjects.size(); i++)
        {

            queryOrder = orderBy[i];
            final ScannerProperties scannerProperties = scanObjects.get(i);

            try
            {
                CompatMap kiddos = childrenObjects.get(i);
                CompatMap papas = parentObjects.get(i);

                if (scannerProperties.useParentDescriptor)
                {
                    final Object a1 = o1;

                    attribute1 = kiddos.computeIfAbsent(o1, o -> {
                        try
                        {
                            if(a1 instanceof PartitionReference)
                            {
                                PartitionReference ref = (PartitionReference) a1;
                                return getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(scannerProperties.attributeDescriptor.getField(), ((PartitionReference)a1).reference);
                            }
                            else
                            {
                                return scannerProperties.recordController.getAttributeWithReferenceId(scannerProperties.attributeDescriptor.getField(), (long) a1);
                            }
                        } catch (Exception e)
                        {
                            return null;
                        }
                    });

                    final Object b2 = o2;
                    attribute2 = kiddos.computeIfAbsent(o2, (attributeVal) -> {
                        try
                        {

                            if(b2 instanceof PartitionReference)
                            {
                                PartitionReference ref = (PartitionReference) b2;
                                return getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(scannerProperties.attributeDescriptor.getField(), ((PartitionReference)b2).reference);
                            }
                            else
                            {
                                return scannerProperties.recordController.getAttributeWithReferenceId(scannerProperties.attributeDescriptor.getField(), (long) b2);
                            }
                        } catch (Exception e)
                        {
                            return null;
                        }
                    });

                } else
                {

                    o1 = indexValues.get(o1);
                    o2 = indexValues.get(o2);

                    final Object a1 = o1;

                    attribute1 = papas.computeIfAbsent(o1, (attributeVal) -> {
                        if(attributeVal != null)
                        {
                            return attributeVal;
                        }
                        try
                        {
                            if(a1 instanceof PartitionReference)
                            {
                                PartitionReference ref = (PartitionReference) a1;
                                return getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(scannerProperties.attributeDescriptor.getField(), ((PartitionReference)a1).reference);
                            }

                            return scannerProperties.recordController.getAttributeWithReferenceId(scannerProperties.attributeDescriptor.getField(), (long) a1);
                        } catch (Exception e)
                        {
                            return null;
                        }

                    });

                    final Object a2 = o2;

                    attribute2 = papas.computeIfAbsent(o2, (attributeVal) -> {
                        if(attributeVal != null)
                        {
                            return attributeVal;
                        }
                        try
                        {
                            if(a2 instanceof PartitionReference)
                            {
                                PartitionReference ref = (PartitionReference) a2;
                                return getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(scannerProperties.attributeDescriptor.getField(), ((PartitionReference)a2).reference);
                            }

                            return scannerProperties.recordController.getAttributeWithReferenceId(scannerProperties.attributeDescriptor.getField(), (long) a2);
                        } catch (Exception e)
                        {
                            return null;
                        }
                    });

                }

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
}