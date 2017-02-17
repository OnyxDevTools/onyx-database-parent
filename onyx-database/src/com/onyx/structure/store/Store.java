package com.onyx.structure.store;

import com.onyx.persistence.context.SchemaContext;
import com.onyx.structure.MapBuilder;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;
import com.onyx.structure.serializer.Serializers;

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
     * De-allocates a record
     *
     * @param position
     * @param size
     * @return
     */
    void deallocate(long position, int size);

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
    void init(MapBuilder builder);

    /**
     * Delete File
     *
     */
    void delete();

    /**
     * Delete File
     *
     */
    String getFilePath();
}

