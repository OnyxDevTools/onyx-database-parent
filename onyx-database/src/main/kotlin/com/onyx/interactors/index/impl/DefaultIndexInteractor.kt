package com.onyx.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.exception.OnyxException
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.data.Header
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.extension.identifier
import java.lang.ref.WeakReference

import java.util.*
import kotlin.collections.HashMap

/**
 * Created by timothy.osborn on 1/29/15.
 *
 * Controls behavior of an index
 */
class DefaultIndexInteractor @Throws(OnyxException::class) constructor(private val descriptor: EntityDescriptor, override val indexDescriptor: IndexDescriptor, context: SchemaContext) : IndexInteractor {

    private val contextReference: WeakReference<SchemaContext>

    private val context: SchemaContext
        get() = contextReference.get()!!

    private val dataFile: DiskMapFactory
        get() = context.getDataFile(descriptor)

    private val references: DiskMap<Any, Header>// Stores the references for an index key
        get() = dataFile.getHashMap(indexDescriptor.type, descriptor.entityClass.name + indexDescriptor.name)

    private val indexValues: DiskMap<Long, Any>
        get() = dataFile.getHashMap(Long::class.java, descriptor.entityClass.name + indexDescriptor.name + "indexValues")

    init {
        contextReference = WeakReference(context)
    }

    /**
     * Save an index key with the record reference
     *
     * @param indexValue Index value to save
     * @param oldReferenceId Old entity reference for the index
     * @param newReferenceId New entity reference for the index
     */
    @Throws(OnyxException::class)
    @Synchronized
    override fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) {

        // Delete the old index key
        if (oldReferenceId > 0) {
            delete(oldReferenceId)
        }

        references.compute(indexValue ?: "") { _, existingHeader ->
            val header = existingHeader ?: dataFile.newMapHeader()
            val indexes: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header)
            indexes[newReferenceId] = null
            header.firstNode = indexes.reference.firstNode
            header.position = indexes.reference.position
            header.recordCount.set(indexes.reference.recordCount.get())
            header
        }
        indexValues[newReferenceId] = indexValue ?: ""
    }

    /**
     * Delete an index key with a record reference
     *
     * @param reference Entity reference
     */
    @Throws(OnyxException::class)
    @Synchronized
    override fun delete(reference: Long) {
        if (reference > 0) {
            val indexValue = indexValues.remove(reference)
            if (indexValue != null) {
                val dataFile = context.getDataFile(descriptor)

                references.computeIfPresent(indexValue) { _, header ->
                    val indexes: DiskMap<Long, Any?> = dataFile.getHashMap(Long::class.java, header!!)
                    indexes.remove(reference)
                    header.firstNode = indexes.reference.firstNode
                    header.position = indexes.reference.position
                    header.recordCount.set(indexes.reference.recordCount.get())
                    header
                }
            }
        }
    }

    /**
     * Find all index references
     *
     * @param indexValue Index value to find values for
     * @return References matching that index value
     */
    @Throws(OnyxException::class)
    override fun findAll(indexValue: Any?): Map<Long, Any?> {
        val header = references[indexValue] ?: return HashMap()
        val dataFile = context.getDataFile(descriptor)

        return dataFile.getHashMap(Long::class.java, header)
    }

    /**
     * Find all index references
     *
     * @return All index references
     */
    @Throws(OnyxException::class)
    override fun findAllValues(): Set<Any> = references.keys

    /**
     * Find all the references above and perhaps equal to the key parameter
     *
     * This has one prerequisite.  You must be using a DiskMatrixHashMap as the storage mechanism.  Otherwise it will not be
     * sorted.
     *
     * @param indexValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeValue Whether to compare above and equal or not.
     * @return A set of record references
     *
     * @throws OnyxException Exception while reading the data structure
     *
     * @since 1.2.0
     */
    @Throws(OnyxException::class)
    override fun findAllAbove(indexValue: Any?, includeValue: Boolean): Set<Long> {
        val allReferences = HashSet<Long>()
        val diskReferences = references.above(indexValue!!, includeValue)

        val dataFile = context.getDataFile(descriptor)

        diskReferences
                .map { references.getWithRecID(it) }
                .map { dataFile.getHashMap<Map<Long, Set<Long>>>(Long::class.java, it!!) }
                .forEach { allReferences.addAll(it.keys) }

        return allReferences
    }

    /**
     * Find all the references blow and perhaps equal to the key parameter
     *
     * This has one prerequisite.  You must be using a DiskMatrixHashMap as the storage mechanism.  Otherwise it will not be
     * sorted.
     *
     * @param indexValue The key to compare.  This must be comparable.  It is only sorted by comparable values
     * @param includeValue Whether to compare below and equal or not.
     * @return A set of record references
     *
     * @throws OnyxException Exception while reading the data structure
     *
     * @since 1.2.0
     */
    @Throws(OnyxException::class)
    override fun findAllBelow(indexValue: Any?, includeValue: Boolean): Set<Long> {
        val allReferences = HashSet<Long>()
        val diskReferences = references.below(indexValue!!, includeValue)
        val dataFile = context.getDataFile(descriptor)
        diskReferences
                .map { references.getWithRecID(it) }
                .map { dataFile.getHashMap<DiskMap<Long, Any?>>(Long::class.java, it!!) }
                .forEach { allReferences.addAll(it.keys) }

        return allReferences
    }

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
    override fun findAllBetween(fromValue: Any?, includeFromValue: Boolean, toValue: Any?, includeToValue: Boolean): Set<Long> {
        val allReferences = HashSet<Long>()
        val diskReferences = references.between(fromValue, includeFromValue, toValue, includeToValue)
        val dataFile = context.getDataFile(descriptor)
        diskReferences
                .map { references.getWithRecID(it) }
                .map { dataFile.getHashMap<DiskMap<Long, Any?>>(Long::class.java, it!!) }
                .forEach { allReferences.addAll(it.keys) }

        return allReferences
    }

    /**
     * ReBuilds an index by iterating through all the values and re-mapping index values
     *
     */
    @Throws(OnyxException::class)
    override fun rebuild() {
        val dataFile = context.getDataFile(descriptor)
        val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(descriptor.identifier!!.type, descriptor.entityClass.name)
        records.entries.forEach {
            val recId = records.getRecID(it.key)
            if (recId > 0) {
                val indexValue = it.value.identifier(context, descriptor)
                if (indexValue != null)
                    save(indexValue, recId, recId)
            }
        }
    }

    /**
     * Clear all index references
     *
     * @since 9/26/2024
     */
    override fun clear() {
        references.clear()
    }
}
