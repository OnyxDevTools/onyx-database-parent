package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.entity.SystemEntity
import com.onyx.exception.OnyxException
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.diskmap.MapBuilder
import com.onyx.extension.common.async
import com.onyx.persistence.context.Contexts
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.runBlocking

import java.util.*
import kotlin.collections.HashMap

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Scan a partition for matching index values
 */
class PartitionIndexScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria<*>, classToScan: Class<*>, descriptor: EntityDescriptor, temporaryDataFile: MapBuilder, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : IndexScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager), TableScanner {

    private var systemEntity: SystemEntity = context.getSystemEntityByName(query.entityType!!.name)!!

    /**
     * Full Table Scan
     *
     * @return Matching references for criteria
     * @throws OnyxException Cannot scan partition
     */
    @Throws(OnyxException::class)
    override fun scan(): Map<Reference, Reference> {
        val context = Contexts.get(contextId)!!

        if (query.partition === QueryPartitionMode.ALL) {

            val matching = HashMap<Reference, Reference>()

            val units = ArrayList<Deferred<Map<Reference, Reference>>>()
            systemEntity.partition!!.entries.forEach {
                units.add(
                    async {
                        val partitionDescriptor = context.getDescriptorForEntity(query.entityType, it.value)
                        val indexInteractor = context.getIndexInteractor(partitionDescriptor.indexes[criteria.attribute]!!)
                        scanPartition(indexInteractor, it.index)
                    }
                )
            }

            runBlocking {
                units.forEach { matching += it.await() }
            }

            return matching

        } else {
            val partitionId = context.getPartitionWithValue(query.entityType!!, query.partition)?.index ?: 0L
            if (partitionId == 0L)
                return HashMap()

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
    private fun scanPartition(indexInteractor: IndexInteractor, partitionId: Long): Map<Reference, Reference> {
        val matching = HashMap<Reference, Reference>()

        if (criteria.value is List<*>)
            (criteria.value as List<Any>).forEach { find(it, indexInteractor, partitionId).forEach { matching.put(it, it) } }
        else
            find(criteria.value, indexInteractor, partitionId).forEach { matching[it] = it }

        return matching
    }
}
