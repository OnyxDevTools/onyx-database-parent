package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.recordInteractor
import com.onyx.entity.SystemEntity
import com.onyx.exception.OnyxException
import com.onyx.scan.PartitionReference
import com.onyx.interactors.scanner.TableScanner
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
import kotlin.collections.HashMap

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * This scans a partition for matching identifiers
 */
class PartitionIdentifierScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria<*>, classToScan: Class<*>, descriptor: EntityDescriptor, temporaryDataFile: MapBuilder, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : IdentifierScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager), TableScanner {

    private var systemEntity: SystemEntity = context.getSystemEntityByName(query.entityType!!.name)!!

    /**
     * Scan existing values for identifiers
     *
     * @return Matching identifiers within partition
     * @throws OnyxException Cannot scan partition
     */
    @Throws(OnyxException::class)
    override fun scan(): Map<PartitionReference, PartitionReference> {
        val context = Contexts.get(contextId)!!
        val matching = HashMap<PartitionReference,PartitionReference>()

        if (query.partition === QueryPartitionMode.ALL) {
            val units = ArrayList<Deferred<Map<PartitionReference,PartitionReference>>>()
            systemEntity.partition!!.entries.forEach {
                units.add(
                    async {
                        val partitionDescriptor = context.getDescriptorForEntity(query.entityType, it.value)
                        val recordInteractor = context.getRecordInteractor(partitionDescriptor)
                        scan(recordInteractor, it.index)
                    }
                )
            }

            runBlocking {
                units.forEach { matching += it.await() }
            }
        } else {

            val partitionId = context.getPartitionWithValue(query.entityType!!, query.partition)?.index ?: 0L
            if (partitionId == 0L)
                return HashMap()

            val descriptor = context.getDescriptorForEntity(query.entityType, query.partition)
            matching += scan(descriptor.recordInteractor(), partitionId)
        }

        return matching
    }
}
