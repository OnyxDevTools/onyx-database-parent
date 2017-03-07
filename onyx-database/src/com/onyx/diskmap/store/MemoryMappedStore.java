package com.onyx.diskmap.store;

import com.onyx.buffer.BufferStream;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.util.ReflectionUtil;
import sun.nio.ch.DirectBuffer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by tosborn on 3/27/15.
 *
 * This class uses buffers that are mapped to memory rather than a direct file channel
 */
public class MemoryMappedStore extends FileChannelStore implements Store {

    Map<Integer, FileSlice> slices;

    MemoryMappedStore()
    {

    }
    /**
     * Constructor open file
     *
     * @param filePath File location for the store
     */
    public MemoryMappedStore(String filePath, SchemaContext context, boolean deleteOnClose) {
        super(filePath, context, deleteOnClose);
    }

    /**
     * Open the data file
     *
     * @param filePath File location for the store
     * @return Whether the file was opened and the first file slice was allocated
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

    @SuppressWarnings("WeakerAccess")
    protected static final ExecutorService cleanupService = Executors.newSingleThreadExecutor();

    /**
     * Close the data file
     *
     * @return  Close the memory mapped flie and truncate to get rid of the remainder of allocated space for the last
     * file slice
     */
    public synchronized boolean close() {
        try {

            if (!deleteOnClose) {
                try {
                    channel.truncate(fileSize.get());
                } catch (IOException ignore) {}
            }

            final Runnable runnable = () -> {
                try {
                    this.slices.values().forEach(FileSlice::flush);
                    this.slices.clear();
                    channel.close();
                    if (deleteOnClose) {
                        delete();
                    }
                } catch (IOException ignore) {
                }
            };
            cleanupService.execute(runnable);
            return !this.channel.isOpen();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Write an Object Buffer
     *
     * @param buffer Object buffer to write
     * @param position position within store to write to
     * @return How many bytes were written
     */
    public int write(ObjectBuffer buffer, long position) {
        final ByteBuffer byteBuffer = buffer.getByteBuffer();
        return this.write(byteBuffer, position);
    }

    /**
     * Write a buffer.  This is a helper function to work with a buffer rather than a FileChannel
     *
     * @param byteBuffer Byte buffer to write
     * @param position position within store to write to
     * @return how many bytes were written
     */
    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "WeakerAccess"})
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
     * @param buffer Byte buffer to read
     * @param position within the store
     */
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
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
     * @param position within the store
     * @param size Amount of bytes to read
     * @return Object buffer that was read
     */
    public ObjectBuffer read(long position, int size) {
        if (!validateFileSize(position))
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
        if (!validateFileSize(position))
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
     * @param position position within memory mapped store
     * @return The corresponding slice that is at that position
     */
    @SuppressWarnings("WeakerAccess")
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

            return new FileSlice(buffer);
        });
    }

    /**
     * Write a serializable object to
     *
     * @param serializable Object serializable to write to store
     * @param position location to write to
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
     * @since 1.2.0 This was migrated to use the Buffer stream.
     *
     * @param position Position within store
     * @param size Size of object to read
     * @param serializerId Serializer version
     * @return instantiated serialized object read from store
     */
    public Object read(long position, int size, Class type, int serializerId) {
        if (!validateFileSize(position))
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
        } catch (IOException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        } finally {
            BufferStream.recycle(buffer);
        }

        return null;
    }

    /**
     * Read a serializable object
     *
     * @param position position within store
     * @param size size of object to read
     * @param type Type of object to assign object to
     * @return Instantiated object of type
     */
    public Object read(long position, int size, Class type) {
        if (!validateFileSize(position))
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
        } catch (IOException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        } finally {
            BufferStream.recycle(buffer);
        }

        return null;
    }

    /**
     * Get the location within the buffer slice
     *
     * @param position Position within the store
     * @return file slice id
     */
    private int getBufferLocation(long position) {
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

        FileSlice(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        /* Hack to unmap MappedByteBuffer.
        * Unmap is necessary on Windows, otherwise file is locked until JVM exits or BB is GCed.
        * There is no public JVM API to unmap buffer, so this tries to use SUN proprietary API for unmap.
        * Any error is silently ignored (for example SUN API does not exist on Android).
        */
        void flush() {
            if (buffer instanceof MappedByteBuffer) {
                ((DirectBuffer) buffer).cleaner().clean();
            }
        }
    }

    /**
     * Commit storage
     */
    @Override
    public void commit() {
        if (!deleteOnClose) {
            slices.values().forEach(fileSlice -> {
                if (fileSlice.buffer instanceof MappedByteBuffer) {
                    ((MappedByteBuffer) fileSlice.buffer).force();
                }
            });
        }
    }
}

