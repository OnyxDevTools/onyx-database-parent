package com.onyx.structure.base;

import com.onyx.structure.node.*;
import com.onyx.structure.store.Store;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by tosborn1 on 9/9/16.
 */
public class AbstractLongCachedBitMap extends AbstractLongBitMap {


    protected Map<Long, BitMapNode> nodeCache;
    protected Map<Long, LongRecordReference> recordCache;
    protected LevelReadWriteLock readWriteLock = new DefaultLevelReadWriteLock();

    /**
     * Constructor.
     *
     * @param fileStore
     * @param header
     */
    public AbstractLongCachedBitMap(Store fileStore, Header header) {
        super(fileStore, header);
        nodeCache = Collections.synchronizedMap(new WeakHashMap());
        recordCache = Collections.synchronizedMap(new WeakHashMap());
    }

    public LongRecordReference insert(LongRecordReference parentRecordReference, BitMapNode node, long value, int[] hashDigits)
    {
        LongRecordReference recordReference = super.insert(parentRecordReference, node, value, hashDigits);
        recordCache.put(recordReference.position, recordReference);
        return recordReference;
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
        recordCache.remove(recordReference.position, recordReference);
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
        return nodeCache.compute(position, (aLong, bitMapNode) -> AbstractLongCachedBitMap.super.getBitmapNode(position));
    }

    /**
     * Write bitmap node.
     *
     * @param  position
     * @param  node
     */
    @Override protected void writeBitmapNode(final long position, final BitMapNode node)
    {
        super.writeBitmapNode(position, node);
        nodeCache.put(position, node);
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

    public LongRecordReference getLongRecordReference(long position)
    {
        return recordCache.compute(position, (aLong, longRecordReference) -> AbstractLongCachedBitMap.super.getLongRecordReference(position));
    }
}
