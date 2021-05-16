package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.exception.OnyxException
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.interactors.record.RecordInteractor
import com.onyx.persistence.context.Contexts

/**
 * Created by timothy.osborn on 1/3/15.
 *
 *
 * Scan identifier values
 */
open class IdentifierScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner, RangeScanner {

    override var isBetween: Boolean = false
    override var rangeFrom: Any? = null
    override var rangeTo: Any? = null
    override var fromOperator:QueryCriteriaOperator? = null
    override var toOperator:QueryCriteriaOperator? = null

    /**
     * Full scan with ids
     *
     * @return Identifiers matching criteria
     * @throws OnyxException Cannot scan records
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!
        val recordInteractor = context.getRecordInteractor(descriptor)
        return scan(recordInteractor)
    }

    fun scan(recordInteractor: RecordInteractor, scanExisting:Boolean = false, partitionId:Long = this.partitionId):MutableSet<Reference> {
        val matching = HashSet<Reference>()
        val context = Contexts.get(contextId)!!

        // If it is an in clause
        if (criteria.value is List<*>) {
            (criteria.value as List<*>)
                    .map { Reference(partitionId, recordInteractor.getReferenceId(it!!)) }
                    .filter { it.reference > 0L }
                    .forEach {
                        if(!scanExisting)
                            collector?.collect(it, it.toManagedEntity(context, descriptor))
                        if(collector == null)
                            matching.add(it)
                    }
        } else {
            val values: Set<Long> = if(isBetween) {
                recordInteractor.findAllBetween(rangeFrom, fromOperator === QueryCriteriaOperator.GREATER_THAN_EQUAL, rangeTo, toOperator === QueryCriteriaOperator.LESS_THAN_EQUAL)
            } else {
                when {
                    criteria.operator === QueryCriteriaOperator.GREATER_THAN ->         recordInteractor.findAllAbove(criteria.value!!, false)
                    criteria.operator === QueryCriteriaOperator.GREATER_THAN_EQUAL ->   recordInteractor.findAllAbove(criteria.value!!, true)
                    criteria.operator === QueryCriteriaOperator.LESS_THAN ->            recordInteractor.findAllBelow(criteria.value!!, false)
                    criteria.operator === QueryCriteriaOperator.LESS_THAN_EQUAL ->      recordInteractor.findAllBelow(criteria.value!!, true)
                    else ->                                                             hashSetOf(recordInteractor.getReferenceId(criteria.value!!))
                }
            }

            values.filter { it > 0L }
                    .map { Reference(partitionId, it) }
                    .forEach {
                        if(!scanExisting)
                            collector?.collect(it, it.toManagedEntity(context, descriptor))
                        if(collector == null)
                            matching.add(it)
                    }
        }

        return matching
    }

    /**
     * Scan existing values for identifiers
     *
     * @param existingValues Existing values to check
     * @return Existing values that meed additional criteria
     * @throws OnyxException Cannot scan records
     */
    @Throws(OnyxException::class)
    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        val context = Contexts.get(contextId)!!
        val matching = scan(context.getRecordInteractor(descriptor), true)
        return existingValues.filterTo(HashSet()) {
            if(matching.contains(it)) {
                collector?.collect(it, it.toManagedEntity(context, descriptor))
                return@filterTo (collector == null)
            }
            return@filterTo false
        }
    }
}
