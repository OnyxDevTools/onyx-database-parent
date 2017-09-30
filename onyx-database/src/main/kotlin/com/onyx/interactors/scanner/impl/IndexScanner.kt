package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.exception.OnyxException
import com.onyx.interactors.scanner.TableScanner
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.diskmap.factory.DiskMapFactory
import kotlin.collections.HashMap

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Scan index values for given criteria
 */
open class IndexScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria<*>, classToScan: Class<*>, descriptor: EntityDescriptor, temporaryDataFile: DiskMapFactory, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager), TableScanner {

    private var indexInteractor: IndexInteractor = context.getIndexInteractor(descriptor.indexes[criteria.attribute]!!)

    /**
     * Scan indexes
     *
     * @return Indexes meeting criteria
     * @throws OnyxException Cannot scan index
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableMap<Reference, Reference> {
        val matching = HashMap<Reference, Reference>()

        // If it is an in clause
        if (criteria.value is List<*>) {
            (criteria.value as List<*>).forEach { find(it).forEach { matching.put(it, it) } }
        } else {
            find(criteria.value).forEach { matching.put(it, it) }
        }

        return matching
    }

    /**
     * Scan indexes that are within the existing values
     *
     * @param existingValues Existing values to check
     * @return Existing values matching criteria
     * @throws OnyxException Cannot scan index
     */
    @Throws(OnyxException::class)
    override fun scan(existingValues: MutableMap<Reference, Reference>): MutableMap<Reference, Reference> {
        val matching = scan()
        return existingValues.filterTo(HashMap()) { matching.containsKey(it.key) }
    }

    /**
     * Find all references within an index matching the value for this query criteria
     * @param indexValue Index value to find references for
     * @return List of Partition References
     *
     * @since 2.0.0
     */
    protected fun find(indexValue:Any?, interactor: IndexInteractor = indexInteractor, partition: Long = partitionId):List<Reference> = when {
        criteria.operator === QueryCriteriaOperator.GREATER_THAN ->         interactor.findAllAbove(indexValue, false)
        criteria.operator === QueryCriteriaOperator.GREATER_THAN_EQUAL ->   interactor.findAllAbove(indexValue, true)
        criteria.operator === QueryCriteriaOperator.LESS_THAN ->            interactor.findAllBelow(indexValue, false)
        criteria.operator === QueryCriteriaOperator.LESS_THAN_EQUAL ->      interactor.findAllBelow(indexValue, true)
        else ->                                                             interactor.findAll(indexValue).keys
    }.map { Reference(partition, it) }
}
