package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.exception.OnyxException
import com.onyx.exception.InvalidQueryException
import com.onyx.extension.*
import com.onyx.extension.common.instance
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.ScannerFactory
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.context.Contexts
import kotlin.collections.HashMap

/**
 * Created by timothy.osborn on 1/3/15.
 *
 * Scan relationships for matching criteria
 */
class RelationshipScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, temporaryDataFile: DiskMapFactory, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager), TableScanner {

    /**
     * Full Table get all relationships
     *
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableMap<Reference, Reference> {

        val startingPoint = HashMap<Reference, Reference>()
        val context = Contexts.get(contextId)!!

        // We do not support querying relationships by all partitions.  That would be horribly inefficient
        if (this.query.partition === QueryPartitionMode.ALL) throw InvalidQueryException()

        var partitionId = 0L

        if (this.descriptor.hasPartition) {
            val temporaryManagedEntity: IManagedEntity = descriptor.entityClass.instance()
            temporaryManagedEntity[context, descriptor, descriptor.partition!!.name] = query.partition.toString()
            partitionId = temporaryManagedEntity.partitionId(context, descriptor)
        }

        records.references.map { Reference(partitionId, it.position) }.forEach { startingPoint.put(it, it) }

        return scan(startingPoint)
    }

    /**
     * Full Scan with existing values
     *
     * @param existingValues Existing values to check criteria
     * @return filtered map of results matching additional criteria
     * @throws OnyxException Cannot scan relationship values
     */
    @Throws(OnyxException::class)
    override fun scan(existingValues: MutableMap<Reference, Reference>): MutableMap<Reference, Reference> {
        val context = Contexts.get(contextId)!!

        // Get the next scanner because we are not at the end of the line.  Otherwise, we would not have gotten to this place
        val tableScanner = ScannerFactory.getFullTableScanner(context, criteria, descriptor.entityClass, temporaryDataFile, query, persistenceManager)

        // Sweet, lets get the scanner.  Note, this very well can be recursive, but sooner or later it will get to the
        // other scanners
        return tableScanner.scan(existingValues)
    }

}
