package com.onyx.persistence.context.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.diskmap.store.StoreType
import com.onyx.entity.SystemPartitionEntry
import com.onyx.exception.OnyxException
import com.onyx.lang.map.OptimisticLockingMap
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator

/**
 * The purpose of this class is to resolve all the metadata, storage mechanism,  and modeling regarding the structure of the database.
 *
 * In this case it is an in-memory data storage.  No data will be persisted.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 */
class CacheSchemaContext(contextId: String, location: String) : DefaultSchemaContext(contextId, location) {

    @Suppress("SuspiciousVarProperty")
    override var encryptDatabase: Boolean = false
        get() = false

    override val dataFiles: MutableMap<String, DiskMapFactory> = OptimisticLockingMap(HashMap())
    override val partitionDataFiles: MutableMap<Long, DiskMapFactory> = OptimisticLockingMap(HashMap())

    /**
     * Return the corresponding data file for the descriptor
     *
     * @since 1.0.0
     * @param descriptor Record Entity Descriptor
     *
     * @return Data storage mechanism factory
     */
    override fun getDataFile(descriptor: EntityDescriptor): DiskMapFactory {
        val path = descriptor.fileName + if (descriptor.partition == null) "" else descriptor.partition!!.partitionValue
        return dataFiles.getOrPut(path) { DefaultDiskMapFactory("$location/$path", StoreType.IN_MEMORY, this@CacheSchemaContext) }
    }

    @Throws(OnyxException::class)
    override fun getPartitionDataFile(descriptor: EntityDescriptor, partitionId: Long): DiskMapFactory {

        if (partitionId == 0L) {
            return getDataFile(descriptor)
        }

        return partitionDataFiles.getOrPut(partitionId) {
            val query = Query(SystemPartitionEntry::class.java, QueryCriteria("index", QueryCriteriaOperator.EQUAL, partitionId))
            val partitions = serializedPersistenceManager.executeQuery<SystemPartitionEntry>(query)
            val partition = partitions[0]

            return@getOrPut getDataFile(getDescriptorForEntity(descriptor.entityClass, partition.value))
        }
    }
}
