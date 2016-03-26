package com.onyx.record;

import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;

/**
 * Created by timothy.osborn on 2/5/15.
 */
public interface RecordController
{
    /**
     * Save an entity and persist it to the data file
     * @param entity
     * @return
     * @throws AttributeMissingException
     */
    Object save(IManagedEntity entity) throws EntityException;

    /**
     * Delete
     *
     * @param entity
     * @throws EntityException
     */
    void delete(IManagedEntity entity) throws EntityException;


    /**
     * Delete with ID
     *
     * @param primaryKey
     */
    void deleteWithId(Object primaryKey);

    /**
     * Get an entity by primary key
     *
     * @param primaryKey
     * @return
     */
    IManagedEntity getWithId(Object primaryKey) throws EntityException;

    /**
     * Get an entity by the entity
     *
     * @param primaryKey
     * @return
     */
    IManagedEntity get(IManagedEntity primaryKey) throws EntityException;

    /**
     * Returns true if the records exists
     *
     * @param entity
     * @return
     */
    boolean exists(IManagedEntity entity) throws EntityException;

    /**
     * Returns true if the records contain a primary key
     *
     * @param primaryKey
     * @return
     */
    boolean existsWithId(Object primaryKey) throws EntityException;

    /**
     * Returns true if the records contain a primary key
     *
     * @param primaryKey
     * @return
     */
    long getReferenceId(Object primaryKey) throws EntityException;

    /**
     * Returns the object using the reference ID
     *
     * @param referenceId
     * @return
     */
    IManagedEntity getWithReferenceId(long referenceId) throws EntityException;
}
