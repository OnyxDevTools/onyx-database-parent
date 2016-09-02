package com.onyx.map.store;

import com.onyx.map.MapBuilder;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.persistence.context.SchemaContext;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by tosborn on 3/27/15.
 */
public class InMemoryStore extends MemoryMappedStore implements Store {

    /**
     * Constructor open file
     *
     * @param builder
     */
    public InMemoryStore(MapBuilder builder, SchemaContext context, String storeId) {
        super();
        this.builder = builder;
        this.context = context;
        open(storeId);
        this.setSize();
    }

    /**
     * Open the data file
     *
     * @param filePath
     * @return
     */
    public synchronized boolean open(String filePath) {

        this.filePath = filePath;
        slices = new ConcurrentHashMap();

        // Lets open the memory mapped files in 2Gig increments since on 32 bit machines the max is I think 2G.  Also buffers are limited by
        // using an int for position.  We are gonna bust that.
        ByteBuffer buffer = null;

        buffer = ObjectBuffer.allocate(SLICE_SIZE);
        slices.put(0, new FileSlice(buffer, new ReentrantLock(true)));

        return true;
    }

    /**
     * Get the associated buffer to the position of the file.  So if the position is 2G + it will get the prop
     * er "slice" of the file
     *
     * @param position
     * @return
     */
    protected FileSlice getBuffer(long position) {

        int index = 0;
        if (position > 0) {
            index = (int) (position / SLICE_SIZE);
        }

        return slices.compute(index, (integer, fileSlice) -> {
            if(fileSlice != null)
                return fileSlice;

            ByteBuffer buffer = ObjectBuffer.allocate(SLICE_SIZE);
            return new FileSlice(buffer, new ReentrantLock(true));
        });

    }

    @Override
    public void delete()
    {

    }
}

