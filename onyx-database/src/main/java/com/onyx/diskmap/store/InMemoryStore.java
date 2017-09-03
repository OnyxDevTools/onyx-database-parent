package com.onyx.diskmap.store;

import com.onyx.buffer.BufferStream;
import com.onyx.util.map.SynchronizedMap;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.diskmap.serializer.ObjectBuffer;

import java.nio.ByteBuffer;

/**
 * Created by tosborn on 3/27/15.
 *
 * Rather than writing to a file, this writes to memory.
 */
public class InMemoryStore extends MemoryMappedStore implements Store {

    /**
     * Constructor open file
     *
     */
    public InMemoryStore(SchemaContext context, String storeId) {
        this.contextId = context.getContextId();
        open(storeId);
        this.setSize();
    }

    /**
     * Open the data file
     *
     * @param filePath  Ignored.  There is no file to open.  Should be blank
     * @return Always true
     */
    public synchronized boolean open(String filePath) {

        this.filePath = filePath;
        slices = new SynchronizedMap<>();

        // Lets open the memory mapped files in 2Gig increments since on 32 bit machines the max is I think 2G.  Also buffers are limited by
        // using an int for position.  We are gonna bust that.
        ByteBuffer buffer = ObjectBuffer.allocate(SLICE_SIZE);
        slices.put(0, new FileSlice(buffer));

        return true;
    }

    /**
     * Get the associated buffer to the position of the file.  So if the position is 2G + it will get the prop
     * er "slice" of the file
     *
     * @param position The position within the combined FileSlice buffers
     * @return The file slice located at the position specified.
     */
    protected FileSlice getBuffer(long position) {

        int index = 0;
        if (position > 0) {
            index = (int) (position / SLICE_SIZE);
        }

        return slices.computeIfAbsent(index, integer -> {
            ByteBuffer buffer = BufferStream.allocate(SLICE_SIZE);
            return new FileSlice(buffer);
        });

    }

    @Override
    public void delete()
    {

    }

    /**
     * Close the data file
     *
     * @return Whether the in memory buffers were cleared
     */
    public synchronized boolean close() {
        for(FileSlice file : this.slices.values())
        {
            file.buffer.clear();
            file.buffer = null;
        }

        this.slices.clear();
        return true;
    }
}

