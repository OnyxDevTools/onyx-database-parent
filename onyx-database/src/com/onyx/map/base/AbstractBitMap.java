package com.onyx.map.base;

import com.onyx.map.node.BitMapNode;
import com.onyx.map.node.Header;
import com.onyx.map.node.Record;
import com.onyx.map.node.RecordReference;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.store.Store;
import com.onyx.util.CompareUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by timothy.osborn on 3/25/15.
 */
public class AbstractBitMap
{

    protected Header header = null;

    // Storage mechanism for the hashmap
    public Store fileStore;

    protected Map<Object, Long> keyCache = new WeakHashMap<>();
    /**
     * Constructor
     *
     * @param fileStore
     */
    public AbstractBitMap(Store fileStore, Header header)
    {
        this.fileStore = fileStore;
        this.header = header;
    }

    /**
     * Iterates through the bitmap graph
     * <p/>
     * It will create the bitmap graph if specified for insert
     *
     * @param hash
     * @param forInsert
     * @return
     */
    public BitMapNode seek(int hash, boolean forInsert, final int[] hashDigits)
    {

        BitMapNode node = null;

        if (this.header.firstNode > 0)
        {
            node = (BitMapNode) getBitmapNode(this.header.firstNode); // Get Root node
        } else
        {
            // No default node, lets create one // It must mean we are inserting
            node = new BitMapNode();
            node.position = fileStore.allocate(BitMapNode.BITMAP_NODE_SIZE);
            header.firstNode = node.position;

            writeBitmapNode(node.position, node);
            updateHeaderFirstNode(header, node.position);
        }

        // There is no default node return -1 because it was not found
        if (!forInsert && node == null)
            return null;

        BitMapNode previousNode = node;
        long nodePosition = 0;
        int hashDigit = 0;

        // Break down the nodes and iterate through them.  We should be left with the remaining node which should point us to the record
        for (int level = 0; level < 10; level++)
        {

            hashDigit = hashDigits[level];

            nodePosition = previousNode.next[hashDigit];

            if (nodePosition == 0 && forInsert == true)
            {
                node = new BitMapNode();
                node.position = fileStore.allocate(BitMapNode.BITMAP_NODE_SIZE);

                writeBitmapNode(node.position, node);
                updateBitmapNodeReference(previousNode, hashDigit, node.position);

                previousNode = node;
                node = new BitMapNode();
            }

            // Not found because it is not in the
            else if (nodePosition == 0)
                return null;
            else
                previousNode = getBitmapNode(nodePosition);
        }

        return previousNode; // This should contain the drones you are looking for (Star Wars reference) // This contains the key to the linked list
    }

    /**
     * Inserts a new record
     *
     * @param node
     * @param key
     * @param value
     */
    public Record insert(RecordReference parentRecordReference, BitMapNode node, Object key, Object value, int[] hashDigits)
    {

        try
        {

            // Create the record
            final Record record = new Record();
            record.key = key;
            record.value = value;

            final ObjectBuffer buffer = new ObjectBuffer(fileStore.getSerializers());
            record.writeObject(buffer); // Write the record to the buffer

            final RecordReference reference = new RecordReference();
            reference.recordSize = record.getSize();
            reference.keySize = record.keySize;
            reference.serializerId = buffer.getSerializerId(value); // Get the serializer id.  Note: This only applies to Managed Entities in order to version

            // Update the record position as well as the BitMapNode to show the location
            final ObjectBuffer referenceBuffer = new ObjectBuffer(fileStore.getSerializers());
            long recordPosition = fileStore.allocate((reference.recordSize + RecordReference.RECORD_REFERENCE_LIST_SIZE));
            reference.writeObject(referenceBuffer);

            final ObjectBuffer totalBuffer = new ObjectBuffer(fileStore.getSerializers());
            totalBuffer.write(referenceBuffer);
            totalBuffer.write(buffer);

            // Write to disk
            fileStore.write(totalBuffer, recordPosition);

            // Update the parent link
            if (parentRecordReference != null)
            {
                parentRecordReference.next = recordPosition;
                updateReferenceNext(parentRecordReference);
            } else
            {
                // Update the record position as well as the BitMapNode to show the location
                updateBitmapNodeReference(node, hashDigits[BitMapNode.RECORD_REFERENCE_INDEX], recordPosition);
            }

            header.recordCount.incrementAndGet();
            updateHeaderRecordCount();

            reference.position = recordPosition;
            record.reference = reference;
            return record;
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        return null;

    }

    /**
     * This method will only update the record count rather than the entire header
     */
    public void updateHeaderRecordCount()
    {
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(header.recordCount.get());
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
        fileStore.write(objectBuffer, header.position + Integer.BYTES + (4 * Long.BYTES));
    }

    /**
     * This method will only update the record count rather than the entire header
     */
    public void updateReferenceNext(RecordReference reference)
    {
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(reference.next);
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
        fileStore.write(objectBuffer, reference.position + (Integer.BYTES * 2));
    }

    /**
     * Only update the first position for a header
     *
     * @param header
     * @param firstNode
     */
    public void updateHeaderFirstNode(Header header, long firstNode)
    {
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(firstNode);
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
        fileStore.write(objectBuffer, header.position);
    }

    /**
     * This method will only update a bitmap node reference
     */
    public void updateBitmapNodeReference(BitMapNode node, int index, long value)
    {
        node.next[index] = value;
        final ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, fileStore.getSerializers());
        fileStore.write(objectBuffer, node.position + (Long.BYTES * index) + Long.BYTES);
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
        // Convert the hash number to digits with leading 0s
        int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

        long position = node.next[hashDigit];

        RecordReference reference = null;
        RecordReference parent = null;

        Object compareKey = null;

        while (position > 0)
        {
            reference = getRecordReference(position);

            if(reference.keySize != 0) {

                compareKey = (Object) getRecordKey(reference);

                if(CompareUtil.compare(key, compareKey, false))
                {
                    RecordReference[] references = new RecordReference[2];
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
        final RecordReference[] references = new RecordReference[2];
        references[0] = parent;
        return references;
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
    public Record update(BitMapNode node, RecordReference parentRecordReference, RecordReference recordReference, Object key, Object value, int[] hashDigits)
    {

        int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

        try
        {
            final Record record = new Record();
            record.key = key;
            record.value = value;

            final ObjectBuffer recordBuffer = new ObjectBuffer(fileStore.getSerializers());
            record.writeObject(recordBuffer); // Write the record to the buffer

            long recordPosition = recordReference.position;

            // The record has grown, we need to move it to a new spot that is big enough
            if (recordReference.recordSize < record.getSize())
            {
                fileStore.deallocate(recordReference.position, (recordReference.recordSize + RecordReference.RECORD_REFERENCE_LIST_SIZE));
                recordPosition = fileStore.allocate((record.getSize() + RecordReference.RECORD_REFERENCE_LIST_SIZE));
            }

            int recordSize = (record.getSize() + RecordReference.RECORD_REFERENCE_LIST_SIZE);
            recordReference.recordSize = record.getSize();
            recordReference.keySize = record.keySize;
            recordReference.position = recordPosition;
            recordReference.serializerId = recordBuffer.getSerializerId(value);

            final ObjectBuffer referenceBuffer = new ObjectBuffer(fileStore.getSerializers());
            recordReference.writeObject(referenceBuffer);

            final ObjectBuffer totalBuffer = new ObjectBuffer(fileStore.getSerializers());
            totalBuffer.write(referenceBuffer);
            totalBuffer.write(recordBuffer);

            // Write to disk
            fileStore.write(totalBuffer, recordPosition);

            // Update the parent
            // If it is a link in a linked list, update the parent link
            if (parentRecordReference != null && parentRecordReference.next != recordPosition)
            {
                parentRecordReference.next = recordPosition;
                updateReferenceNext(parentRecordReference);
            }
            // Otherwise update the bitmap node
            else if (parentRecordReference == null)
            {
                // Update the record position as well as the BitMapNode to show the location
                updateBitmapNodeReference(node, hashDigit, recordPosition);
            }

            record.reference = recordReference;
            return record;

        } catch (IOException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Delete a record
     *
     * @param node
     * @param parentRecordReference
     * @param recordReference
     */
    public void delete(BitMapNode node, RecordReference parentRecordReference, RecordReference recordReference, int[] hashDigits, Object key)
    {
        // Convert the hash number to digits with leading 0s

        int hashDigit = hashDigits[BitMapNode.RECORD_REFERENCE_INDEX];

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

        fileStore.deallocate(recordReference.position, (recordReference.recordSize + RecordReference.RECORD_REFERENCE_LIST_SIZE));
    }

    /**
     * Get bitmap node
     *
     * @param position
     * @return
     */
    protected BitMapNode getBitmapNode(long position)
    {
        return (BitMapNode)fileStore.read(position, BitMapNode.BITMAP_NODE_SIZE, BitMapNode.class);
    }

    /**
     * Write bitmap node
     *
     * @param position
     * @param node
     */
    protected void writeBitmapNode(long position, BitMapNode node)
    {
        fileStore.write(node, node.position);
    }

    /**
     * Get Key
     *
     * @param reference
     * @return
     */
    protected Object getRecordKey(RecordReference reference)
    {
        return fileStore.read(reference.position + RecordReference.RECORD_REFERENCE_LIST_SIZE, reference.keySize, Object.class);
    }

    /**
     * Get Record Reference
     *
     * @param position
     * @return
     */
    protected RecordReference getRecordReference(long position)
    {
        final RecordReference reference = (RecordReference) fileStore.read(position, RecordReference.RECORD_REFERENCE_LIST_SIZE, RecordReference.class);
        reference.position = position; // This is needed.  We do not persist the position to save time so we need to get it here
        return reference;
    }

    /**
     * Get Value
     *
     * @param reference
     * @return
     */
    public Object getRecordValue(RecordReference reference)
    {
        return fileStore.read(reference.position + reference.keySize + RecordReference.RECORD_REFERENCE_LIST_SIZE, (reference.recordSize - reference.keySize), Object.class, reference.serializerId);
    }


    /**
     * The purpose of this hash is to generate a fancier hash so that in instances for a long or int, it will not generate the value of those
     * rather it will get a searchable value within a BST without being lop sided.
     *
     * @param key
     * @return
     */
    protected int hash(final Object key)
    {
        return key.hashCode();
    }

    /**
     * Helper method for getting the digits of a hash number.  This relies on it being a 10 digit number max
     *
     * @param hash
     * @return
     */
    protected int[] getHashDigits(int hash)
    {
        int[] digits = new int[11];

        if (hash < 0)
        {
            digits[0] = 0;
            hash = hash * -1;
        }
        else
            digits[0] = 1;

        for (int i = 10; i >= 1; i--)
        {
            digits[i] = hash % 10;
            hash /= 10;
        }
        return digits;
    }
}
