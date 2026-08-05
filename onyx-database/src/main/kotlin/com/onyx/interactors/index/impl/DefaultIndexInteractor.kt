package com.onyx.interactors.index.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.IndexPostingMap
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.exception.OnyxException
import com.onyx.extension.common.ClassMetadata
import com.onyx.extension.common.canBeCastToPrimitive
import com.onyx.extension.common.castTo
import com.onyx.extension.common.forceCompare
import com.onyx.extension.common.long
import com.onyx.extension.get
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.QueryCriteriaOperator
import java.lang.ref.WeakReference
import java.util.Date
import java.util.HashMap
import java.util.HashSet

/** Controls the persistent, sorted secondary index for one entity attribute. */
open class DefaultIndexInteractor @Throws(OnyxException::class) constructor(
    private val descriptor: EntityDescriptor,
    override val indexDescriptor: IndexDescriptor,
    context: SchemaContext
) : IndexInteractor {

    private val contextReference = WeakReference(context)

    private val context: SchemaContext
        get() = contextReference.get()!!

    private val dataFile: DiskMapFactory
        get() = context.getDataFile(descriptor)

    /** One posting BTree ordered by (index value, entity record reference). */
    private val references: IndexPostingMap
        get() = dataFile.getIndexMap(indexDescriptor.type, mapBaseName)

    /** Reverse lookup remains necessary because mutation callers provide only the old record reference. */
    protected open val indexValues: DiskMap<Long, Any>
        get() = dataFile.getHashMap(Long::class.java, mapBaseName + INDEX_VALUES_SUFFIX)

    private val mapBaseName: String
        get() = descriptor.entityClass.name + indexDescriptor.name

    /**
     * Save an index value and its entity record reference.
     *
     * A null value is deliberately not indexed. IS NULL queries use a table
     * scan, and omitting null prevents collisions with valid zero/empty values.
     */
    @Throws(OnyxException::class)
    @Synchronized
    override fun save(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) {
        saveIndexValue(indexValue, oldReferenceId, newReferenceId)
    }

    private fun saveIndexValue(indexValue: Any?, oldReferenceId: Long, newReferenceId: Long) {
        if (oldReferenceId > 0L && oldReferenceId == newReferenceId) {
            updateIndexValue(indexValue, newReferenceId)
            return
        }

        if (oldReferenceId > 0L) deleteIndexValue(oldReferenceId)
        if (indexValue == null || newReferenceId <= 0L) return

        val normalizedValue = normalize(indexValue)
        references.add(normalizedValue, newReferenceId)
        indexValues[newReferenceId] = normalizedValue
    }

    /** Update a stable entity reference without deleting and recreating its reverse-map entry. */
    private fun updateIndexValue(indexValue: Any?, reference: Long) {
        val persistedValue = indexValues[reference]?.let(::normalize)
        val normalizedValue = indexValue?.let(::normalize)

        if (persistedValue == null) {
            if (normalizedValue != null) {
                references.add(normalizedValue, reference)
                indexValues[reference] = normalizedValue
            }
            return
        }

        if (normalizedValue == null) {
            indexValues.remove(reference)
            references.remove(persistedValue, reference)
            return
        }

        if (valuesHaveSameIndexOrder(persistedValue, normalizedValue)) return

        references.remove(persistedValue, reference)
        references.add(normalizedValue, reference)
        indexValues[reference] = normalizedValue
    }

    /** Delete an index posting using the persisted old value from the reverse lookup. */
    @Throws(OnyxException::class)
    @Synchronized
    override fun delete(reference: Long) {
        deleteIndexValue(reference)
    }

    private fun deleteIndexValue(reference: Long) {
        if (reference <= 0L) return
        val indexValue = indexValues.remove(reference) ?: return
        references.remove(indexValue, reference)
    }

    /** Find every record whose indexed value exactly matches [indexValue]. */
    @Throws(OnyxException::class)
    override fun findAll(indexValue: Any?): Map<Long, Any?> {
        if (indexValue == null) return emptyMap()

        val matches = HashMap<Long, Any?>()
        visitReferences(indexValue, Long.MIN_VALUE, true, indexValue, Long.MAX_VALUE, true) {
            matches[it] = null
        }
        return matches
    }

    /** Return the distinct, currently active values represented by this index. */
    @Throws(OnyxException::class)
    override fun findAllValues(): Set<Any> {
        val values = HashSet<Any>()
        references.forEachDistinctValue(values::add)
        return values
    }

    /** Find references above the supplied indexed value. */
    @Throws(OnyxException::class)
    override fun findAllAbove(indexValue: Any?, includeValue: Boolean): Set<Long> {
        if (indexValue == null) return emptySet()

        val matches = HashSet<Long>()
        val fromRecordId = if (includeValue) Long.MIN_VALUE else Long.MAX_VALUE
        visitReferences(indexValue, fromRecordId, includeValue, null, 0L, false, matches::add)
        return matches
    }

    /** Find references below the supplied indexed value. */
    @Throws(OnyxException::class)
    override fun findAllBelow(indexValue: Any?, includeValue: Boolean): Set<Long> {
        if (indexValue == null) return emptySet()

        val matches = HashSet<Long>()
        val toRecordId = if (includeValue) Long.MAX_VALUE else Long.MIN_VALUE
        visitReferences(null, 0L, false, indexValue, toRecordId, includeValue, matches::add)
        return matches
    }

    /** Find references between two indexed values with independent endpoint inclusion. */
    override fun findAllBetween(
        fromValue: Any?,
        includeFromValue: Boolean,
        toValue: Any?,
        includeToValue: Boolean
    ): Set<Long> {
        if (fromValue == null || toValue == null) return emptySet()

        val matches = HashSet<Long>()
        val fromRecordId = if (includeFromValue) Long.MIN_VALUE else Long.MAX_VALUE
        val toRecordId = if (includeToValue) Long.MAX_VALUE else Long.MIN_VALUE
        visitReferences(
            fromValue,
            fromRecordId,
            includeFromValue,
            toValue,
            toRecordId,
            includeToValue,
            matches::add
        )
        return matches
    }

    private fun visitReferences(
        fromValue: Any?,
        fromRecordId: Long,
        includeFrom: Boolean,
        toValue: Any?,
        toRecordId: Long,
        includeTo: Boolean,
        action: (Long) -> Unit
    ) {
        references.forEachRecordIdInRange(
            fromValue?.let(::normalize),
            fromRecordId,
            includeFrom,
            toValue?.let(::normalize),
            toRecordId,
            includeTo,
            action
        )
    }

    /** Preserve the declared index type before encoding the native posting key. */
    private fun normalize(indexValue: Any): Any =
        indexValue.castTo(indexDescriptor.type) ?: indexValue

    /** Match the posting tree's value comparison, including compareTo-equivalent object values. */
    @Suppress("UNCHECKED_CAST")
    private fun valuesHaveSameIndexOrder(first: Any, second: Any): Boolean = when (indexDescriptor.type) {
        ClassMetadata.FLOAT_TYPE, ClassMetadata.FLOAT_PRIMITIVE_TYPE ->
            java.lang.Float.compare(first as Float, second as Float) == 0
        ClassMetadata.DOUBLE_TYPE, ClassMetadata.DOUBLE_PRIMITIVE_TYPE ->
            java.lang.Double.compare(first as Double, second as Double) == 0
        Date::class.java -> (first as Date).time == (second as Date).time
        else -> if (indexDescriptor.type.canBeCastToPrimitive()) {
            first.long() == second.long()
        } else {
            if (first === second || first == second) {
                true
            } else {
                try {
                    (first as Comparable<Any?>).compareTo(second) == 0
                } catch (_: Exception) {
                    first.forceCompare(second, QueryCriteriaOperator.EQUAL)
                }
            }
        }
    }

    /** Rebuild the flat and reverse mappings from authoritative entity records. */
    @Throws(OnyxException::class)
    @Synchronized
    override fun rebuild() {
        rebuildIndex()
    }

    private fun rebuildIndex() {
        val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(
            descriptor.identifier!!.type,
            descriptor.entityClass.name
        )

        references.clear()
        indexValues.clear()
        records.forEachReference { recordId, entity ->
            if (recordId > 0L) {
                val indexValue = entity.get<Any?>(context, descriptor, indexDescriptor.name)
                saveIndexValue(indexValue, 0L, recordId)
            }
        }
    }

    /** Clear the active index. */
    @Synchronized
    override fun clear() {
        references.clear()
        indexValues.clear()
    }

    override fun shutdown() {
        // Default indexes are owned by the entity data file.
    }

    override fun deleteResources() {
        // Default indexes are owned by the entity data file.
    }

    private companion object {
        const val INDEX_VALUES_SUFFIX = "indexValues"
    }
}
