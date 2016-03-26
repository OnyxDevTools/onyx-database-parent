package com.onyx.map.store;

import com.onyx.map.MapBuilder;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.ObjectSerializable;
import com.onyx.persistence.context.SchemaContext;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by tosborn on 3/27/15.
 */
public class MemoryMappedStore extends FileChannelStore implements Store {

    public static final int SLICE_SIZE = ((1024 * 1024) * 3);

    public List<FileSlice> slices;

    public MemoryMappedStore()
    {
        super();
    }

    /**
     * Constructor open file
     *
     * @param filePath
     */
    public MemoryMappedStore(String filePath, MapBuilder builder, SchemaContext context) {
        super(filePath, builder, context);
    }

    /**
     * Open the data file
     *
     * @param filePath
     * @return
     */
    public synchronized boolean open(String filePath) {
        try {

            // Open the file Channel
            super.open(filePath);

            slices = new ArrayList<>();
            long fileSize = this.fileSize.get();


            // Lets open the memory mapped files in 2Gig increments since on 32 bit machines the max is I think 2G.  Also buffers are limited by
            // using an int for position.  We are gonna bust that.
            long offset = 0;
            MappedByteBuffer buffer = null;

            while (fileSize > 0) {
                buffer = channel.map(FileChannel.MapMode.READ_WRITE, offset, SLICE_SIZE);
                slices.add(new FileSlice(buffer, new ReentrantLock(true)));
                offset += SLICE_SIZE;
                fileSize -= SLICE_SIZE;
            }

        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }

        return channel.isOpen();
    }

    /**
     * Close the data file
     *
     * @return
     */
    public synchronized boolean close() {
        try {

            this.slices.forEach(file ->
            {
                file.flush();
            });


            try {
                channel.truncate(fileSize.get());
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.channel.close();

            return !this.channel.isOpen();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Write an Object Buffer
     *
     * @param buffer
     * @param position
     * @return
     */
    public int write(ObjectBuffer buffer, long position) {
        final ByteBuffer byteBuffer = buffer.getByteBuffer();
        return this.write(byteBuffer, position);
    }

    /**
     * Write a buffer.  This is a helper function to work with a buffer rather than a FileChannel
     *
     * @param byteBuffer
     * @param position
     * @return
     */
    protected int write(ByteBuffer byteBuffer, long position) {

        final FileSlice slice = getBuffer(position);
        final byte[] bytesToWrite = byteBuffer.array();

        int bufLocation = getBufferLocation(position);
        int endBufLocation = bufLocation + bytesToWrite.length;

        // This occurs when we bridge from one slice to another
        if (endBufLocation > SLICE_SIZE) {
            final FileSlice overflowSlice = getBuffer(position + bytesToWrite.length);

            byte[] firstSlice = new byte[(int) (SLICE_SIZE - bufLocation)];
            System.arraycopy(bytesToWrite, 0, firstSlice, 0, firstSlice.length);

            byte[] secondSlice = new byte[endBufLocation - bufLocation - firstSlice.length];
            System.arraycopy(bytesToWrite, firstSlice.length, secondSlice, 0, secondSlice.length);

            slice.lock.lock();

            try {
                slice.buffer.position(bufLocation);
                slice.buffer.put(firstSlice);
            } finally {
                slice.lock.unlock();
            }
            overflowSlice.lock.lock();
            try {
                overflowSlice.buffer.position(0);
                overflowSlice.buffer.put(secondSlice);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                overflowSlice.lock.unlock();
            }
            return (int) byteBuffer.position();
        } else {
            slice.lock.lock();
            try {
                slice.buffer.position(getBufferLocation(position));
                slice.buffer.put(bytesToWrite);
                return (int) byteBuffer.position();
            } finally {
                slice.lock.unlock();
            }

        }
    }

    /**
     * Read a mem mapped file
     *
     * @param buffer
     * @param position
     */
    protected void read(ByteBuffer buffer, long position) {

        final FileSlice slice = getBuffer(position);

        int bufLocation = getBufferLocation(position);
        int endBufLocation = bufLocation + buffer.array().length;

        // This occurs when we bridge from one slice to another
        if (endBufLocation >= SLICE_SIZE) {
            final FileSlice overflowSlice = getBuffer(position + buffer.array().length);

            byte[] firstSlice = new byte[(int) (SLICE_SIZE - bufLocation)];
            byte[] secondSlice = new byte[endBufLocation - bufLocation - firstSlice.length];

            slice.lock.lock();

            try {
                slice.buffer.position(bufLocation);
                slice.buffer.get(firstSlice);
            } finally {
                slice.lock.unlock();
            }
            overflowSlice.lock.lock();
            try {
                overflowSlice.buffer.position(0);
                overflowSlice.buffer.get(secondSlice);
            } finally {
                overflowSlice.lock.unlock();
            }

            buffer.put(firstSlice);
            buffer.put(secondSlice);
        } else {
            slice.lock.lock();

            try {

                final byte[] bytes = new byte[buffer.limit()];

                slice.buffer.position(getBufferLocation(position));
                slice.buffer.get(bytes);

                buffer.put(bytes);
                buffer.rewind();
            } finally {
                slice.lock.unlock();
            }

        }

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
                long offset = (SLICE_SIZE * (long) index);
                ByteBuffer buffer = null;
                try {
                    buffer = channel.map(FileChannel.MapMode.READ_WRITE, offset, SLICE_SIZE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
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

    /**
     * Write a serializable object to
     *
     * @param serializable
     * @param position
     * @throws java.io.IOException
     */
    public int write(ObjectSerializable serializable, long position) {
        final ObjectBuffer objectBuffer = new ObjectBuffer(serializers);

        try {
            serializable.writeObject(objectBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return this.write(objectBuffer.getByteBuffer(), position);
    }

    /**
     * Write a serializable object
     *
     * @param position
     * @param size
     * @param serializerId
     * @return
     */
    public Object read(long position, int size, Class type, int serializerId)
    {
        if (position >= fileSize.get())
            return null;

        final ByteBuffer buffer = ObjectBuffer.allocate(size);

        this.read(buffer, position);
        buffer.rewind();

        try {
            if(serializerId > 0)
            {
                return ObjectBuffer.unwrap(buffer, serializers, serializerId);
            }
            else if (ObjectSerializable.class.isAssignableFrom(type)) {
                Object serializable = type.newInstance();
                final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, serializers);
                ((ObjectSerializable) serializable).readObject(objectBuffer, position);
                return serializable;
            } else {
                return ObjectBuffer.unwrap(buffer, serializers);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Write a serializable object
     *
     * @param position
     * @param size
     * @return
     */
    public Object read(long position, int size, Class type) {
        if (position >= fileSize.get())
            return null;

        final ByteBuffer buffer = ObjectBuffer.allocate(size);

        this.read(buffer, position);
        buffer.rewind();

        try {
            if (ObjectSerializable.class.isAssignableFrom(type)) {
                Object serializable = type.newInstance();
                final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, serializers);
                ((ObjectSerializable) serializable).readObject(objectBuffer, position);
                return serializable;
            } else {
                return ObjectBuffer.unwrap(buffer, serializers);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Get the location within the buffer slice
     *
     * @param position
     * @return
     */
    protected int getBufferLocation(long position) {
        int index = 0;
        if (position > 0) {
            index = (int) (position % SLICE_SIZE);
        }
        return index;
    }

    /**
     * File Slice
     * <p>
     * This contains the memory mapped segment as well as a lock for it
     */
    class FileSlice {
        public ByteBuffer buffer;
        public ReentrantLock lock;

        public FileSlice(ByteBuffer buffer, ReentrantLock lock) {
            this.buffer = buffer;
            this.lock = lock;
        }

        /* Hack to unmap MappedByteBuffer.
        * Unmap is necessary on Windows, otherwise file is locked until JVM exits or BB is GCed.
        * There is no public JVM API to unmap buffer, so this tries to use SUN proprietary API for unmap.
        * Any error is silently ignored (for example SUN API does not exist on Android).
        */
        public void flush() {
            if (buffer instanceof MappedByteBuffer) {
                ((MappedByteBuffer) buffer).force(); // Flush the contents of the buffer
                try {
                    Method cleanerMethod = buffer.getClass().getMethod("cleaner", new Class[0]);
                    if (cleanerMethod != null) {
                        cleanerMethod.setAccessible(true);
                        Object cleaner = cleanerMethod.invoke(buffer);
                        if (cleaner != null) {
                            Method clearMethod = cleaner.getClass().getMethod("clean", new Class[0]);
                            if (clearMethod != null) {
                                clearMethod.invoke(cleaner);
                            }
                        }
                    }
                } catch (Exception ignore) {
                }
            }
        }
    }

    /**
     * Commit storage
     */
    @Override
    public void commit() {
        for (int i = 0; i < slices.size(); i++) {
            if (slices.get(i).buffer instanceof MappedByteBuffer) {
                ((MappedByteBuffer)slices.get(i).buffer).force();
            }
        }
    }
}

