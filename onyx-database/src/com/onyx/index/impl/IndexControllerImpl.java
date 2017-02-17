package com.onyx.index.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.index.IndexController;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;
import com.onyx.structure.DefaultDiskSet;
import com.onyx.structure.DiskMap;
import com.onyx.structure.MapBuilder;
import com.onyx.structure.base.ScaledDiskMap;
import com.onyx.structure.node.Header;

import java.util.*;

/**
 * Created by timothy.osborn on 1/29/15.
 */
public class IndexControllerImpl implements IndexController {

    protected SchemaContext context;

    protected Map<Object, Header> references = null; // Stores the references for an index key
    protected Map<Long, Object> indexValues = null;
    protected RecordController recordController = null;
    protected IndexDescriptor indexDescriptor = null;
    protected EntityDescriptor descriptor;

    public IndexDescriptor getIndexDescriptor()
    {
        return indexDescriptor;
    }

    /**
     * Constructor with entity descriptor and index descriptor
     *
     * @param descriptor
     * @param indexDescriptor
     * @throws EntityException
     */
    public IndexControllerImpl(EntityDescriptor descriptor, IndexDescriptor indexDescriptor, SchemaContext context) throws EntityException
    {
        this.context = context;

        this.indexDescriptor = indexDescriptor;
        this.recordController = context.getRecordController(descriptor);
        this.descriptor = descriptor;
        final MapBuilder dataFile = context.getDataFile(descriptor);

        references = dataFile.getScalableMap(descriptor.getClazz().getName() + indexDescriptor.getName(), indexDescriptor.getLoadFactor());
        indexValues = dataFile.getScalableMap(descriptor.getClazz().getName() + indexDescriptor.getName() + "indexValues", indexDescriptor.getLoadFactor());
    }

    /**
     * Save an index key with the record reference
     *
     * @param indexValue
     * @param oldReference
     * @param reference
     * @throws EntityException
     */
    public void save(Object indexValue, long oldReference, long reference) throws EntityException
    {
        // Delete the old index key
        if(oldReference > 0)
        {
            delete(oldReference);
        }

        if(indexValue != null) {
            final MapBuilder dataFile = context.getDataFile(descriptor);

            references.compute(indexValue, (o, header) -> {
                if(header == null) {
                    header = dataFile.newMapHeader();
                }
                Map indexes = dataFile.getSkipListMap(header);
                indexes.put(reference, null);
                indexes = null;
                return header;
            });
            indexValues.compute(reference, (aLong, o) -> indexValue);
        }
    }

    /**
     * Delete an index key with a record reference
     *
     * @param reference
     * @throws EntityException
     */
    public void delete(long reference) throws EntityException
    {
        if(reference > 0)
        {
            Object indexValue = indexValues.remove(reference);
            if (indexValue != null)
            {
                final MapBuilder dataFile = context.getDataFile(descriptor);

                references.computeIfPresent(indexValue, (o, header) -> {
                    Map indexes = dataFile.getSkipListMap(header);
                    indexes.remove(reference);
                    indexes = null;
                    return header;
                });
            }
        }
    }

    /**
     * Find all index references
     *
     * @param indexValue
     * @return
     * @throws EntityException
     */
    public Map findAll(Object indexValue) throws EntityException
    {
        final Header header = references.get(indexValue);
        if(header == null)
            return new HashMap();
        final MapBuilder dataFile = context.getDataFile(descriptor);

        return dataFile.getSkipListMap(header);
    }

    /**
     * Find all index references
     *
     * @return
     * @throws EntityException
     */
    public Set<Object> findAllValues() throws EntityException
    {
        return references.keySet();
    }


    /**
     * Find all the references above and perhaps equal to the key parameter
     *
     * This has one prerequisite.  You must be using a ScaledDiskMap as the storage mechanism.  Otherwise it will not be
     * sorted.
     *
     * @param indexValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeValue Whether to compare above and equal or not.
     * @return A set of record references
     *
     * @throws EntityException Exception while reading the data structure
     *
     * @since 1.2.0
     */
    public Set<Long> findAllAbove(Object indexValue, boolean includeValue) throws EntityException
    {
        final Set<Long> allReferences = new HashSet();
        final Set<Long> diskReferences = ((ScaledDiskMap)references).above(indexValue, includeValue);

        final MapBuilder dataFile = context.getDataFile(descriptor);

        diskReferences.forEach(aLong -> {
            Set subSet = (Set)((ScaledDiskMap) references).getWithRecID(aLong);
            ((DefaultDiskSet)subSet).attachStorage(dataFile);
            allReferences.addAll(subSet);
        });

        return allReferences;
    }

    /**
     * Find all the references blow and perhaps equal to the key parameter
     *
     * This has one prerequisite.  You must be using a ScaledDiskMap as the storage mechanism.  Otherwise it will not be
     * sorted.
     *
     * @param indexValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeValue Whether to compare below and equal or not.
     * @return A set of record references
     *
     * @throws EntityException Exception while reading the data structure
     *
     * @since 1.2.0
     */
    public Set<Long> findAllBelow(Object indexValue, boolean includeValue) throws EntityException
    {
        final Set<Long> allReferences = new HashSet();
        final Set<Long> diskReferences = ((ScaledDiskMap)references).below(indexValue, includeValue);
        final MapBuilder dataFile = context.getDataFile(descriptor);
        diskReferences.forEach(aLong -> {
            Set subSet = (Set)((ScaledDiskMap) references).getWithRecID(aLong);
            ((DefaultDiskSet)subSet).attachStorage(dataFile);
            allReferences.addAll(subSet);
        });

        return allReferences;
    }

    /**
     * ReBuilds an index by iterating through all the values and re-mapping index values
     *
     * @throws EntityException
     */
    public void rebuild() throws EntityException
    {
        final MapBuilder dataFile = context.getDataFile(descriptor);

        final DiskMap records = (DiskMap)dataFile.getScalableMap(indexDescriptor.getEntityDescriptor().getClazz().getName(), indexDescriptor.getLoadFactor());
            final Iterator<Map.Entry> iterator = records.entrySet().iterator();

            // Iterate Through all of the values and re-structure the key key for the record id
            Map.Entry entry = null;
            while (iterator.hasNext()) {
                try {
                    entry = iterator.next();
                    long recId = records.getRecID(entry.getKey());
                    if (recId > 0) {
                        final Object indexValue = AbstractRecordController.getIndexValueFromEntity((IManagedEntity) entry.getValue(), indexDescriptor);
                        if (indexValue != null)
                            save(indexValue, recId, recId);
                    }
                }
                // Catch an exception so it may continue the routine
            catch (Exception ignore){}
        }
    }
}
