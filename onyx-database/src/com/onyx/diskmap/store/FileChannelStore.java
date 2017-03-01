package com.onyx.diskmap.store;

import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;
import com.onyx.diskmap.serializer.Serializers;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by timothy.osborn on 3/25/15.
 * <p>
 * The default implementation of a store that includes the i/o of writting to a basic file channel.
 * This is recommended for larger data sets.
 *
 * This class also encapsulates the serialization of objects that are read and written to the store.
 *
 * @since 1.0.0
 */
public class FileChannelStore implements Store {

    @SuppressWarnings("WeakerAccess")
    protected FileChannel channel;
    @SuppressWarnings("WeakerAccess")
    protected String contextId = "";
    @SuppressWarnings("WeakerAccess")
    protected boolean deleteOnClose = false;
    String filePath;

    int SLICE_SIZE = ((1024 * 1024) * 3);

    final AtomicLong fileSize = new AtomicLong(0);

    // This is an internal structure only used to store serializers
    Serializers serializers = null;

    /**
     * Constructor open file
     *
     * @param filePath Location of the store
     */
    public FileChannelStore(String filePath, SchemaContext context, boolean deleteOnClose) {
        if (deleteOnClose) {
            SLICE_SIZE = (1024 * 512);
        }
        this.filePath = filePath;
        this.deleteOnClose = deleteOnClose;
        open(filePath);
        this.setSize();
        if (context != null)
            this.contextId = context.getContextId();
    }

    /**
     * Constructor
     */
    FileChannelStore() {

    }

    /**
     * Initialize the file
     */
    @SuppressWarnings("unchecked")
    public void init(Map mapById, Map mapByName) {
        serializers = new Serializers(mapById, mapByName, DefaultSchemaContext.registeredSchemaContexts.get(contextId));
    }


    /**
     * Open the data file
     *
     * @param filePath Path of the file to open
     * @return Whether the file was opened or not
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored", "UnusedReturnValue", "WeakerAccess"})
    public boolean open(String filePath) {
        final File file = new File(filePath);
        try {
            // Create the data file if it does not exist
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }

            // Open the random access file
            final RandomAccessFile randomAccessFile = new RandomAccessFile(filePath, "rw");
            this.channel = randomAccessFile.getChannel();
            this.fileSize.set(this.channel.size());

        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }

        return channel.isOpen();
    }

    /**
     * Set the size after opening a file.  The first 8 bytes are reserved for the size.  The reason why we maintain the size
     * outside of relying of the fileChannel is because it may not be accurate.  In order to force it's accuracy
     * we have to configure the file channel to do so.  That causes the store to be severly slowed down.
     */
    void setSize() {
        final ObjectBuffer buffer = this.read(0, 8);

        try {
            if (buffer == null || channel.size() == 0) {
                this.allocate(8);
            } else {
                Long fSize = buffer.readLong();
                this.fileSize.set(fSize);
            }
        } catch (IOException ignore) {}
    }

    /**
     * Close the data file
     *
     * @return Whether the file was closed successfully.
     */
    public boolean close() {
        try {
            if (!deleteOnClose) {
                this.channel.force(true);
            }
            this.channel.close();
            if (deleteOnClose) {
                delete();
            }
            return !this.channel.isOpen();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Commit all file writes
     */
    @Override
    public void commit() {
        try {
            this.channel.force(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write an Object Buffer
     *
     * @param buffer Object buffer to write
     * @param position Position within the volume to write to.
     * @return How many bytes were written
     */
    public int write(ObjectBuffer buffer, long position) {

        try {
            return channel.write(buffer.getByteBuffer(), position);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Write a serializable object to a volume.  This uses the ObjectBuffer for serialization
     *
     * @param serializable Object
     * @param position Position to write to
     */
    public int write(ObjectSerializable serializable, long position) {
        final ObjectBuffer objectBuffer = new ObjectBuffer(serializers);

        try {
            serializable.writeObject(objectBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            return channel.write(objectBuffer.getByteBuffer(), position);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Read a serializable object from the store
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param type class type
     * @return The object that was read from the store
     */
    public Object read(long position, int size, Class type) {
        if (position >= fileSize.get())
            return null;

        final ByteBuffer buffer = ObjectBuffer.allocate(size);

        try {
            channel.read(buffer, position);
            buffer.rewind();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (ObjectSerializable.class.isAssignableFrom(type)) {
                Object serializable = type.newInstance();
                final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, serializers);
                ((ObjectSerializable) serializable).readObject(objectBuffer, position);
                return serializable;
            } else {
                return ObjectBuffer.unwrap(buffer, serializers);
            }
        } catch (IOException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Read a serializable object
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param object object to read into
     * @return same object instance that was sent in.
     */
    public Object read(long position, int size, ObjectSerializable object) {
        if (position >= fileSize.get())
            return null;

        final ByteBuffer buffer = ObjectBuffer.allocate(size);

        try {
            channel.read(buffer, position);
            buffer.rewind();
            object.readObject(new ObjectBuffer(buffer, serializers), position);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return object;
    }

    /**
     * Read a serializable object
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param serializerId Key to the serializer version that was used when written to the store
     * @return Object read from the store
     */
    public Object read(long position, int size, Class type, int serializerId) {
        if (position >= fileSize.get())
            return null;

        final ByteBuffer buffer = ObjectBuffer.allocate(size);

        try {
            channel.read(buffer, position);
            buffer.rewind();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (serializerId > 0) {
                return ObjectBuffer.unwrap(buffer, serializers, serializerId);
            } else if (ObjectSerializable.class.isAssignableFrom(type)) {
                Object serializable = type.newInstance();
                final ObjectBuffer objectBuffer = new ObjectBuffer(buffer, serializers);
                ((ObjectSerializable) serializable).readObject(objectBuffer, position);
                return serializable;
            } else {
                return ObjectBuffer.unwrap(buffer, serializers);
            }
        } catch (IOException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Write a serializable object
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @return Object Buffer contains bytes read
     */
    public ObjectBuffer read(long position, int size) {
        if (position >= fileSize.get())
            return null;

        final ByteBuffer buffer = ObjectBuffer.allocate(size);

        try {
            channel.read(buffer, position);
            buffer.rewind();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new ObjectBuffer(buffer, serializers);

    }

    /**
     * Read the file channel and put it into a buffer at a position
     *
     * @param buffer   Buffer to put into
     * @param position position in store to read
     */
    @Override
    public void read(ByteBuffer buffer, long position) {
        try {
            channel.read(buffer, position);
            buffer.flip();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Allocates a spot in the file
     *
     * @param size Allocate space within the store.
     * @return position of started allocated bytes
     */
    public long allocate(int size) {
        final ObjectBuffer buffer = new ObjectBuffer(serializers);
        long newFileSize = fileSize.getAndAdd(size);
        try {
            buffer.writeLong(newFileSize + size);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.write(buffer, 0);
        return newFileSize;
    }

    @Override
    public Serializers getSerializers() {
        return this.serializers;
    }

    @Override
    public long getFileSize() {
        return fileSize.get();
    }

    /**
     * Delete File
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void delete() {
        final File dataFile = new File(filePath);
        dataFile.delete();
    }
}
