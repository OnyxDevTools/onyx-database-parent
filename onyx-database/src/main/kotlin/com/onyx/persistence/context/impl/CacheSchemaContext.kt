package com.onyx.persistence.context.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.factory.impl.DefaultDiskMapFactory
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.diskmap.store.StoreType

/**
 * The purpose of this class is to resolve all the the metadata, storage mechanism,  and modeling regarding the structure of the database.
 *
 * In this case it is an in-memory data storage.  No data will be persisted.
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 */
class CacheSchemaContext(contextId: String, location: String) : DefaultSchemaContext(contextId, location) {

    override var encryptDatabase: Boolean = false
        get() = false

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

}
