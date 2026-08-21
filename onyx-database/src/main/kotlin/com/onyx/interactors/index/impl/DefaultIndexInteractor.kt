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
        require(oldReferenceId <= 0L) {
            "The old index value is required when updating an index without a reverse mapping"
        }
        saveIndexValue(null, indexValue, oldReferenceId, newReferenceId)
    }

    @Throws(OnyxException::class)
    @Synchronized
    override fun save(
        oldIndexValue: Any?,
        indexValue: Any?,
        oldReferenceId: Long,
        newReferenceId: Long
    ) {
        saveIndexValue(oldIndexValue, indexValue, oldReferenceId, newReferenceId)
    }

    private fun saveIndexValue(
        oldIndexValue: Any?,
        indexValue: Any?,
        oldReferenceId: Long,
        newReferenceId: Long
    ) {
        val persistedValue = oldIndexValue?.let(::normalize)
        val normalizedValue = indexValue?.let(::normalize)

        if (oldReferenceId > 0L && oldReferenceId == newReferenceId &&
            persistedValue != null && normalizedValue != null &&
            valuesHaveSameIndexOrder(persistedValue, normalizedValue)
        ) {
            return
        }

        if (persistedValue != null && oldReferenceId > 0L) {
            references.remove(persistedValue, oldReferenceId)
        }
        if (normalizedValue != null && newReferenceId > 0L) {
            references.add(normalizedValue, newReferenceId)
        }
    }

    /** Legacy insert-only API cannot identify a posting for deletion without its indexed value. */
    @Throws(OnyxException::class)
    @Synchronized
    override fun delete(reference: Long) {
        throw IllegalArgumentException(
            "The index value is required when deleting an index entry without a reverse mapping"
        )
    }

    @Throws(OnyxException::class)
    @Synchronized
    override fun delete(indexValue: Any?, reference: Long) {
        if (indexValue == null || reference <= 0L) return
        references.remove(normalize(indexValue), reference)
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

    /** Rebuild the posting tree from authoritative entity records. */
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
        records.forEachReference { recordId, entity ->
            if (recordId > 0L) {
                val indexValue = entity.get<Any?>(context, descriptor, indexDescriptor.name)
                saveIndexValue(null, indexValue, 0L, recordId)
            }
        }
    }

    /** Clear the active index. */
    @Synchronized
    override fun clear() {
        references.clear()
    }

    override fun shutdown() {
        // Default indexes are owned by the entity data file.
    }

    override fun deleteResources() {
        // Default indexes are owned by the entity data file.
    }
}
