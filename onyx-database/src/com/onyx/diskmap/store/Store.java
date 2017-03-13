package com.onyx.diskmap.store;

import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;
import com.onyx.diskmap.serializer.Serializers;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Created by tosborn on 3/27/15.
 *
 * This declares the contract for the volume storage
 */
public interface Store {

    /**
     * Write a serializable object to
     *
     * @param serializable Object serializable to write to store
     * @param position location to write to
     */
    @SuppressWarnings("UnusedReturnValue")
    int write(ObjectSerializable serializable, long position);

    /**
     * Write an Object Buffer
     *
     * @param serializable Object to write
     * @param position Position within the volume to write to.
     * @return How many bytes were written
     */
    @SuppressWarnings("UnusedReturnValue")
    int write(ObjectBuffer serializable, long position);

    /**
     * Write a serializable object
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @return Object Buffer contains bytes read
     */
    ObjectBuffer read(long position, int size);

    /**
     * Read the file channel and put it into a buffer at a position
     *
     * @param buffer   Buffer to put into
     * @param position position in store to read
     */
    @SuppressWarnings("unused")
    void read(ByteBuffer buffer, long position);

    /**
     * Read a serializable object from the store
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param type class type
     * @return The object that was read from the store
     */
    Object read(long position, int size, Class type);

    /**
     * Read a serializable object
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param object object to read into
     * @return same object instance that was sent in.
     */
    Object read(long position, int size, ObjectSerializable object);

    /**
     * Read a serializable object
     *
     * @param position Position to read from
     * @param size Amount of bytes to read.
     * @param serializerId Key to the serializer version that was used when written to the store
     * @return Object read from the store
     */
    @SuppressWarnings("unused")
    Object read(long position, int size, Class type, int serializerId);

    /**
     * Allocates a spot in the file
     *
     * @param size Allocate space within the store.
     * @return position of started allocated bytes
     */
    long allocate(int size);

    /**
     * Getter for serializers
     * @return Serializers used to serialize Object Buffers
     */
    Serializers getSerializers();

    /**
     * Getter for file longSize
     *
     * @return The self tracked size of the storage
     */
    long getFileSize();

    /**
     * Close file storage
     *
     * @return Whether the store was closed
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean close();

    /**
     * Commit and flush Storage
     */
    void commit();

    /**
     * Initialize
     * @param mapById Serializers by id
     * @param mapByName Serializers by namre
     */
    void init(Map mapById, Map mapByName);

    /**
     * Delete File
     *
     */
    void delete();

    /**
     * Getter for file path for store.  If this is in memory, this will be null
     *
     * @return File path
     */
    @SuppressWarnings("unused")
    String getFilePath();

    /**
     * Reset the storage so that it has a clean slate
     * and truncates all relative data.
     *
     * @since 1.3.0
     */
    void reset();
}

