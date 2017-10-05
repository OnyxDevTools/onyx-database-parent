package com.onyx.interactors.record

import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.reflection.ReflectionField

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * This is the contract that contains the actions acted upon an entity
 */
interface RecordInteractor {

    /**
     * Save an entity and persist it to the data file
     * @param entity Entity to save
     * @return Entity id
     * @throws OnyxException Attribute missing
     */
    @Throws(OnyxException::class)
    fun save(entity: IManagedEntity): Any

    /**
     * Delete
     *
     * @param entity Entity to delete
     * @throws OnyxException Error deleting entity
     */
    @Throws(OnyxException::class)
    fun delete(entity: IManagedEntity)

    /**
     * Delete with ID
     *
     * @param primaryKey Entity id to delete
     */
    fun deleteWithId(primaryKey: Any): IManagedEntity?

    /**
     * Get an entity by primary key
     *
     * @param primaryKey entity id
     * @return hydrated entity
     */
    @Throws(OnyxException::class)
    fun getWithId(primaryKey: Any): IManagedEntity?

    /**
     * Get an entity by the entity
     *
     * @param entity entity with id pre defined
     * @return hydrated entity
     */
    @Throws(OnyxException::class)
    operator fun get(entity: IManagedEntity): IManagedEntity?

    /**
     * Returns true if the records exists
     *
     * @param entity Entity to check
     * @return whether it exist
     */
    @Throws(OnyxException::class)
    fun exists(entity: IManagedEntity): Boolean

    /**
     * Returns true if the records contain a primary key
     *
     * @param primaryKey entity primary key
     * @return whether it exist
     */
    @Throws(OnyxException::class)
    fun existsWithId(primaryKey: Any?): Boolean

    /**
     * Returns true if the records contain a primary key
     *
     * @param primaryKey entity id
     * @return reference within store
     */
    @Throws(OnyxException::class)
    fun getReferenceId(primaryKey: Any): Long

    /**
     * Returns the object using the reference ID
     *
     * @param referenceId entity reference within store
     * @return hydrated entity
     */
    @Throws(OnyxException::class)
    fun getWithReferenceId(referenceId: Long): IManagedEntity?

    /**
     * Returns a structure of the entity with a reference id
     *
     * @param reference entity reference
     * @return entity as a map
     */
    @Throws(OnyxException::class)
    fun getMapWithReferenceId(reference: Long): Map<String, *>?

    /**
     * Returns a specific attribute of an entity
     *
     * @param attribute entity attribute
     * @param referenceId entity reference id
     * @return entity attribute
     * @throws OnyxException Attribute does not exist
     *
     * @since 1.3.0 Changed to include the reflection field in order to optimize
     * and not instantiate new reflection fields
     */
    @Throws(OnyxException::class)
    fun getAttributeWithReferenceId(attribute: ReflectionField, referenceId: Long): Any?

    /**
     * For sorted indexes, you can find all the entity references above the value.  The index value must implement comparable
     *
     * @param indexValue Index value to compare
     * @param includeValue whether it is above and including
     * @return Set of references
     *
     * @throws OnyxException Exception occurred while iterating index
     *
     * @since 1.2.0
     */
    @Throws(OnyxException::class)
    fun findAllAbove(indexValue: Any, includeValue: Boolean): Set<Long>

    /**
     * For sorted indexes, you can find all the entity references below the value.  The index value must implement comparable
     *
     * @param indexValue Index value to compare
     * @param includeValue whether it is below and including
     * @return Set of references
     *
     * @throws OnyxException Exception occurred while iterating index
     *
     * @since 1.2.0
     */
    @Throws(OnyxException::class)
    fun findAllBelow(indexValue: Any, includeValue: Boolean): Set<Long>
}
