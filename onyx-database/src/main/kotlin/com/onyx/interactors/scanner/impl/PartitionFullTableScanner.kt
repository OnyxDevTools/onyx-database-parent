package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.diskmap.impl.base.skiplist.AbstractIterableSkipList
import com.onyx.entity.SystemEntity
import com.onyx.exception.OnyxException
import com.onyx.extension.common.async
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.extension.meetsCriteria
import com.onyx.persistence.context.Contexts
import java.util.concurrent.Future
import kotlin.collections.HashMap

/**
 * Created by timothy.osborn on 1/3/15.
 *
 *
 * It can either scan the entire table or a subset of index values
 */
class PartitionFullTableScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, temporaryDataFile: DiskMapFactory, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : FullTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager), TableScanner {

    private var systemEntity: SystemEntity = context.getSystemEntityByName(query.entityType!!.name)!!

    /**
     * Scan records with existing values
     *
     * @param records Existing values to check for criteria
     * @return Existing values that match the criteria
     * @throws OnyxException Cannot scan partition
     */
    @Throws(OnyxException::class)
    private fun scanPartition(records: DiskMap<Any, IManagedEntity>, partitionId: Long): MutableMap<Reference, Reference> {
        val matching = HashMap<Reference, Reference>()
        val context = Contexts.get(contextId)!!

        @Suppress("UNCHECKED_CAST")
        records.entries.filter {
            val entry = it as AbstractIterableSkipList<Any, IManagedEntity>.SkipListEntry<Any, IManagedEntity>
            entry.node != null && query.meetsCriteria(entry.value!!, Reference(partitionId, entry.node!!.position), context, descriptor)
        }.forEach {
            val entry = it as AbstractIterableSkipList<Any, IManagedEntity>.SkipListEntry<Any, IManagedEntity>
            val reference = Reference(partitionId, entry.node!!.position)
            matching.put(reference, reference)
        }

        return matching
    }

    /**
     * Full Table Scan
     *
     * @return References matching criteria
     * @throws OnyxException Cannot scan partition
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableMap<Reference, Reference> {
        val context = Contexts.get(contextId)!!

        if (query.partition === QueryPartitionMode.ALL) {
            val matching = HashMap<Reference, Reference>()
            val units = ArrayList<Future<Map<Reference, Reference>>>()

            systemEntity.partition!!.entries.forEach {
                units.add(
                    async {
                        val partitionDescriptor = context.getDescriptorForEntity(query.entityType, it.value)
                        val dataFile = context.getDataFile(partitionDescriptor)
                        val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(partitionDescriptor.entityClass.name, partitionDescriptor.identifier!!.loadFactor.toInt())
                        scanPartition(records, it.index)
                    }
                )
            }

            units.forEach { matching += it.get() }
            return matching
        } else {
            val partitionId = context.getPartitionWithValue(query.entityType!!, query.partition)?.index ?: 0L
            if(partitionId == 0L) // Partition does not exist, lets do a full scan of default partition
                return super.scan()

            val partitionDescriptor = context.getDescriptorForEntity(query.entityType, query.partition)
            val dataFile = context.getDataFile(partitionDescriptor)
            val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(partitionDescriptor.entityClass.name, partitionDescriptor.identifier!!.loadFactor.toInt())
            return scanPartition(records, partitionId)
        }
    }
}