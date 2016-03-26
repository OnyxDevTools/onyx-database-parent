package com.onyx.map.store;

import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.ObjectSerializable;
import com.onyx.map.serializer.Serializers;
import com.onyx.persistence.context.SchemaContext;

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
     * Getter for file size
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
     */
    void init();

    /**
     * Getter for schema context as it pertains to onyxdb
     * @return
     */
    SchemaContext getContext();

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

