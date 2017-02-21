package com.onyx.index.impl;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.index.IndexController;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.record.AbstractRecordController;
import com.onyx.record.RecordController;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.MapBuilder;
import com.onyx.diskmap.base.DiskMultiMatrixHashMap;
import com.onyx.diskmap.node.Header;

import java.util.*;

/**
 * Created by timothy.osborn on 1/29/15.
 *
 * Controls actions of an index
 */
@SuppressWarnings("unchecked")
public class IndexControllerImpl implements IndexController {

    protected String contextId;

    protected Map<Object, Header> references = null; // Stores the references for an index key
    private Map<Long, Object> indexValues = null;
    protected RecordController recordController = null;
    protected IndexDescriptor indexDescriptor = null;
    protected EntityDescriptor descriptor;

    private static final int INDEX_VALUE_MAP_LOAD_FACTOR = 1;

    public IndexDescriptor getIndexDescriptor()
    {
        return indexDescriptor;
    }

    /**
     * Constructor with entity descriptor and index descriptor
     *
     * @param descriptor Entity Descriptor
     * @param indexDescriptor Index Descriptor
     */
    public IndexControllerImpl(EntityDescriptor descriptor, IndexDescriptor indexDescriptor, SchemaContext context) throws EntityException
    {
        this.contextId = context.getContextId();

        this.indexDescriptor = indexDescriptor;
        this.recordController = context.getRecordController(descriptor);
        this.descriptor = descriptor;
        final MapBuilder dataFile = context.getDataFile(descriptor);

        references = dataFile.getHashMap(descriptor.getClazz().getName() + indexDescriptor.getName(), indexDescriptor.getLoadFactor());
        indexValues = dataFile.getHashMap(descriptor.getClazz().getName() + indexDescriptor.getName() + "indexValues", indexDescriptor.getLoadFactor());
    }

    /**
     * Save an index key with the record reference
     *
     * @param indexValue Index value to save
     * @param oldReference Old entity reference for the index
     * @param reference New entity reference for the index
     */
    public void save(Object indexValue, long oldReference, long reference) throws EntityException
    {
        // Delete the old index key
        if(oldReference > 0)
        {
            delete(oldReference);
        }

        if(indexValue != null) {
            final MapBuilder dataFile = getContext().getDataFile(descriptor);

            references.compute(indexValue, (o, header) -> {
                if(header == null) {
                    header = dataFile.newMapHeader();
                }
                DiskMap indexes = dataFile.newHashMap(header, INDEX_VALUE_MAP_LOAD_FACTOR);
                indexes.put(reference, null);
                header.firstNode = indexes.getReference().firstNode;
                header.position = indexes.getReference().position;
                header.recordCount.set(indexes.getReference().recordCount.get());
                indexes = null;
                return header;
            });
            indexValues.compute(reference, (aLong, o) -> indexValue);
        }
    }

    /**
     * Delete an index key with a record reference
     *
     * @param reference Entity reference
     */
    public void delete(long reference) throws EntityException
    {
        if(reference > 0)
        {
            Object indexValue = indexValues.remove(reference);
            if (indexValue != null)
            {
                final MapBuilder dataFile = getContext().getDataFile(descriptor);

                references.computeIfPresent(indexValue, (o, header) -> {
                    DiskMap indexes = dataFile.newHashMap(header, INDEX_VALUE_MAP_LOAD_FACTOR);
                    indexes.remove(reference);
                    header.firstNode = indexes.getReference().firstNode;
                    header.position = indexes.getReference().position;
                    header.recordCount.set(indexes.getReference().recordCount.get());
                    indexes = null;
                    return header;
                });
            }
        }
    }

    /**
     * Find all index references
     *
     * @param indexValue Index value to find values for
     * @return References matching that index value
     */
    public Map findAll(Object indexValue) throws EntityException
    {
        final Header header = references.get(indexValue);
        if(header == null)
            return new HashMap();
        final MapBuilder dataFile = getContext().getDataFile(descriptor);

        return dataFile.newHashMap(header, INDEX_VALUE_MAP_LOAD_FACTOR);
    }

    /**
     * Find all index references
     *
     * @return All index references
     */
    public Set<Object> findAllValues() throws EntityException
    {
        return references.keySet();
    }


    /**
     * Find all the references above and perhaps equal to the key parameter
     *
     * This has one prerequisite.  You must be using a DiskMultiMatrixHashMap as the storage mechanism.  Otherwise it will not be
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
        final Set<Long> diskReferences = ((DiskMultiMatrixHashMap)references).above(indexValue, includeValue);

        final MapBuilder dataFile = getContext().getDataFile(descriptor);

        diskReferences.forEach(aLong -> {
            Header header = (Header)((DiskMultiMatrixHashMap) references).getWithRecID(aLong);
            DiskMap map = (DiskMap)dataFile.getHashMap(header, INDEX_VALUE_MAP_LOAD_FACTOR);
            map.keySet().forEach(o -> allReferences.add((long)o));
        });

        return allReferences;
    }

    /**
     * Find all the references blow and perhaps equal to the key parameter
     *
     * This has one prerequisite.  You must be using a DiskMultiMatrixHashMap as the storage mechanism.  Otherwise it will not be
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
        final Set<Long> diskReferences = ((DiskMultiMatrixHashMap)references).below(indexValue, includeValue);
        final MapBuilder dataFile = getContext().getDataFile(descriptor);
        diskReferences.forEach(aLong -> {
            Header header = (Header)((DiskMultiMatrixHashMap) references).getWithRecID(aLong);
            DiskMap map = (DiskMap)dataFile.getHashMap(header, INDEX_VALUE_MAP_LOAD_FACTOR);
            map.keySet().forEach(o -> allReferences.add((long)o));
        });

        return allReferences;
    }

    /**
     * ReBuilds an index by iterating through all the values and re-mapping index values
     *
     */
    public void rebuild() throws EntityException
    {
        final MapBuilder dataFile = getContext().getDataFile(descriptor);

        final DiskMap records = (DiskMap)dataFile.getHashMap(indexDescriptor.getEntityDescriptor().getClazz().getName(), indexDescriptor.getLoadFactor());
            final Iterator<Map.Entry> iterator = records.entrySet().iterator();

            // Iterate Through all of the values and re-structure the key key for the record id
            Map.Entry entry;
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

    protected SchemaContext getContext()
    {
        return DefaultSchemaContext.registeredSchemaContexts.get(contextId);
    }
}
