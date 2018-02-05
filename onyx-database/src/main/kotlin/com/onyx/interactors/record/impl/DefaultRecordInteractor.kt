package com.onyx.interactors.record.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.SortedDiskMap
import com.onyx.exception.AttributeTypeMismatchException
import com.onyx.exception.EntityCallbackException
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.QueryListenerEvent
import com.onyx.interactors.record.RecordInteractor
import java.lang.reflect.Field

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by timothy.osborn on 2/5/15.
 *
 * This controls the crud for a record
 */
open class DefaultRecordInteractor(val entityDescriptor: EntityDescriptor, protected val context: SchemaContext) : RecordInteractor {

    protected val records: DiskMap<Any, IManagedEntity>

    init {
        val dataFile = context.getDataFile(entityDescriptor)
        records = dataFile.getHashMap(entityDescriptor.entityClass.name, entityDescriptor.identifier!!.loadFactor.toInt())
    }

    /**
     * Save an entity
     *
     * @param entity Entity to save
     * @return Pair of existing reference id and new identifier value
     * @throws OnyxException Error saving entity
     *
     * @since 1.2.3 Optimized to only do a put if there are not pre persist callbacks
     * @since 2.0.0 Optimized to return the old reference value
     */
    @Throws(OnyxException::class)
    override fun save(entity: IManagedEntity): Pair<Long, Any> {
        val identifierValue = entity.identifier(context)

        var oldReference = 0L

        val isNew = AtomicBoolean(false) // Keeps track of whether the record is new or not

        if (this.entityDescriptor.preInsertCallback != null || this.entityDescriptor.preUpdateCallback != null) {

            records.compute(identifierValue!!) { _, current ->
                try {
                    if (current == null) {
                        isNew.set(true)
                        entity.onPreInsert(context, entityDescriptor)
                    } else {
                            val recordId = records.getRecID(identifierValue)
                            if (recordId > 0L) {
                                // Update Cached queries
                                oldReference = recordId
                                context.queryCacheInteractor.updateCachedQueryResultsForEntity(entity, this.entityDescriptor, Reference(entity.partitionId(context), recordId), QueryListenerEvent.PRE_UPDATE)
                            }
                        entity.onPreUpdate(context, entityDescriptor)
                    }
                } catch (ignore: EntityCallbackException) { }

                entity
            }
        } else {
            entity.onPrePersist(context, entityDescriptor)
            val recordId = records.getRecID(identifierValue!!)
            if (recordId > 0L) {
                oldReference = recordId
                isNew.set(false)
                // Update Cached queries
                context.queryCacheInteractor.updateCachedQueryResultsForEntity(entity, this.entityDescriptor, Reference(entity.partitionId(context), recordId), QueryListenerEvent.PRE_UPDATE)
            } else {
                isNew.set(true)
            }
            records.put(identifierValue, entity)
        }

        // Invoke Post insert or update callback
        if (isNew.get()) {
            entity.onPostInsert(context, entityDescriptor)
        } else {
            entity.onPostUpdate(context, entityDescriptor)
        }

        // Return the id
        return Pair(oldReference, identifierValue)
    }

    /**
     * Get an entity by primary key
     *
     * @param primaryKey Identifier of an entity
     * @return Entity if it exist
     */
    @Throws(OnyxException::class)
    override fun getWithId(primaryKey: Any): IManagedEntity? = records[primaryKey]

    /**
     * Returns true if the record exists in database
     *
     * @param entity Entity to check
     * @return Whether it exists
     */
    @Throws(OnyxException::class)
    override fun exists(entity: IManagedEntity): Boolean = records.containsKey(entity.identifier(context))

    /**
     * Delete
     *
     * @param entity Entity to delete
     * @throws OnyxException Error deleting an entity
     */
    @Throws(OnyxException::class)
    override fun delete(entity: IManagedEntity) {
        val identifierValue = entity.identifier(context)
        // Update Cached queries
        val recordId = records.getRecID(identifierValue!!)
        if (recordId > -1) {
            entity.onPreRemove(context, entityDescriptor)
            context.queryCacheInteractor.updateCachedQueryResultsForEntity(entity, this.entityDescriptor, Reference(entity.partitionId(context), recordId), QueryListenerEvent.DELETE)
            this.deleteWithId(identifierValue)
            entity.onPostRemove(context, entityDescriptor)
        }
    }

    /**
     * Delete with ID
     *
     * @param primaryKey Identifier of an entity
     */
    override fun deleteWithId(primaryKey: Any) = records.remove(primaryKey)

    /**
     * Get an entity by the entity with populated primary key
     *
     * @param entity Entity to get.  Its id must be defined
     * @return Hydrated entity
     */
    @Throws(OnyxException::class)
    override operator fun get(entity: IManagedEntity): IManagedEntity? = getWithId(entity.identifier(context)!!)

    /**
     * Returns the record reference ID
     *
     * @param primaryKey Identifier for entity
     * @return Entity reference id
     */
    @Throws(OnyxException::class)
    override fun getReferenceId(primaryKey: Any): Long = records.getRecID(primaryKey)

    /**
     * Returns the object using the reference ID
     *
     * @param referenceId Entity reference id
     * @return Hydrated entity
     */
    @Throws(OnyxException::class)
    override fun getWithReferenceId(referenceId: Long): IManagedEntity = records.getWithRecID(referenceId)!!

    /**
     * Returns a structure of the entity with a reference id
     *
     * @param reference Entity reference id
     * @return Entity as a map
     */
    @Throws(OnyxException::class)
    override fun getMapWithReferenceId(reference: Long): Map<String, Any?> = records.getMapWithRecID(reference)!!

    /**
     * Get a specific attribute with reference Id
     *
     * @param attribute Name of attribute to get
     * @param referenceId location of record within storage
     * @return Attribute key
     */
    @Throws(AttributeTypeMismatchException::class)
    override fun getAttributeWithReferenceId(attribute: Field, referenceId: Long): Any? = records.getAttributeWithRecID(attribute, referenceId)

    /**
     * Find all objects greater than the key parameter.  The underlying data
     * structure should be sorted
     *
     * @param indexValue The value to compare
     * @param includeValue Include whether the keys match what you pass in as index value
     * @return A set of REFERENCES not the actual values
     * @throws OnyxException Error when reading the store
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun findAllAbove(indexValue: Any, includeValue: Boolean): Set<Long> = (records as SortedDiskMap<Any, IManagedEntity>).above(indexValue, includeValue)

    /**
     * Find all objects less than the key parameter.  The underlying data
     * structure should be sorted
     *
     * @param indexValue The value to compare
     * @param includeValue Include whether the keys match what you pass in as index value
     * @return A set of REFERENCES not the actual values
     * @throws OnyxException Error when reading the store
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    override fun findAllBelow(indexValue: Any, includeValue: Boolean): Set<Long> = (records as SortedDiskMap<Any, IManagedEntity>).below(indexValue, includeValue)

    /**
     * Find all the references between from and to values.
     *
     * This has one prerequisite.  You must be using a DiskMatrixHashMap as the storage mechanism.  Otherwise it will not be
     * sorted.
     *
     * @param fromValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeFromValue Whether to compare above and equal or not.
     * @param toValue Key to end range to
     * @param includeToValue Whether to compare equal or not.
     * @return A set of record references
     *
     * @since 1.2.0
     */
    @Suppress("UNCHECKED_CAST")
    override fun findAllBetween(fromValue: Any?, includeFromValue: Boolean, toValue: Any?, includeToValue: Boolean): Set<Long> = (records as SortedDiskMap<Any, IManagedEntity>).between(fromValue, includeFromValue, toValue, includeToValue)
}
