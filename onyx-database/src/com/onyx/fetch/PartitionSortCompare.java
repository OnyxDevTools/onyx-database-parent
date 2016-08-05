package com.onyx.fetch;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.exception.InvalidDataTypeForOperator;
import com.onyx.helpers.PartitionContext;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.util.CompareUtil;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Created by timothy.osborn on 2/11/15.
 */
public class PartitionSortCompare<T> extends PartitionContext implements Comparator<T>
{
    protected List<ScannerProperties> scanObjects = null;
    protected QueryOrder[] orderBy = null;
    protected Map<Object, Object> indexValues;

    protected List<Map<Object, Object>> parentObjects = new ArrayList<>();
    protected List<Map<Object, Object>> childrenObjects = new ArrayList<>();
    protected Query query;
    protected SchemaContext context;
    protected PartitionQueryController queryController;

    /**
     * Constructor
     *
     * @param orderBy
     * @param indexValues
     * @throws com.onyx.exception.EntityException
     */
    public PartitionSortCompare(Query query, QueryOrder[] orderBy, Map indexValues, EntityDescriptor descriptor, SchemaContext context, PartitionQueryController queryController)
    {
        super(context, descriptor);

        this.context = context;
        this.query = query;
        final String attributes[] = new String[orderBy.length];
        int i = 0;
        for (QueryOrder order : orderBy)
        {
            attributes[i] = order.getAttribute();
            parentObjects.add(new WeakHashMap());
            childrenObjects.add(new WeakHashMap());
            i++;
        }

        try
        {
            scanObjects = ScannerProperties.getScannerProperties(attributes, descriptor, query, context);
        } catch (EntityException e)
        {}

        this.orderBy = orderBy;
        this.indexValues = indexValues;
        this.queryController = queryController;
    }

    @Override
    public int compare(T t1, T t2)
    {

        Object o1 = (Object)t1;
        Object o2 = (Object)t2;

        Object attribute1 = null;
        Object attribute2 = null;

        QueryOrder queryOrder = null;

        IManagedEntity e1 = null;
        IManagedEntity e2 = null;

        for (int i = 0; i < scanObjects.size(); i++)
        {

            queryOrder = orderBy[i];
            final ScannerProperties scannerProperties = scanObjects.get(i);

            try
            {
                Map kiddos = childrenObjects.get(i);
                Map papas = parentObjects.get(i);

                if (scannerProperties.useParentDescriptor == true)
                {
                    attribute1 = kiddos.compute(o1, new BiFunction<Object, Object, Object>() {
                        @Override
                        public Object apply(Object aLong, Object attributeVal)
                        {
                            if(attributeVal != null)
                            {
                                return attributeVal;
                            }
                            try
                            {
                                if(aLong instanceof PartitionReference)
                                {
                                    PartitionReference ref = (PartitionReference) aLong;
                                    return getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(scannerProperties.attributeDescriptor.field.field.getName(), ((PartitionReference)aLong).reference);
                                }
                                else
                                {
                                    return scannerProperties.recordController.getAttributeWithReferenceId(scannerProperties.attributeDescriptor.field.field.getName(), (long) aLong);
                                }
                            } catch (Exception e)
                            {
                                return null;
                            }

                        }
                    });
                    attribute2 = kiddos.compute(o2, new BiFunction<Object, Object, Object>() {
                        @Override
                        public Object apply(Object aLong, Object attributeVal)
                        {
                            if(attributeVal != null)
                            {
                                return attributeVal;
                            }
                            try
                            {

                                if(aLong instanceof PartitionReference)
                                {
                                    PartitionReference ref = (PartitionReference) aLong;
                                    return getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(scannerProperties.attributeDescriptor.field.field.getName(), ((PartitionReference)aLong).reference);
                                }
                                else
                                {
                                    return scannerProperties.recordController.getAttributeWithReferenceId(scannerProperties.attributeDescriptor.field.field.getName(), (long) aLong);
                                }
                            } catch (Exception e)
                            {
                                return null;
                            }
                        }
                    });

                } else
                {

                    o1 = indexValues.get(o1);
                    o2 = indexValues.get(o2);

                    attribute1 = papas.compute(o1, new BiFunction<Object, Object, Object>() {
                        @Override
                        public Object apply(Object aLong, Object attributeVal)
                        {
                            if(attributeVal != null)
                            {
                                return attributeVal;
                            }
                            try
                            {
                                if(aLong instanceof PartitionReference)
                                {
                                    PartitionReference ref = (PartitionReference) aLong;
                                    return getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(scannerProperties.attributeDescriptor.field.field.getName(), ((PartitionReference)aLong).reference);
                                }

                                return scannerProperties.recordController.getAttributeWithReferenceId(scannerProperties.attributeDescriptor.field.field.getName(), (long) aLong);
                            } catch (Exception e)
                            {
                                return null;
                            }

                        }
                    });
                    attribute2 = papas.compute(o2, new BiFunction<Object, Object, Object>() {
                        @Override
                        public Object apply(Object aLong, Object attributeVal)
                        {
                            if(attributeVal != null)
                            {
                                return attributeVal;
                            }
                            try
                            {
                                if(aLong instanceof PartitionReference)
                                {
                                    PartitionReference ref = (PartitionReference) aLong;
                                    return getRecordControllerForPartition(ref.partition).getAttributeWithReferenceId(scannerProperties.attributeDescriptor.field.field.getName(), ((PartitionReference)aLong).reference);
                                }

                                return scannerProperties.recordController.getAttributeWithReferenceId(scannerProperties.attributeDescriptor.field.field.getName(), (long) aLong);
                            } catch (Exception e)
                            {
                                return null;
                            }
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
                // They must be equal, onto the next order by criteria
                else
                {
                    continue;
                }
            } catch (InvalidDataTypeForOperator invalidDataTypeForOperator)
            {
                return 0;
            }
        }

        return 1;
    }
};