package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.exception.OnyxException
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.extension.common.async
import com.onyx.extension.toManagedEntity
import com.onyx.persistence.context.Contexts

import java.util.*
import java.util.concurrent.Future
import kotlin.collections.HashSet

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Scan a partition for matching index values
 */
class PartitionIndexScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : IndexScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        if(query.partition === QueryPartitionMode.ALL) {
            val context = Contexts.get(contextId)!!
            val units = ArrayList<Future<Set<Reference>>>()
            val entries = context.getAllPartitions(query.entityType!!)

            entries.forEach {
                units.add(
                    async {
                        val partitionDescriptor = context.getDescriptorForEntity(query.entityType, it.value)
                        val indexInteractor = context.getIndexInteractor(partitionDescriptor.indexes[criteria.attribute]!!)
                        scanPartition(indexInteractor, it.index)
                    }
                )
            }

            val results: MutableSet<Reference> = hashSetOf()
            units.forEach {
                results += it.get()
            }
            return existingValues.filterTo(HashSet()) {
                if(results.contains(it)) {
                    collector?.collect(it, it.toManagedEntity(context, descriptor))
                    return@filterTo collector == null
                }
                return@filterTo false
            }
        } else {
            return super.scan(existingValues)
        }
    }

    /**
     * Full Table Scan
     *
     * @return Matching references for criteria
     * @throws OnyxException Cannot scan partition
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!

        if (query.partition === QueryPartitionMode.ALL) {

            val matching = HashSet<Reference>()

            val units = ArrayList<Future<Set<Reference>>>()
            val entries = context.getAllPartitions(query.entityType!!)

            entries.forEach {
                units.add(
                    async {
                        val partitionDescriptor = context.getDescriptorForEntity(query.entityType, it.value)
                        val indexInteractor = context.getIndexInteractor(partitionDescriptor.indexes[criteria.attribute]!!)
                        scanPartition(indexInteractor, it.index)
                    }
                )
            }

            units.forEach {
                val results =  it.get()
                if(collector == null) matching += results
            }
            return matching

        } else {
            val partitionId = context.getPartitionWithValue(query.entityType!!, query.partition)?.index ?: 0L
            if (partitionId == 0L)
                return HashSet()

            val descriptor = context.getDescriptorForEntity(query.entityType, query.partition)
            val indexInteractor = context.getIndexInteractor(descriptor.indexes[criteria.attribute]!!)
            return scanPartition(indexInteractor, partitionId)
        }
    }

    /**
     * Scan indexes
     *
     * @return Matching values meeting criteria
     * @throws OnyxException Cannot scan partition
     */
    @Throws(OnyxException::class)
    @Suppress("UNCHECKED_CAST")
    private fun scanPartition(indexInteractor: IndexInteractor, partitionId: Long): MutableSet<Reference> {
        val matching = HashSet<Reference>()
        val context = Contexts.get(contextId)!!
        val maxCardinality = context.maxCardinality

        if (criteria.value is List<*>)
            (criteria.value as List<Any>).forEach { value ->
                find(value, indexInteractor, partitionId).forEach {
                collector?.collect(it, it.toManagedEntity(context, descriptor))
                if (matching.size > maxCardinality)
                    throw MaxCardinalityExceededException(context.maxCardinality)
                if(collector == null)
                    matching.add(it)
            } }
        else
            find(criteria.value, indexInteractor, partitionId).forEach {
                collector?.collect(it, it.toManagedEntity(context, descriptor))
                if (matching.size > maxCardinality)
                    throw MaxCardinalityExceededException(context.maxCardinality)
                if(collector == null)
                    matching.add(it)
            }

        return matching
    }
}
