package com.onyx.map.base;

import com.onyx.exception.AttributeMissingException;
import com.onyx.map.node.BitMapNode;
import com.onyx.map.node.Header;
import com.onyx.map.node.Record;
import com.onyx.map.node.RecordReference;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.store.Store;
import com.onyx.util.AttributeField;
import com.onyx.util.ObjectUtil;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by timothy.osborn on 3/27/15.
 */
public class AbstractCachedBitMap extends AbstractBitMap {

    protected Map<Long, BitMapNode> nodeCache;
    protected Map<Long, Record> recordCache;
    protected Map<Object, Long> keyCache;

    /**
     * Constructor
     *
     * @param fileStore
     * @param header
     */
    public AbstractCachedBitMap(Store fileStore, Header header)
    {
        super(fileStore, header);
        nodeCache = Collections.synchronizedMap(new WeakHashMap());
        recordCache = Collections.synchronizedMap(new CacheMap());
        keyCache = Collections.synchronizedMap(new WeakHashMap());
    }

    /**
     * Inserts a new record
     *
     * @param node
     * @param key
     * @param value
     */
    @Override
    public Record insert(RecordReference parentRecordReference, BitMapNode node, Object key, Object value, int[] hashDigits)
    {
        final Record record = super.insert(parentRecordReference, node, key, value, hashDigits);
        if (parentRecordReference != null)
        {
            Record parentRecord = recordCache.get(parentRecordReference.position);
            if (parentRecord != null)
            {
                parentRecord.reference = parentRecordReference;
            }
            recordCache.put(parentRecordReference.position, parentRecord);
        }
        recordCache.put(record.reference.position, record);
        keyCache.put(key, record.reference.position);

        return record;
    }

    /**
     * Update a record
     *
     * @param node
     * @param parentRecordReference
     * @param recordReference
     * @param key
     * @param value
     */
    @Override
    public Record update(BitMapNode node, RecordReference parentRecordReference, RecordReference recordReference, Object key, Object value, int[] hashDigits)
    {
        recordCache.remove(recordReference.position);

        Record parentRecord = null;

        if (parentRecordReference != null)
        {
            parentRecord = recordCache.remove(parentRecordReference.position);
        }

        final Record record = super.update(node, parentRecordReference, recordReference, key, value, hashDigits);

        if (parentRecord != null)
        {
            parentRecord.reference = parentRecordReference;
            recordCache.put(parentRecord.reference.position, parentRecord);
        }

        recordCache.put(record.reference.position, record);
        keyCache.put(key, record.reference.position);


        return record;
    }

    /**
     * Delete a record
     *
     * @param node
     * @param parentRecordReference
     * @param recordReference
     */
    @Override
    public void delete(BitMapNode node, RecordReference parentRecordReference, RecordReference recordReference, int[] hashDigits, Object key)
    {
        super.delete(node, parentRecordReference, recordReference, hashDigits, key);
        if (parentRecordReference != null)
        {
            Record parentRecord = recordCache.get(parentRecordReference.position);
            if (parentRecord != null)
            {
                parentRecord.reference = parentRecordReference;
            }
            recordCache.put(parentRecordReference.position, parentRecord);
        }
        nodeCache.remove(node.position);
        recordCache.remove(recordReference.position);
        keyCache.remove((Object)key);
    }

    /**
     * Get bitmap node
     *
     * @param position
     * @return
     */
    @Override
    protected BitMapNode getBitmapNode(long position)
    {
        BitMapNode node = nodeCache.get(position);
        if (node == null)
        {
            node = super.getBitmapNode(position);
            nodeCache.put(position, node);
        }
        return node;
    }

    /**
     * Write bitmap node
     *
     * @param position
     * @param node
     */
    @Override
    protected void writeBitmapNode(long position, BitMapNode node)
    {
        nodeCache.put(position, node);
        super.writeBitmapNode(position, node);
    }

    /**
     * This method will only update a bitmap node reference
     */
    @Override
    public void updateBitmapNodeReference(BitMapNode node, int index, long value)
    {
        super.updateBitmapNodeReference(node, index, value);
        nodeCache.put(node.position, node);
    }

    /**
     * Get Key
     *
     * @param reference
     * @return
     */
    @Override
    protected Object getRecordKey(RecordReference reference)
    {

        Record record = recordCache.get(reference.position);
        if(record != null && record.key != null)
        {
            return record.key;
        }
        else
        {
            if(record == null)
            {
                record = new Record();
            }

            record.key = super.getRecordKey(reference);
        }

        return record.key;
    }

    /**
     * Get Record Reference
     *
     * @param position
     * @return
     */
    @Override
    protected RecordReference getRecordReference(long position)
    {
        Record record = recordCache.get(position);
        if (record != null && record.reference != null)
        {
            return record.reference;
        }

        if (record == null)
        {
            record = new Record();
        }

        record.reference = super.getRecordReference(position);

        if (record.reference != null)
        {
            recordCache.put(record.reference.position, record);
        }
        return record.reference;

    }

    /**
     * Get Record Reference
     *
     * @param node
     * @param key
     * @return
     */
    public RecordReference[] getRecordReference(BitMapNode node, Object key, int[] hashDigits)
    {
        final RecordReference[] references = super.getRecordReference(node, key, hashDigits);
        if(references[1] != null)
        {
            keyCache.put(key, references[1].position);
        }

        return references;
    }

    /**
     * Get Value
     *
     * @param reference
     * @return
     */
    @Override
    public Object getRecordValue(RecordReference reference)
    {
        Record record = recordCache.get(reference.position);
        if(record != null && record.value != null)
        {
            return record.value;
        }
        else
        {
            if(record == null)
            {
                record = new Record();
            }

            record.value = super.getRecordValue(reference);
        }

        return record.value;
    }

    private static ObjectUtil reflection = ObjectUtil.getInstance();

    /**
     * Get Map representation of value object
     *
     * @param attribute Attribute name to fetch
     * @param recordId Record reference within storage structure
     *
     * @return Map of key values
     */
    public Object getAttributeWithRecID(String attribute, long recordId)
    {
        final RecordReference reference = this.getRecordReference(recordId);

        // First see if it is cached and get it via reflection
        Record record = recordCache.get(reference.position);
        if(record != null && record.value != null)
        {
            Class clazz = record.value.getClass();
            AttributeField attributeField = null;
            try {
                attributeField = ObjectUtil.getAttributeField(clazz, attribute);
            } catch (AttributeMissingException e) {
                return getAttributeWithRecID(attribute, reference);
            }
            return reflection.getAttribute(attributeField, record.value);
        }

        if(reference != null && reference.position == recordId)
        {
            return getAttributeWithRecID(attribute, reference);
        }
        return null;
    }

}

