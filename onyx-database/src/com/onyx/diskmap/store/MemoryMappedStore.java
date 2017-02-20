package com.onyx.diskmap.store;

import com.onyx.buffer.BufferStream;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;
import com.onyx.util.ReflectionUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by tosborn on 3/27/15.
 */
public class MemoryMappedStore extends FileChannelStore implements Store {

    public static final int SLICE_SIZE = ((1024 * 1024) * 3);

    public Map<Integer, FileSlice> slices;

    public MemoryMappedStore() {
        super();
    }

    /**
     * Constructor open file
     *
     * @param filePath
     */
    public MemoryMappedStore(String filePath, SchemaContext context, boolean force) {
        super(filePath, context, force);
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

            // Load the first chunk into memory
            slices = new ConcurrentHashMap<>();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, SLICE_SIZE);
            slices.put(0, new FileSlice(buffer));

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

            if (force) {
                this.slices.values().stream().forEach(file -> file.flush());

                try {
                    channel.truncate(fileSize.get());
                } catch (IOException e) {
                }
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

        int bufLocation = getBufferLocation(position);
        int endBufLocation = bufLocation + byteBuffer.limit();

        // This occurs when we bridge from one slice to another
        if (endBufLocation > SLICE_SIZE) {
            final FileSlice overflowSlice = getBuffer(position + byteBuffer.limit());

            synchronized (slice) {
                slice.buffer.position(bufLocation);
                for (int i = 0; i < SLICE_SIZE - bufLocation; i++)
                    slice.buffer.put(byteBuffer.get());
            }
            synchronized (overflowSlice) {
                overflowSlice.buffer.position(0);
                for (int i = 0; i < endBufLocation - bufLocation - (SLICE_SIZE - bufLocation); i++)
                    overflowSlice.buffer.put(byteBuffer.get());
            }
            return byteBuffer.position();
        } else {
            synchronized (slice) {
                slice.buffer.position(getBufferLocation(position));
                slice.buffer.put(byteBuffer);
                return byteBuffer.position();
            }
        }
    }

    /**
     * Read a mem mapped file
     *
     * @param buffer
     * @param position
     */
    public void read(ByteBuffer buffer, long position) {

        final FileSlice slice = getBuffer(position);

        int bufLocation = getBufferLocation(position);
        int endBufLocation = bufLocation + buffer.limit();

        // This occurs when we bridge from one slice to another
        if (endBufLocation >= SLICE_SIZE) {
            final FileSlice overflowSlice = getBuffer(position + buffer.limit());

            synchronized (slice) {
                slice.buffer.position(bufLocation);
                for (int i = 0; i < SLICE_SIZE - bufLocation; i++)
                    buffer.put(slice.buffer.get());
            }
            synchronized (overflowSlice) {
                overflowSlice.buffer.position(0);
                for (int i = 0; i < endBufLocation - bufLocation - (SLICE_SIZE - bufLocation); i++)
                    buffer.put(overflowSlice.buffer.get());
            }
        } else {
            synchronized (slice) {
                slice.buffer.position(getBufferLocation(position));
                int bytesToRead = buffer.limit();
                for (int i = 0; i < bytesToRead; i++)
                    buffer.put(slice.buffer.get());

                buffer.flip();
            }
        }
    }

    /**
     * Write a serializable object
     *
     * @param position
     * @param size
     * @return
     */
    public ObjectBuffer read(long position, int size) {
        if (position >= fileSize.get())
            return null;

        final ByteBuffer buffer = ObjectBuffer.allocate(size);
        this.read(buffer, position);
        buffer.rewind();

        return new ObjectBuffer(buffer, serializers);
    }

    /**
     * Read a from the store and put it into the serializable object that is already instantiated
     *
     * @param position position to read from the store
     * @param size     how many bytes to read
     * @param object   object to map the results to
     * @return the object you sent in
     */
    public Object read(long position, int size, ObjectSerializable object) {
        if (position >= fileSize.get())
            return null;

        final ObjectBuffer buffer = read(position, size);

        try {
            object.readObject(buffer);
            return object;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
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

        final int finalIndex = index;

        return slices.compute(index, (integer, fileSlice) -> {
            if (fileSlice != null)
                return fileSlice;

            long offset = ((long) SLICE_SIZE * (long) finalIndex);
            ByteBuffer buffer = null;
            try {
                buffer = channel.map(FileChannel.MapMode.READ_WRITE, offset, SLICE_SIZE);
            } catch (IOException e) {
                e.printStackTrace();
            }

            FileSlice newFileSize = new FileSlice(buffer);
            return newFileSize;
        });
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
    public Object read(long position, int size, Class type, int serializerId) {
        if (position >= fileSize.get())
            return null;

        final ByteBuffer buffer = BufferStream.allocate(size);

        this.read(buffer, position);
        buffer.rewind();

        try {
            if (serializerId > 0) {
                return ObjectBuffer.unwrap(buffer, serializers, serializerId);
            } else if (ObjectSerializable.class.isAssignableFrom(type)) {
                Object serializable = ReflectionUtil.instantiate(type);//type.newInstance();
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
        } finally {
            BufferStream.recycle(buffer);
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

        final ByteBuffer buffer = BufferStream.allocate(size);

        this.read(buffer, position);
        buffer.rewind();

        try {
            if (ObjectSerializable.class.isAssignableFrom(type)) {
                Object serializable = ReflectionUtil.instantiate(type);//type.newInstance();
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
        } finally {
            BufferStream.recycle(buffer);
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

        public FileSlice(ByteBuffer buffer) {
            this.buffer = buffer;
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
                    Method cleanerMethod = buffer.getClass().getMethod("cleaner");
                    if (cleanerMethod != null) {
                        cleanerMethod.setAccessible(true);
                        Object cleaner = cleanerMethod.invoke(buffer);
                        if (cleaner != null) {
                            Method clearMethod = cleaner.getClass().getMethod("clean");
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
        if (force) {
            slices.values().forEach(fileSlice -> {
                if (fileSlice.buffer instanceof MappedByteBuffer) {
                    ((MappedByteBuffer) fileSlice.buffer).force();
                }
            });
        }
    }
}

