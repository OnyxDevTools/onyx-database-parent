package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.interactors.query.QueryCollector
import com.onyx.interactors.query.QueryCollectorFactory
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import java.lang.ref.WeakReference

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * This contains the abstract information for a table scanner.
 */
abstract class AbstractTableScanner constructor(protected val criteria: QueryCriteria, classToScan: Class<*>, protected var descriptor: EntityDescriptor, protected val query: Query, context: SchemaContext, protected var persistenceManager: PersistenceManager){

    private val contextReference: WeakReference<SchemaContext>

    protected val context: SchemaContext
        get() = contextReference.get()!!

    protected val records: DiskMap<Any, IManagedEntity>
        get() = context.getDataFile(descriptor).getHashMap(descriptor.identifier!!.type, descriptor.entityClass.name)

    protected var partitionId= if(descriptor.hasPartition) context.getPartitionWithValue(classToScan, descriptor.partition!!.partitionValue)!!.primaryKey.toLong() else 0L
    protected val contextId = context.contextId

    var collector:QueryCollector<Any>? = null

    init {
        contextReference = WeakReference(context)
    }

    var isLast:Boolean = false
        set(value) {
            field = value
            if(value) {
                collector = QueryCollectorFactory.create(Contexts.get(contextId)!!, descriptor, query)
            }
        }

}
