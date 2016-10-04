package com.onyx.structure.base;

import com.onyx.structure.node.*;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.store.Store;

import java.nio.ByteBuffer;


/**
 * Created by tosborn1 on 9/7/16.
 */
public abstract class AbstractLongBitMap extends AbstractBitMap {

    /**
     * Constructor.
     *
     * @param fileStore
     * @param header
     */
    public AbstractLongBitMap(Store fileStore, Header header) {
        super(fileStore, header);
    }

    public LongRecordReference insert(LongRecordReference parentRecordReference, BitMapNode node, long value, int[] hashDigits)
    {
        final LongRecordReference recordReference = new LongRecordReference();
        recordReference.position = fileStore.allocate(Long.BYTES * 3);
        recordReference.value = value;
        recordReference.next = -1;

        fileStore.write(recordReference, recordReference.position);

        // Update the parent link
        if (parentRecordReference != null)
        {
            parentRecordReference.next = recordReference.position;
            updateReferenceNext(parentRecordReference);
        } else
        {
            // Update the record position as well as the BitMapNode to show the location
            updateBitmapNodeReference(node, hashDigits[BITMAP_ITERATIONS], recordReference.position);
        }

        header.recordCount.incrementAndGet();
        updateHeaderRecordCount();
        return recordReference;
    }

    /**
     * Delete a record
     *
     * @param node
     * @param parentRecordReference
     * @param recordReference
     */
    public void delete(BitMapNode node, LongRecordReference parentRecordReference, LongRecordReference recordReference, int[] hashDigits, long value)
    {
        // Convert the hash number to digits with leading 0s

        int hashDigit = hashDigits[BITMAP_ITERATIONS];

        if (node.next[hashDigit] == recordReference.position)
        {
            node.next[hashDigit] = recordReference.next;
            updateBitmapNodeReference(node, hashDigit, recordReference.next);
        } else if (parentRecordReference != null)
        {
            parentRecordReference.next = recordReference.next;
            updateReferenceNext(parentRecordReference);
        }

        header.recordCount.decrementAndGet();
        updateHeaderRecordCount();
    }

    /**
     * This method will only update the record count rather than the entire header
     */
    public void updateReferenceNext(LongRecordReference reference)
    {
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(reference.next);
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
        fileStore.write(objectBuffer, reference.position);
    }

    public LongRecordReference getLongRecordReference(long position)
    {
        final LongRecordReference reference = (LongRecordReference) fileStore.read(position, Long.BYTES * 3, LongRecordReference.class);
        reference.position = position; // This is needed.  We do not persist the position to save time so we need to get it here
        return reference;
    }


    public LongRecordReference[] getLongRecordReferences(BitMapNode node, long value, int[] hashDigits)
    {
        // Convert the hash number to digits with leading 0s
        int hashDigit = hashDigits[BITMAP_ITERATIONS];

        long position = node.next[hashDigit];

        LongRecordReference reference = null;
        LongRecordReference parent = null;

        long compareValue;

        while (position > 0)
        {
            reference = getLongRecordReference(position);

            if(reference.position > 0) {

                compareValue = reference.value;

                if(compareValue == value)
                {
                    LongRecordReference[] references = new LongRecordReference[2];
                    references[0] = parent;
                    references[1] = reference;
                    return references;
                }
            }

            // Keep track of the parent
            parent = reference;
            position = reference.next;
        }

        // We still want to return the parent
        final LongRecordReference[] references = new LongRecordReference[2];
        references[0] = parent;
        return references;
    }

    /**
     * This method will only update the record count rather than the entire header
     */
    public void updateHeaderRecordCount()
    {
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(header.recordCount.get());
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
        fileStore.write(objectBuffer, header.position + Long.BYTES * 2);
    }
}
