package com.onyx.diskmap.store;

import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;
import com.onyx.diskmap.serializer.Serializers;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Created by tosborn on 3/27/15.
 */
public interface Store {

    /**
     * Write a serializable object to
     *
     * @param serializable
     * @param position
     * @throws java.io.IOException
     */
    int write(ObjectSerializable serializable, long position);

    /**
     * Write a serializable object to
     *
     * @param serializable
     * @param position
     * @throws java.io.IOException
     */
    int write(ObjectBuffer serializable, long position);

    /**
     * Read a buffer at position for a longSize
     *
     * @param position
     * @param size
     * @return
     */
    ObjectBuffer read(long position, int size);

    /**
     * Read a file at a position for a and put value into byte buffer
     * @param position position in store to read
     * @param buffer Buffer to put into
     *
     */
    void read(ByteBuffer buffer, long position);

    /**
     * Write a serializable object
     *
     * @param position
     * @param size
     * @return
     */
    Object read(long position, int size, Class type);

    /**
     * Write a serializable object
     *
     * @param position
     * @param size
     * @param object
     * @return
     */
    Object read(long position, int size, ObjectSerializable object);

    /**
     * Write a serializable object
     *
     * @param position
     * @param size
     * @param serializerId
     * @return
     */
    Object read(long position, int size, Class type, int serializerId);

    /**
     * Allocates a spot in the file
     *
     * @param size
     * @return
     */
    long allocate(int size);

    /**
     * Getter for serializers
     * @return
     */
    Serializers getSerializers();

    /**
     * Getter for file longSize
     *
     * @return
     */
    long getFileSize();

    /**
     * Close file storage
     *
     * @return
     */
    boolean close();

    /**
     * Commit and flush Storage
     */
    void commit();

    /**
     * Initialize
     * @param builder Map builder used to initialize
     */
    void init(Map mapById, Map mapByName);

    /**
     * Delete File
     *
     */
    void delete();

}

