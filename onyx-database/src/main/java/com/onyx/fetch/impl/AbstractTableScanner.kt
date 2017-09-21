package com.onyx.fetch.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.MapBuilder
import com.onyx.helpers.PartitionContext
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.util.OffsetField

import java.util.concurrent.Executors

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * This contains the abstract information for a table scanner.
 */
abstract class AbstractTableScanner constructor(protected val criteria: QueryCriteria<*>, protected val classToScan: Class<*>, protected val descriptor: EntityDescriptor, protected val temporaryDataFile: MapBuilder, protected val query: Query, context: SchemaContext, protected var persistenceManager: PersistenceManager) : PartitionContext(context, descriptor) {

    protected var records: DiskMap<Any, IManagedEntity> = context.getDataFile(descriptor).getHashMap(descriptor.entityClass.name, descriptor.identifier!!.loadFactor.toInt())
    protected var partitionId= if(descriptor.hasPartition) context.getPartitionWithValue(classToScan, descriptor.partition!!.partitionValue)!!.primaryKey.toLong() else 0L

    @Deprecated("Use entity reflection")
    var fieldToGrab: OffsetField? = null
    @Deprecated("Use co-routines")
    protected val executorService = Executors.newWorkStealingPool()


    init {

        // Ensure it is not a relationship
        if (!criteria.attribute!!.contains(".")) {
            // Get the reflection field to grab the key to compare
            fieldToGrab = this.descriptor.attributes[criteria.attribute!!]!!.field
        }
    }
}
