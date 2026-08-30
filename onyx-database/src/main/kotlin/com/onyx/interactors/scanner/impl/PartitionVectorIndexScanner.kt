package com.onyx.interactors.scanner.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.OnyxException
import com.onyx.exception.SearchEmbeddingUnavailableException
import com.onyx.interactors.index.impl.FingerprintIndexInteractor
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.scanner.TableScanner
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.query.SearchMode
import com.onyx.persistence.query.SearchQuery
import com.onyx.persistence.query.resolveSearchQuery
import com.onyx.vector.SearchEmbedding

/** Executes [VectorIndexScanner] routing across every concrete entity partition. */
class PartitionVectorIndexScanner @Throws(OnyxException::class) constructor(
    criteria: QueryCriteria,
    classToScan: Class<*>,
    descriptor: EntityDescriptor,
    query: Query,
    context: SchemaContext,
    persistenceManager: PersistenceManager
) : VectorIndexScanner(criteria, classToScan, descriptor, query, context, persistenceManager), TableScanner {

    override fun scan(): MutableSet<Reference> {
        if (query.partition != QueryPartitionMode.ALL) return super.scan()
        val (matching, descriptors) = scanAllPartitions()
        return finishScan(matching) { reference -> descriptors.getValue(reference.partition) }
    }

    override fun scan(existingValues: Set<Reference>): MutableSet<Reference> {
        if (query.partition != QueryPartitionMode.ALL) return super.scan(existingValues)
        val (matching, descriptors) = scanAllPartitions(existingValues)
        return finishScan(matching) { reference -> descriptors.getValue(reference.partition) }
    }

    private fun scanAllPartitions(
        existingValues: Set<Reference>? = null
    ): Pair<LinkedHashSet<Reference>, Map<Long, EntityDescriptor>> {
        val currentContext = Contexts.get(contextId)!!
        val partitions = currentContext.getAllPartitions(requireNotNull(query.entityType))
            .sortedBy { it.index }
        val descriptors = LinkedHashMap<Long, EntityDescriptor>()
        partitions.forEach { partition ->
            val partitionDescriptor = currentContext.getDescriptorForEntity(query.entityType, partition.value)
            descriptors[partition.index] = partitionDescriptor
        }

        if (criteria.operator == QueryCriteriaOperator.SEARCH) {
            val cachedAdmission = query.vectorSearchMatches?.get(criteria)
            if (cachedAdmission != null) {
                val restricted = cachedAdmission.asSequence()
                    .filter { it.partition in descriptors }
                    .filter { existingValues == null || it in existingValues }
                    .toCollection(LinkedHashSet())
                return restricted to descriptors
            }
            return executePartitionedSearch(
                partitions.map { it.index },
                descriptors,
                existingValues,
            ) to descriptors
        }

        val matching = LinkedHashSet<Reference>()
        partitions.forEach { partition ->
            val partitionDescriptor = descriptors.getValue(partition.index)
            matching += scanDescriptor(partitionDescriptor, partition.index, existingValues)
        }
        return matching to descriptors
    }

    /**
     * Divides one SEARCH admission budget across every concrete partition, then merges and ranks
     * those independently routed candidates as one result set. Partition indexes provide a stable
     * allocation/tie-break order regardless of metadata query order.
     */
    private fun executePartitionedSearch(
        partitionIds: List<Long>,
        descriptors: Map<Long, EntityDescriptor>,
        existingValues: Set<Reference>?,
    ): LinkedHashSet<Reference> {
        val search = resolveSearchQuery(criteria.value)
        val minimumPerPartition = if (search.mode == SearchMode.HYBRID) 2 else 1
        val requiredCandidates = partitionIds.size.toLong() * minimumPerPartition
        require(requiredCandidates <= search.maxCandidates) {
            "SEARCH maxCandidates=${search.maxCandidates} cannot cover all ${partitionIds.size} " +
                "partitions of ${requireNotNull(query.entityType).name}; " +
                "${search.mode.name.lowercase()} search requires at least $requiredCandidates " +
                "candidates or an explicit partition"
        }
        val semanticEmbedding: SearchEmbedding? = if (
            search.mode == SearchMode.SEMANTIC || search.mode == SearchMode.HYBRID
        ) {
            val entityType = requireNotNull(query.entityType)
            val provider = persistenceManager.searchEmbeddingProvider
                ?: throw SearchEmbeddingUnavailableException(
                    "${search.mode.name.lowercase()} search requires a SearchEmbeddingProvider " +
                        "configured on the database server"
                )
            if (!provider.supports(entityType)) {
                throw SearchEmbeddingUnavailableException(
                    "SearchEmbeddingProvider does not support ${entityType.name}",
                )
            }
            provider.embed(search.text, entityType)
        } else {
            null
        }
        if (partitionIds.isEmpty()) return LinkedHashSet()

        val baseBudget = search.maxCandidates / partitionIds.size
        val remainder = search.maxCandidates % partitionIds.size
        val combinedScores = LinkedHashMap<Reference, Float>(search.maxCandidates)
        val currentContext = Contexts.get(contextId)!!

        partitionIds.forEachIndexed { index, partitionId ->
            val partitionBudget = baseBudget + if (index < remainder) 1 else 0
            val partitionSearch = SearchQuery(
                text = search.text,
                mode = search.mode,
                match = search.match,
                minScore = search.minScore,
                maxCandidates = partitionBudget,
            )
            val partitionDescriptor = descriptors.getValue(partitionId)
            val indexDescriptor = requireNotNull(
                partitionDescriptor.indexes[VectorManagedEntity.REPRESENTATION_FIELD]
            )
            val interactor = currentContext.getIndexInteractor(
                indexDescriptor,
            ) as FingerprintIndexInteractor
            val restrictedIds = existingValues?.asSequence()
                ?.filter { it.partition == partitionId }
                ?.mapTo(LinkedHashSet(), Reference::reference)

            executeSearch(
                partitionSearch,
                partitionDescriptor,
                interactor,
                restrictedIds,
                semanticEmbedding,
            ).forEach { (recordId, score) ->
                combinedScores[Reference(partitionId, recordId)] = score
            }
        }

        val globallyRanked = combinedScores.entries.asSequence()
            .sortedWith(
                compareByDescending<Map.Entry<Reference, Float>> { it.value }
                    .thenBy { it.key.partition }
                    .thenBy { it.key.reference }
            )
            .take(search.maxCandidates)
            .associateTo(LinkedHashMap()) { it.key to it.value }
        mergeScores(globallyRanked)
        return globallyRanked.keys.toCollection(LinkedHashSet())
    }
}
