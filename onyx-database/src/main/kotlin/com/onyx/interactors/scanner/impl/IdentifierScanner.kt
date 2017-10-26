package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.record.data.Reference
import com.onyx.exception.OnyxException
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.diskmap.factory.DiskMapFactory
import com.onyx.interactors.record.RecordInteractor
import com.onyx.persistence.context.Contexts

/**
 * Created by timothy.osborn on 1/3/15.
 *
 *
 * Scan identifier values
 */
open class IdentifierScanner @Throws(OnyxException::class) constructor(criteria: QueryCriteria, classToScan: Class<*>, descriptor: EntityDescriptor, temporaryDataFile: DiskMapFactory, query: Query, context: SchemaContext, persistenceManager: PersistenceManager) : AbstractTableScanner(criteria, classToScan, descriptor, temporaryDataFile, query, context, persistenceManager), TableScanner {

    /**
     * Full scan with ids
     *
     * @return Identifiers matching criteria
     * @throws OnyxException Cannot scan records
     */
    @Throws(OnyxException::class)
    override fun scan(): MutableMap<Reference, Reference> {
        val context = Contexts.get(contextId)!!
        val recordInteractor = context.getRecordInteractor(descriptor)
        return scan(recordInteractor)
    }

    fun scan(recordInteractor: RecordInteractor, partitionId:Long = this.partitionId):MutableMap<Reference, Reference> {
        val matching = HashMap<Reference, Reference>()

        // If it is an in clause
        if (criteria.value is List<*>) {
            (criteria.value as List<*>)
                    .map { Reference(partitionId, recordInteractor.getReferenceId(it!!)) }
                    .filter { it.reference > 0L }
                    .forEach { matching.put(it, it) }
        } else {
            val values: Set<Long> = when {
                criteria.operator === QueryCriteriaOperator.GREATER_THAN ->         recordInteractor.findAllAbove(criteria.value!!, false)
                criteria.operator === QueryCriteriaOperator.GREATER_THAN_EQUAL ->   recordInteractor.findAllAbove(criteria.value!!, true)
                criteria.operator === QueryCriteriaOperator.LESS_THAN ->            recordInteractor.findAllBelow(criteria.value!!, false)
                criteria.operator === QueryCriteriaOperator.LESS_THAN_EQUAL ->      recordInteractor.findAllBelow(criteria.value!!, true)
                else ->                                                             hashSetOf(recordInteractor.getReferenceId(criteria.value!!))
            }

            values.filter { it > 0L }
                    .map { Reference(partitionId, it) }
                    .forEach { matching.put(it, it) }
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
    override fun scan(existingValues: MutableMap<Reference, Reference>): MutableMap<Reference, Reference> {
        val matching = scan()
        return existingValues.filterTo(HashMap()) { matching.containsKey(it.key) }
    }
}
