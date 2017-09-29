package com.onyx.diskmap.base;

import com.onyx.buffer.BufferPool;
import com.onyx.buffer.BufferStream;
import com.onyx.diskmap.DiskMap;
import com.onyx.diskmap.node.HashMatrixNode;
import com.onyx.diskmap.node.Header;
import com.onyx.diskmap.store.Store;
import com.onyx.util.map.AbstractCompatMap;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Created by tosborn1 on 1/11/17.
 *
 * This class was intended to abstract some of the common actions and values that all disk data structures use.
 *
 * I kept running into various different areas of teh application I would have to update if I had to modify say a
 * header class.  I would then have to make sure I modified several different implementations.  No longer!!!
 *
 * @since 1.2.0
 *
 */
public abstract class AbstractDiskMap<K, V> extends AbstractCompatMap<K,V> implements DiskMap<K, V> {

    protected AbstractDiskMap()
    {

    }
    protected Store fileStore; // Underlying storage mechanism
    protected Header header = null;
    protected boolean detached = false;
    protected byte loadFactor = HashMatrixNode.DEFAULT_BITMAP_ITERATIONS;

    protected AbstractDiskMap(Store fileStore, Header header, boolean headless) {
        this.fileStore = fileStore;
        // Clone the header so that we do not have a cross reference
        // This was preventing WeakHashMaps from ejecting the entire map value
        this.header = new Header();
        this.header.firstNode = header.firstNode;
        this.header.position = header.position;
        this.header.recordCount = new AtomicLong(header.recordCount.get());
        this.detached = headless;
    }

    /**
     * This method will only update the record count rather than the entire header
     */
    @SuppressWarnings("WeakerAccess")
    void updateHeaderRecordCount() {
        final ByteBuffer buffer = BufferPool.INSTANCE.allocateAndLimit(Long.BYTES);
        try {
            buffer.putLong(header.recordCount.get());
            buffer.flip();
            fileStore.write(buffer, header.position + Long.BYTES);
        }
        finally {
            BufferPool.INSTANCE.recycle(buffer);
        }
    }

    /**
     * Only update the first position for a header
     *
     * @param header    Data structure header
     * @param firstNode First Node location
     */
    @SuppressWarnings("unused")
    protected void updateHeaderFirstNode(Header header, long firstNode) {
        final ByteBuffer buffer = BufferPool.INSTANCE.allocateAndLimit(Long.BYTES);
        try {
            buffer.putLong(firstNode);
            buffer.flip();
            fileStore.write(buffer, header.position);
        }
        finally {
            BufferPool.INSTANCE.recycle(buffer);
        }
    }

    /**
     * Default hashing algorithm.
     *
     * @param key Key to get the hash of
     * @return The hash value of that key.
     */
    protected int hash(final Object key) {
        if (key == null)
            return 0;
        return key.hashCode();
    }

    /**
     * Whether or not the map is empty.
     *
     * @return True if the size is 0
     * @since 1.2.0
     */
    public boolean isEmpty() {
        return longSize() == 0L;
    }

    /**
     * The size in a long value
     *
     * @return The size of the map as a long
     * @since 1.2.0
     */
    @SuppressWarnings("WeakerAccess")
    public long longSize() {
        return header.recordCount.get();
    }

    /**
     * Increment the size of the map
     *
     * @since 1.2.0
     */
    protected void incrementSize() {
        header.recordCount.addAndGet(1L);
        updateHeaderRecordCount();
    }

    /**
     * Decrement the size of the map
     *
     * @since 1.2.0
     */
    protected void decrementSize() {
        header.recordCount.decrementAndGet();
        updateHeaderRecordCount();
    }

    /**
     * Getter for file store
     * @return the file store that was assigned to this data structure
     * @since 1.0.1
     */
    @Override
    public Store getFileStore() {
        return fileStore;
    }

    /**
     * Getter for the self reference within the store
     * @return Header that was assigned to this data structure
     * @since 1.0.0
     */
    @Override
    public Header getReference() {
        return header;
    }

    /**
     * This indicates how many iterations of hash tables
     * we need to iterate through.  The higher the number the more scalable the
     * index key is.  The lower, means we have less HashMatrixNode(s) we have to create
     * thus saving disk space.  This is going to be for future use.  Still to come is a backup
     * index such as a BST if the load factor is set too small.  Currently the logic relies on a
     * linked list if there are hashCode collisions.  This should be anything other than that.
     *
     *
     * @since 1.1.1 This was not actually given relevance until v1.2.0. V1.2.0 makes this applicable when determineing its
     * scale and speed for various data sets.
     *
     * @return Load Factor. A key from 5-10.  5 is for minimum data sets and 10 is for fully scalable data sets.
     */
    @SuppressWarnings("unused")
    public int getLoadFactor()
    {
        return loadFactor;
    }

    /**
     * Helper method for getting the digits of a hash number.  This relies on it being a 10 digit number max
     *
     * @param hash Hash value
     * @return the hash value in format of an array of single digits
     */
    @SuppressWarnings("WeakerAccess")
    protected int[] getHashDigits(int hash)
    {
        hash = Math.abs(hash);
        int[] digits = new int[loadFactor];
        if(hash == 0)
            return digits;

        for (int i = loadFactor -1; i >= 0; i--)
        {
            digits[i] = hash % 10;
            hash /= 10;
        }
        return digits;
    }

}
