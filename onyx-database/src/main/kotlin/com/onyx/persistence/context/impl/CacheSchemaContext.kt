package com.onyx.persistence.context.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DefaultMapBuilder
import com.onyx.diskmap.MapBuilder
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

    /**
     * This method will create a pool of map builders.  They are used to run queries
     * and then be recycled and put back on the queue.
     *
     * @since 1.3.0
     */
    override fun createTemporaryDiskMapPool() {
        for (i in 0..31) {
            val builder = DefaultMapBuilder(location, StoreType.IN_MEMORY, this@CacheSchemaContext, true)
            temporaryDiskMapQueue.add(builder)
            temporaryMaps.add(builder)
        }
    }

    /**
     * Return the corresponding data file for the descriptor
     *
     * @since 1.0.0
     * @param descriptor Record Entity Descriptor
     *
     * @return Data storage mechanism factory
     */
    override fun getDataFile(descriptor: EntityDescriptor): MapBuilder {
        val path = descriptor.fileName + if (descriptor.partition == null) "" else descriptor.partition!!.partitionValue
        return synchronized(dataFiles) { dataFiles.getOrPut(path) { DefaultMapBuilder("$location/$path", StoreType.IN_MEMORY, this@CacheSchemaContext) } }
    }

    /**
     * Create Temporary Map Builder.
     *
     * @return Create new storage mechanism factory
     * @since 1.3.0 Changed to use a pool of map builders.
     * The intent of this is to increase performance.  There was a performance
     * issue with map builders being destroyed invoking the DirectBuffer cleanup.
     * That was not performant.
     */
    override fun createTemporaryMapBuilder(): MapBuilder = temporaryDiskMapQueue.take()

    /**
     * Recycle a temporary map builder so that it may be re-used
     *
     * @param mapBuilder Discarded map builder
     * @since 1.3.0
     */
    override fun releaseMapBuilder(mapBuilder: MapBuilder) {
        mapBuilder.reset()
        temporaryDiskMapQueue.offer(mapBuilder)
    }

}
