package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * This contains the abstract information for a table scanner.
 */
abstract class AbstractTableScanner constructor(protected val criteria: QueryCriteria<*>, classToScan: Class<*>, protected val descriptor: EntityDescriptor, protected val temporaryDataFile: DiskMapFactory, protected val query: Query, context: SchemaContext, protected var persistenceManager: PersistenceManager){
    protected var records: DiskMap<Any, IManagedEntity> = context.getDataFile(descriptor).getHashMap(descriptor.entityClass.name, descriptor.identifier!!.loadFactor.toInt())
    protected var partitionId= if(descriptor.hasPartition) context.getPartitionWithValue(classToScan, descriptor.partition!!.partitionValue)!!.primaryKey.toLong() else 0L
    protected val contextId = context.contextId
}
