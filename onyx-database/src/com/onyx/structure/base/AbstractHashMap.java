package com.onyx.structure.base;

import com.onyx.buffer.BufferStream;
import com.onyx.structure.node.Header;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.store.Store;

import java.nio.ByteBuffer;

/**
 * Created by tosborn1 on 2/15/17.
 */
public abstract class AbstractHashMap<K, V> extends AbstractDiskMap<K,V> {

    int size;
    volatile int count;
    int referenceOffset;
    int listReferenceOffset;

    AbstractHashMap(Store fileStore, Header header, boolean headless, int loadFactor) {
        super(fileStore, header, headless);
        this.loadFactor = (byte) loadFactor;
        int allocation = 1;

        for (int i = 0; i < loadFactor; i++)
            allocation *= 10;

        this.size = allocation - 1;

        int numberOfReferenceBytes = allocation * Long.BYTES;
        int numberOfListReferenceBytes = allocation * Integer.BYTES;
        int countBytes = Integer.BYTES;

        if (header.firstNode == 0) {
            updateHeaderFirstNode(header, fileStore.allocate(numberOfReferenceBytes + numberOfListReferenceBytes + countBytes));
            this.count = 0;
        } else
        {
            long position = header.firstNode;
            final ObjectBuffer objectBuffer = fileStore.read(position, Integer.BYTES);
            count = objectBuffer.readInt();
        }

        referenceOffset = countBytes;
        listReferenceOffset = referenceOffset + numberOfReferenceBytes;
    }

    long insertReference(int hash, long reference)
    {
        ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(reference);
        fileStore.write(new ObjectBuffer(buffer, null), header.firstNode + referenceOffset + (hash*8));

        // Add list reference for iterating
        buffer.clear();
        buffer.putInt(hash);
        fileStore.write(new ObjectBuffer(buffer, null), header.firstNode + listReferenceOffset + (count * Integer.BYTES));

        // Update count
        count++;
        buffer.clear();
        buffer.putInt(count);
        fileStore.write(new ObjectBuffer(buffer, null), header.firstNode);

//        BufferStream.recycle(buffer);
        return reference;
    }

    long updateReference(int hash, long reference)
    {
        long position = (hash*8) + referenceOffset + header.firstNode;
        ByteBuffer buffer = ObjectBuffer.allocate(Long.BYTES);
        buffer.putLong(reference);
        fileStore.write(new ObjectBuffer(buffer, null), position);
//        BufferStream.recycle(buffer);
        return reference;
    }

    int getMapIdentifier(int index)
    {
        long position = header.firstNode + listReferenceOffset + (index * Integer.BYTES);
        final ObjectBuffer objectBuffer = fileStore.read(position, Integer.BYTES);
        return objectBuffer.readInt();
    }

    long getReference(int hash)
    {
        long position = (hash*8) + referenceOffset + header.firstNode;
        final ObjectBuffer objectBuffer = fileStore.read(position, Long.BYTES);
        return objectBuffer.readLong();
    }

}
