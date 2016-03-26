package com.onyx.index.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.index.IndexController;
import com.onyx.map.DiskMap;
import com.onyx.map.MapBuilder;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Created by timothy.osborn on 1/29/15.
 */
public class IndexControllerImpl implements IndexController {


    protected SchemaContext context;

    protected Map<Object, Set<Long>> references = null; // Stores the references for an index value
    protected Map<Long, Object> indexValues = null;
    protected RecordController recordController = null;
    protected MapBuilder dataFile = null;
    protected IndexDescriptor indexDescriptor = null;

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
        dataFile = context.getDataFile(descriptor);
        this.indexDescriptor = indexDescriptor;
        this.recordController = context.getRecordController(descriptor);

        references = dataFile.getHashMap(descriptor.getClazz().getCanonicalName() + indexDescriptor.getName());
        indexValues = dataFile.getHashMap(descriptor.getClazz().getCanonicalName() + indexDescriptor.getName() + "indexValues");
    }

    /**
     * Save an index value with the record reference
     *
     * @param indexValue
     * @param identifier
     * @throws EntityException
     */
    public void save(Object indexValue, long oldReference, long reference) throws EntityException
    {
        // Delete the old index value
        if(oldReference > 0)
        {
            delete(oldReference);
        }

        if(indexValue != null) {
            references.compute(indexValue, (o, longs) -> {
                if (longs == null) {
                    longs = new HashSet<Long>();
                }
                longs.add(reference);

                // Add previous index value

                return longs;
            });

            indexValues.compute(reference, (aLong, o) -> indexValue);
        }
    }

    /**
     * Delete an index value with a record reference
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
                references.computeIfPresent(indexValue, new BiFunction<Object, Set<Long>, Set<Long>>() {
                    @Override
                    public Set<Long> apply(Object o, Set<Long> longs)
                    {
                        longs.remove(reference);
                        return longs;
                    }
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
    public Set<Long> findAll(Object indexValue) throws EntityException
    {
        final Set<Long> refs = references.get(indexValue);
        if(refs == null)
            return new HashSet();
        return refs;
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
     * ReBuilds an index by iterating through all the values and re-mapping index values
     *
     * @throws EntityException
     */
    public void rebuild() throws EntityException
    {
        final DiskMap records = (DiskMap)dataFile.getHashMap(indexDescriptor.getEntityDescriptor().getClazz().getCanonicalName());
        final Iterator<Map.Entry> iterator = records.entrySet().iterator();

        // Iterate Through all of the values and re-map the key value for the record id
        Map.Entry entry = null;
        while(iterator.hasNext())
        {
            try {
                entry = iterator.next();
                long recId = records.getRecID(entry.getKey());
                if(recId > 0) {
                    final Object indexValue = AbstractRecordController.getIndexValueFromEntity((IManagedEntity) entry.getValue(), indexDescriptor);
                    if(indexValue != null)
                        save(indexValue, recId, recId);
                }
            }
            // Catch an exception so it may continue the routine
            catch (Exception ignore){}
        }
    }
}
