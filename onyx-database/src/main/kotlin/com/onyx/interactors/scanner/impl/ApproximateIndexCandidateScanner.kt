package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.annotations.values.IndexType
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.query.resolveApproximateIndexCandidateQuery

/** Executes the opt-in, physically bounded `CANDIDATES` secondary-index operation. */
class ApproximateIndexCandidateScanner(
    criteria: QueryCriteria,
    classToScan: Class<*>,
    descriptor: EntityDescriptor,
    query: Query,
    context: SchemaContext,
    persistenceManager: PersistenceManager
) : AbstractTableScanner(criteria, classToScan, descriptor, query, context, persistenceManager),
    TableScanner {

    private val candidateQuery = resolveApproximateIndexCandidateQuery(criteria.value)

    init {
        require(criteria.operator == QueryCriteriaOperator.CANDIDATES) {
            "ApproximateIndexCandidateScanner requires the CANDIDATES operator"
        }
        require(!criteria.isNot && !criteria.flip) {
            "Approximate CANDIDATES criteria cannot be negated"
        }
    }

    override fun scan(): MutableSet<Reference> = scanCandidates(null)

    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> =
        scanCandidates(existingValues)

    private fun scanCandidates(existingValues: Set<Reference>?): MutableSet<Reference> {
        val currentContext = Contexts.get(contextId)!!
        val state = ScanState()

        if (!descriptor.hasPartition) {
            scanDescriptor(descriptor, partitionId, existingValues, state)
            return state.matching
        }

        require(query.partition != QueryPartitionMode.ALL) {
            "CANDIDATES requires one concrete partition for partitioned entities"
        }

        val partition = currentContext.getPartitionWithValue(
            requireNotNull(query.entityType),
            query.partition
        ) ?: return state.matching
        val partitionDescriptor = currentContext.getDescriptorForEntity(query.entityType, query.partition)
        scanDescriptor(partitionDescriptor, partition.index, existingValues, state)
        return state.matching
    }

    private fun scanDescriptor(
        partitionDescriptor: EntityDescriptor,
        concretePartitionId: Long,
        existingValues: Set<Reference>?,
        state: ScanState
    ) {
        val remaining = candidateQuery.maxCandidates - state.visits
        if (remaining <= 0) return
        val attribute = requireNotNull(criteria.attribute)
        val indexDescriptor = partitionDescriptor.indexes[attribute]
            ?: throw IllegalArgumentException(
                "CANDIDATES requires an ordinary secondary index on '$attribute'"
            )
        require(indexDescriptor.indexType == IndexType.DEFAULT) {
            "CANDIDATES supports ordinary secondary indexes only; '$attribute' uses ${indexDescriptor.indexType}"
        }
        val currentContext = Contexts.get(contextId)!!
        val interactor = currentContext.getIndexInteractor(indexDescriptor)
        val visits = interactor.visitApproximateCandidates(
            candidateQuery.values.filterNotNull(),
            remaining
        ) { recordId ->
            val reference = Reference(concretePartitionId, recordId)
            if ((existingValues == null || reference in existingValues) && state.admitted.add(reference)) {
                if (collector == null) {
                    state.matching += reference
                    if (state.matching.size > currentContext.maxCardinality) {
                        throw MaxCardinalityExceededException(currentContext.maxCardinality)
                    }
                } else {
                    collector?.collect(
                        reference,
                        reference.toManagedEntity(currentContext, partitionDescriptor)
                    )
                }
            }
            true
        }
        require(visits in 0..remaining) {
            "Index interactor exceeded the CANDIDATES posting-visit budget"
        }
        state.visits += visits
    }

    private class ScanState {
        var visits: Int = 0
        val admitted: MutableSet<Reference> = hashSetOf()
        val matching: LinkedHashSet<Reference> = linkedSetOf()
    }
}
