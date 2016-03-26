package com.onyx.map.store;

import com.onyx.map.MapBuilder;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.persistence.context.SchemaContext;

import java.nio.ByteBuffer;
import java.util.ArrayList;
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
    public InMemoryStore(MapBuilder builder, SchemaContext context) {
        super();
        this.builder = builder;
        this.context = context;
        open(null);
        this.setSize();
    }

    /**
     * Open the data file
     *
     * @param filePath
     * @return
     */
    public synchronized boolean open(String filePath) {

        slices = new ArrayList<>();
        long fileSize = this.fileSize.get();


        // Lets open the memory mapped files in 2Gig increments since on 32 bit machines the max is I think 2G.  Also buffers are limited by
        // using an int for position.  We are gonna bust that.
        long offset = 0;
        ByteBuffer buffer = null;

        while (fileSize > 0) {
            buffer = ObjectBuffer.allocate(SLICE_SIZE);
            slices.add(new FileSlice(buffer, new ReentrantLock(true)));
            offset += SLICE_SIZE;
            fileSize -= SLICE_SIZE;
        }


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

        synchronized (slices) {
            if (index >= slices.size()) {
                ByteBuffer buffer = ObjectBuffer.allocate(SLICE_SIZE);
                slices.add(new FileSlice(buffer, new ReentrantLock(true)));
            }
        }

        try {
            return slices.get(index);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void delete()
    {

    }
}

