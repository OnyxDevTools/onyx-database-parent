package com.onyx.structure.base;

import com.onyx.exception.AttributeMissingException;

import com.onyx.exception.AttributeTypeMismatchException;
import com.onyx.structure.node.BitMapNode;
import com.onyx.structure.node.Header;
import com.onyx.structure.node.Record;
import com.onyx.structure.node.RecordReference;
import com.onyx.structure.store.Store;

import com.onyx.util.OffsetField;
import com.onyx.util.ReflectionUtil;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;


/**
 Created by timothy.osborn on 3/27/15.
 */
public class AbstractCachedBitMap extends AbstractBitMap
{
    protected Map<Long, BitMapNode> nodeCache;
    protected Map<Long, Record> recordCache;
    protected Map<Object, Long> keyCache;

    /**
     * Constructor.
     *
     * @param  fileStore
     * @param  header
     */
    public AbstractCachedBitMap(final Store fileStore, final Header header)
    {
        super(fileStore, header);
        nodeCache = Collections.synchronizedMap(new WeakHashMap());

        if(supportsJavaSystemNotifications())
        {
            recordCache = Collections.synchronizedMap(new CacheMap());
        }
        else
        {
            recordCache = Collections.synchronizedMap(new WeakHashMap<>());
        }

        keyCache = Collections.synchronizedMap(new WeakHashMap());
    }

    /**
     * Inserts a new record.
     *
     * @param   parentRecordReference
     * @param   node
     * @param   key
     * @param   value
     * @param   hashDigits
     *
     * @return  inserts a new record.
     */
    @Override public Record insert(final RecordReference parentRecordReference, final BitMapNode node, final Object key, final Object value,
        final int[] hashDigits)
    {
        final Record record = super.insert(parentRecordReference, node, key, value, hashDigits);

        if (parentRecordReference != null)
        {
            final Record parentRecord = recordCache.get(parentRecordReference.position);

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
     * Update a record.
     *
     * @param   node
     * @param   parentRecordReference
     * @param   recordReference
     * @param   key
     * @param   value
     * @param   hashDigits
     *
     * @return  update a record.
     */
    @Override public Record update(final BitMapNode node, final RecordReference parentRecordReference,
        final RecordReference recordReference, final Object key, final Object value, final int[] hashDigits)
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
     * Delete a record.
     *
     * @param  node
     * @param  parentRecordReference
     * @param  recordReference
     * @param  hashDigits
     * @param  key
     */
    @Override public void delete(final BitMapNode node, final RecordReference parentRecordReference, final RecordReference recordReference,
        final int[] hashDigits, final Object key)
    {
        super.delete(node, parentRecordReference, recordReference, hashDigits, key);

        if (parentRecordReference != null)
        {
            final Record parentRecord = recordCache.get(parentRecordReference.position);

            if (parentRecord != null)
            {
                parentRecord.reference = parentRecordReference;
            }

            recordCache.put(parentRecordReference.position, parentRecord);
        }

        nodeCache.remove(node.position);
        recordCache.remove(recordReference.position);
        keyCache.remove((Object) key);
    }

    /**
     * Get bitmap node.
     *
     * @param   position
     *
     * @return  get bitmap node.
     */
    @Override protected BitMapNode getBitmapNode(final long position)
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
     * Write bitmap node.
     *
     * @param  position
     * @param  node
     */
    @Override protected void writeBitmapNode(final long position, final BitMapNode node)
    {
        nodeCache.put(position, node);
        super.writeBitmapNode(position, node);
    }

    /**
     * This method will only update a bitmap node reference.
     *
     * @param  node
     * @param  index
     * @param  value
     */
    @Override public void updateBitmapNodeReference(final BitMapNode node, final int index, final long value)
    {
        super.updateBitmapNodeReference(node, index, value);
        nodeCache.put(node.position, node);
    }

    /**
     * Get Key.
     *
     * @param   reference
     *
     * @return  get Key.
     */
    @Override protected Object getRecordKey(final RecordReference reference)
    {
        Record record = recordCache.get(reference.position);

        if ((record != null) && (record.key != null))
        {
            return record.key;
        }
        else
        {

            if (record == null)
            {
                record = new Record();
            }

            record.key = super.getRecordKey(reference);
        }

        return record.key;
    }

    /**
     * Get Record Reference.
     *
     * @param   position
     *
     * @return  get Record Reference.
     */
    @Override protected RecordReference getRecordReference(final long position)
    {
        Record record = recordCache.get(position);

        if ((record != null) && (record.reference != null))
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
     * Get Record Reference.
     *
     * @param   node
     * @param   key
     * @param   hashDigits
     *
     * @return  get Record Reference.
     */
    @Override public RecordReference[] getRecordReference(final BitMapNode node, final Object key, final int[] hashDigits)
    {
        final RecordReference[] references = super.getRecordReference(node, key, hashDigits);

        if (references[1] != null)
        {
            keyCache.put(key, references[1].position);
        }

        return references;
    }

    /**
     * Get Value.
     *
     * @param   reference
     *
     * @return  get Value.
     */
    @Override public Object getRecordValue(final RecordReference reference)
    {
        Record record = recordCache.get(reference.position);

        if ((record != null) && (record.value != null))
        {
            return record.value;
        }
        else
        {

            if (record == null)
            {
                record = new Record();
            }

            record.value = super.getRecordValue(reference);
        }

        return record.value;
    }

    /**
     * Get Map representation of key object.
     *
     * @param   attribute  Attribute name to fetch
     * @param   recordId   Record reference within storage structure
     *
     * @return  Map of key values
     */
    public Object getAttributeWithRecID(final String attribute, final long recordId) throws AttributeTypeMismatchException {
        final RecordReference reference = this.getRecordReference(recordId);

        // First see if it is cached and get it via reflection
        final Record record = recordCache.get(reference.position);

        if ((record != null) && (record.value != null))
        {
            final Class clazz = record.value.getClass();
            OffsetField attributeField = null;

            try
            {
                attributeField = ReflectionUtil.getOffsetField(clazz, attribute);
            }
            catch (AttributeMissingException e)
            {
                return getAttributeWithRecID(attribute, reference);
            }

            return ReflectionUtil.getAny(record.value, attributeField);
        }

        if ((reference != null) && (reference.position == recordId))
        {
            return getAttributeWithRecID(attribute, reference);
        }

        return null;
    }

    /**
     * The purpose of this method is to detect whether the javax management is available.  This will throw an exception if
     * the client is on an android device.  If that is the case, we will use alternative caching mechanism
     *
     * @return whether javax.management is supported
     */
    protected static boolean supportsJavaSystemNotifications()
    {
        try {
            Class.forName("javax.management.Notification");
            return true;
        } catch(ClassNotFoundException e) {
            return false;
        }
    }
}
