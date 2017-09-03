package com.onyx.record;

import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.util.OffsetField;

import java.util.Map;
import java.util.Set;

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * This is the contract that contains the actions acted upon an entity
 */
public interface RecordController
{
    /**
     * Save an entity and persist it to the data file
     * @param entity Entity to save
     * @return ENtity id
     * @throws EntityException Attribute missing
     */
    Object save(IManagedEntity entity) throws EntityException;

    /**
     * Delete
     *
     * @param entity Entity to delete
     * @throws EntityException Error deleting entity
     */
    void delete(IManagedEntity entity) throws EntityException;


    /**
     * Delete with ID
     *
     * @param primaryKey Entity id to delete
     */
    void deleteWithId(Object primaryKey);

    /**
     * Get an entity by primary key
     *
     * @param primaryKey entity id
     * @return hydrated entity
     */
    @SuppressWarnings("RedundantThrows")
    IManagedEntity getWithId(Object primaryKey) throws EntityException;

    /**
     * Get an entity by the entity
     *
     * @param primaryKey entity with id pre defined
     * @return hydrated entity
     */
    IManagedEntity get(IManagedEntity primaryKey) throws EntityException;

    /**
     * Returns true if the records exists
     *
     * @param entity Entity to check
     * @return whether it exist
     */
    boolean exists(IManagedEntity entity) throws EntityException;

    /**
     * Returns true if the records contain a primary key
     *
     * @param primaryKey entity primary key
     * @return whether it exist
     */
    @SuppressWarnings("RedundantThrows")
    boolean existsWithId(Object primaryKey) throws EntityException;

    /**
     * Returns true if the records contain a primary key
     *
     * @param primaryKey entity id
     * @return reference within store
     */
    @SuppressWarnings("RedundantThrows")
    long getReferenceId(Object primaryKey) throws EntityException;

    /**
     * Returns the object using the reference ID
     *
     * @param referenceId entity reference within store
     * @return hydrated entity
     */
    @SuppressWarnings("RedundantThrows")
    IManagedEntity getWithReferenceId(long referenceId) throws EntityException;

    /**
     * Returns a structure of the entity with a reference id
     *
     * @param referenceId entity reference
     * @return entity as a map
     */
    @SuppressWarnings("RedundantThrows")
    Map getMapWithReferenceId(long referenceId) throws EntityException;

    /**
     * Returns a specific attribute of an entity
     *
     * @param attribute entity attribute
     * @param referenceId entity reference id
     * @return entity attribute
     * @throws EntityException Attribute does not exist
     *
     * @since 1.3.0 Changed to include the reflection field in order to optimze
     *              and not instantiate new reflection fields
     */
    @SuppressWarnings("RedundantThrows")
    Object getAttributeWithReferenceId(OffsetField attribute, long referenceId) throws EntityException;

    /**
     * For sorted indexs, you can find all the entity references above the value.  The index value must impelement comparable
     *
     * @param indexValue Index value to compare
     * @param includeValue whether it is above and including
     * @return Set of references
     *
     * @throws EntityException Exception ocurred while iterating index
     *
     * @since 1.2.0
     */
    @SuppressWarnings("RedundantThrows")
    Set<Long> findAllAbove(Object indexValue, boolean includeValue) throws EntityException;

    /**
     * For sorted indexs, you can find all the entity references below the value.  The index value must impelement comparable
     *
     * @param indexValue Index value to compare
     * @param includeValue whether it is below and including
     * @return Set of references
     *
     * @throws EntityException Exception ocurred while iterating index
     *
     * @since 1.2.0
     */
    @SuppressWarnings("RedundantThrows")
    Set<Long> findAllBelow(Object indexValue, boolean includeValue) throws EntityException;

}
