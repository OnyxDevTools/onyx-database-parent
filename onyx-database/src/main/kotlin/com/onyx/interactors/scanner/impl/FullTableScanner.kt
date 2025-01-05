package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.impl.base.skiplist.AbstractIterableSkipList
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.exception.OnyxException
import com.onyx.extension.*
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * It can either scan the entire table or a subset of index values
 */
open class FullTableScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    /**
     * Full Table Scan
     *
     * @return Map of identifiers.  The key is the partition reference and the value is the reference within file.
     * @throws OnyxException Query exception while trying to scan elements
     * @since 1.3.0 Simplified to check all criteria rather than only a single criteria
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> {
        val matching = HashSet<Reference>()
        val context = Contexts.get(contextId)!!
        val maxCardinality = context.maxCardinality

        @Suppress("UNCHECKED_CAST")
        records.entries.forEach {
            val entry = it as AbstractIterableSkipList<Any, IManagedEntity>.SkipListEntry<Any?, IManagedEntity>
            val reference = Reference(partitionId, entry.node?.position ?: 0)
            if(entry.node != null && query.meetsCriteria(entry.value!!, reference, context, descriptor)) {
                collector?.collect(reference, entry.value)
                if (matching.size > maxCardinality)
                    throw MaxCardinalityExceededException(context.maxCardinality)
                if(collector == null)
                    matching.add(reference)
            }
        }

        return matching
    }

    /**
     * Scan records with existing values
     *
     * @param existingValues Existing values to scan from
     * @return Remaining values that meet the criteria
     * @throws OnyxException Exception while scanning entity records
     * @since 1.3.0 Simplified to check all criteria rather than only a single criteria
     */
    @Throws(OnyxException::class)
    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!
        return existingValues.filterTo(HashSet()) {
            val entity = it.toManagedEntity(context, descriptor)
            val meetsCriteria = query.meetsCriteria(entity, it, context, descriptor)
            if(meetsCriteria)
                collector?.collect(it, entity)
            (collector == null) && meetsCriteria
        }
    }
}