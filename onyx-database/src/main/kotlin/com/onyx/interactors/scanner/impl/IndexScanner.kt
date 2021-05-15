package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.exception.OnyxException
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.scanner.TableScanner
import com.onyx.interactors.index.IndexInteractor
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator

/**
 * Created by timothy.osborn on 2/10/15.
 *
 * Scan index values for given criteria
 */
open class IndexScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner, RangeScanner {

    private var indexInteractor: IndexInteractor = context.getIndexInteractor(descriptor.indexes[criteria.attribute]!!)
    override var isBetween: Boolean = false
    override var rangeFrom: Any? = null
    override var rangeTo: Any? = null
    override var fromOperator:QueryCriteriaOperator? = null
    override var toOperator:QueryCriteriaOperator? = null

    /**
     * Scan indexes
     *
     * @return Indexes meeting criteria
     * @throws OnyxException Cannot scan index
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> = scan(false)

    /**
     * Scan indexes
     *
     * @return Indexes meeting criteria
     * @throws OnyxException Cannot scan index
     */
    fun scan(includesExisting:Boolean = false): MutableSet<Reference> {
        val matching = HashSet<Reference>()
        val context = Contexts.get(contextId)!!
        // If it is an in clause
        if (criteria.value is List<*>) {
            (criteria.value as List<*>).forEach { it ->
                find(it).forEach {
                    if(!includesExisting)
                        collector?.collect(it, it.toManagedEntity(context, descriptor))
                    if(collector == null)
                        matching.add(it)
                }
            }
        } else {
            find(criteria.value).forEach {
                if(!includesExisting)
                    collector?.collect(it, it.toManagedEntity(context, descriptor))
                if(collector == null)
                    matching.add(it)
            }
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
    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!
        val matching = scan(true)
        return existingValues.filterTo(HashSet()) {
            if(matching.contains(it)) {
                collector?.collect(it, it.toManagedEntity(context, descriptor))
                return@filterTo collector == null
            }
            return@filterTo false
        }
    }

    /**
     * Find all references within an index matching the value for this query criteria
     * @param indexValue Index value to find references for
     * @return List of Partition References
     *
     * @since 2.0.0
     */
    protected fun find(indexValue:Any?, interactor: IndexInteractor = indexInteractor, partition: Int = partitionId):List<Reference> = if(isBetween) {
        interactor.findAllBetween(rangeFrom, fromOperator === QueryCriteriaOperator.GREATER_THAN_EQUAL, rangeTo, toOperator === QueryCriteriaOperator.LESS_THAN_EQUAL)
    } else {
        when {
            criteria.operator === QueryCriteriaOperator.GREATER_THAN -> interactor.findAllAbove(indexValue, false)
            criteria.operator === QueryCriteriaOperator.GREATER_THAN_EQUAL -> interactor.findAllAbove(indexValue, true)
            criteria.operator === QueryCriteriaOperator.LESS_THAN -> interactor.findAllBelow(indexValue, false)
            criteria.operator === QueryCriteriaOperator.LESS_THAN_EQUAL -> interactor.findAllBelow(indexValue, true)
            else -> interactor.findAll(indexValue).keys
        }
    }.map {
        Reference(partition, it)
    }
}
