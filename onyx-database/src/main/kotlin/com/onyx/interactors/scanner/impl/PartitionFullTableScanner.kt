package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
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

/**
 * Created by timothy.osborn on 1/3/15.
 *
 *
 * It can either scan the entire table or a subset of index values
 */
class PartitionFullTableScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : FullTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    private var systemEntity: SystemEntity = context.getSystemEntityByName(query.entityType!!.name)!!

    /**
     * Scan records with existing values
     *
     * @param records Existing values to check for criteria
     * @return Existing values that match the criteria
     * @throws OnyxException Cannot scan partition
     */
    @Throws(OnyxException::class)
    private fun scanPartition(records: DiskMap<Any, IManagedEntity>, partitionId: Long): MutableSet<Reference> {
        val matching = HashSet<Reference>()
        val context = Contexts.get(contextId)!!

        @Suppress("UNCHECKED_CAST")
        records.entries.forEach {
            val entry = it as AbstractIterableSkipList<Any, IManagedEntity>.SkipListEntry<Any?, IManagedEntity>
            val reference = Reference(partitionId, entry.node?.position ?: 0)
            if(entry.node != null && query.meetsCriteria(entry.value!!, reference, context, descriptor)) {
                collector?.collect(reference, entry.value)
                if(collector == null)
                    matching.add(reference)
            }
        }

        records.clearCache()

        return matching
    }

    /**
     * Full Table Scan
     *
     * @return References matching criteria
     * @throws OnyxException Cannot scan partition
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!

        if (query.partition === QueryPartitionMode.ALL) {
            val matching = HashSet<Reference>()
            val units = ArrayList<Future<Set<Reference>>>()

            systemEntity.partition!!.entries.forEach {
                units.add(
                    async {
                        val partitionDescriptor = context.getDescriptorForEntity(query.entityType, it.value)
                        val dataFile = context.getDataFile(partitionDescriptor)
                        val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(descriptor.identifier!!.type, partitionDescriptor.entityClass.name)
                        scanPartition(records, it.index)
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
            if(partitionId == 0L) // Partition does not exist, lets do a full scan of default partition
                return super.scan()

            val partitionDescriptor = context.getDescriptorForEntity(query.entityType, query.partition)
            val dataFile = context.getDataFile(partitionDescriptor)
            val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(descriptor.identifier!!.type, partitionDescriptor.entityClass.name)
            return scanPartition(records, partitionId)
        }
    }
}