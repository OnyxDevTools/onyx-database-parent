package com.onyx.record;

import com.onyx.exception.AttributeMissingException;
import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;

import java.util.Map;

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

    /**
     * Returns a map of the entity with a reference id
     *
     * @param referenceId
     * @return
     * @throws EntityException
     */
    Map getMapWithReferenceId(long referenceId) throws EntityException;

    /**
     * Returns a specific attribute of an entity
     *
     * @param attribute
     * @param referenceId
     * @return
     * @throws EntityException
     */
    Object getAttributeWithReferenceId(String attribute, long referenceId) throws EntityException;
}
