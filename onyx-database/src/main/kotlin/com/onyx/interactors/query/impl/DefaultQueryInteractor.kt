package com.onyx.interactors.query.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.record.descriptorForReference
import com.onyx.interactors.record.withRecordMutationLock
import com.onyx.interactors.scanner.ScannerFactory
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.*
import com.onyx.extension.*
import com.onyx.extension.common.compare
import com.onyx.interactors.query.QueryCollector
import com.onyx.interactors.query.QueryCollectorFactory
import com.onyx.persistence.context.Contexts
import com.onyx.interactors.query.QueryInteractor
import com.onyx.interactors.scanner.impl.*
import java.util.IdentityHashMap

/**
 * Created by timothy.osborn on 3/5/15.
 *
 *
 * Controls how to query a partition
 */
class DefaultQueryInteractor internal constructor(
    private var descriptor: EntityDescriptor,
    private var persistenceManager: PersistenceManager,
    context: SchemaContext,
    private val scannerSelection: ScannerSelection
) : QueryInteractor {

    constructor(
        descriptor: EntityDescriptor,
        persistenceManager: PersistenceManager,
        context: SchemaContext
    ) : this(descriptor, persistenceManager, context, ScannerSelection.AUTOMATIC)

    /**
     * Selects scanners without bypassing the normal query orchestration and collection pipeline.
     * [FULL_TABLE] exists for correctness comparisons and diagnostic benchmarks.
     */
    internal enum class ScannerSelection {
        AUTOMATIC,
        FULL_TABLE
    }

    private val contextId = context.contextId

    /**
     * Find object ids that match the criteria
     *
     * @param query Query Criteria
     * @return References matching query criteria
     * @since 1.3.0 This has been refactored to remove the logic for meeting criteria.  That has
     * been moved to CompareUtil
     */
    override fun <T> getReferencesForQuery(query: Query):QueryCollector<T> {
        query.fullTextScores = null
        query.vectorSearchMatches = null
        if (query.isTerminated) {
            val collector = QueryCollectorFactory.create<T>(Contexts.get(contextId)!!, descriptor, query)
            collector.finalizeResults()
            return collector
        }

        val requestedCriteria = query.criteria!!
        val requestedSearch = query.getAllCriteria().singleOrNull {
            it.operator == QueryCriteriaOperator.SEARCH
        }
        val vectorGroupNegation = requestedCriteria.hasGroupNegation() &&
            ScannerFactory.isVectorManagedCriteriaTree(descriptor, requestedCriteria)
        val executionCriteria = if (vectorGroupNegation) {
            requestedCriteria.withoutGroupNegations()
        } else {
            requestedCriteria
        }
        requestedSearch?.let { searchCriteria ->
            val searchScanner = ScannerFactory.getScannerForQueryCriteria(
                Contexts.get(contextId)!!,
                searchCriteria,
                query.entityType!!,
                query,
                persistenceManager,
            )
            val admitted = searchScanner.scan()
            query.vectorSearchMatches = IdentityHashMap<QueryCriteria, Set<Reference>>().apply {
                put(searchCriteria, admitted)
                // De Morgan normalization creates a new criteria tree. Cache the same one-shot
                // admission under its SEARCH node so execution cannot embed and scan it again.
                executionCriteria.findSearchCriteria()?.let { put(it, admitted) }
            }
        }
        val pair = getReferencesForCriteria<T>(
            query,
            executionCriteria,
            null,
            forceFullScan = requestedCriteria.isNot && !vectorGroupNegation
        )
        val references = pair.first.orderedBySearchScore(query)
        var collector = pair.second
        if(collector == null) {
            collector = QueryCollectorFactory.create(Contexts.get(contextId)!!, descriptor, query)
            collector.setReferenceSet(references)
        }
        collector.finalizeResults()
        query.resultsCount = collector.getNumberOfResults()

        return collector
    }

    /**
     * Delete record with reference ids
     *
     * @param records References to delete
     * @param query   Query object
     * @return Number of entities deleted
     */
    override fun deleteRecordsWithReferences(records: List<Reference>, query: Query): Int {

        val context = Contexts.get(contextId)!!
        var deleteCount = 0

        records.forEach { reference ->
            val sourceDescriptor = context.descriptorForReference(
                reference,
                query.entityType!!,
                descriptor,
            )
            val deleted = context.withRecordMutationLock(sourceDescriptor) {
                val entity = reference.toManagedEntity(context, query.entityType!!, descriptor)
                    ?: return@withRecordMutationLock false
                entity.deleteAllIndexes(context, reference.reference, sourceDescriptor)
                entity.deleteRelationships(context, descriptor = sourceDescriptor)
                entity.recordInteractor(context, sourceDescriptor).delete(entity)
                true
            }
            if (deleted) {
                deleteCount++
            }
        }

        return deleteCount
    }

    /**
     * Update records
     *
     * @param query   Query information containing update values
     * @param records Entity references as a result of the query
     * @return how many entities were updated
     * @throws OnyxException Cannot update entity
     */
    override fun updateRecordsWithReferences(query: Query, records: List<Reference>): Int {

        val context = Contexts.get(contextId)!!
        var updateCount = 0

        records.forEach { reference ->
            val sourceDescriptor = context.descriptorForReference(
                reference,
                query.entityType!!,
                descriptor,
            )
            val partitionUpdate = query.updates.firstOrNull {
                it.fieldName == sourceDescriptor.partition?.name
            }

            if (partitionUpdate == null) {
                context.withRecordMutationLock(sourceDescriptor) {
                    val entity = reference.toManagedEntity(context, query.entityType!!, descriptor)
                        ?: return@withRecordMutationLock
                    query.updates.forEach {
                        entity.set(
                            context = context,
                            descriptor = sourceDescriptor,
                            name = it.fieldName!!,
                            value = it.value,
                        )
                    }
                    val putResult = entity.save(context, sourceDescriptor)
                    entity.saveIndexes(
                        context,
                        if (putResult.isInsert) 0L else reference.reference,
                        putResult.recordId,
                        sourceDescriptor,
                        previousEntity = putResult.previousValue as? IManagedEntity,
                    )
                    context.queryCacheInteractor.updateCachedQueryResultsForEntity(
                        entity,
                        sourceDescriptor,
                        entity.reference(putResult.recordId, context, sourceDescriptor),
                        QueryListenerEvent.UPDATE,
                    )
                    updateCount++
                }
                return@forEach
            }

            // A partition move is two independently consistent store mutations, not a cross-store
            // CAS. Remove the source row+indexes together, release its monitor before relationship
            // traversal, then add the target row+indexes under the target monitor.
            var movingEntity: IManagedEntity? = null
            var moved = false
            var completedInSource = false
            context.withRecordMutationLock(sourceDescriptor) {
                val entity = reference.toManagedEntity(context, query.entityType!!, descriptor)
                    ?: return@withRecordMutationLock
                movingEntity = entity
                moved = !entity.get<Any?>(
                    context,
                    sourceDescriptor,
                    partitionUpdate.fieldName!!,
                ).compare(partitionUpdate.value)
                if (moved) {
                    entity.deleteAllIndexes(context, reference.reference, sourceDescriptor)
                    entity.deleteRelationships(context, descriptor = sourceDescriptor)
                    entity.recordInteractor(context, sourceDescriptor).delete(entity)
                } else {
                    query.updates.forEach {
                        entity.set(
                            context = context,
                            descriptor = sourceDescriptor,
                            name = it.fieldName!!,
                            value = it.value,
                        )
                    }
                    val putResult = entity.save(context, sourceDescriptor)
                    entity.saveIndexes(
                        context,
                        if (putResult.isInsert) 0L else reference.reference,
                        putResult.recordId,
                        sourceDescriptor,
                        previousEntity = putResult.previousValue as? IManagedEntity,
                    )
                    context.queryCacheInteractor.updateCachedQueryResultsForEntity(
                        entity,
                        sourceDescriptor,
                        entity.reference(putResult.recordId, context, sourceDescriptor),
                        QueryListenerEvent.UPDATE,
                    )
                    updateCount++
                    completedInSource = true
                }
            }
            if (completedInSource) return@forEach
            val entity = movingEntity ?: return@forEach
            query.updates.forEach {
                entity.set(
                    context = context,
                    descriptor = sourceDescriptor,
                    name = it.fieldName!!,
                    value = it.value,
                )
            }
            val targetDescriptor = context.getDescriptorForEntity(entity)
            context.withRecordMutationLock(targetDescriptor) {
                val putResult = entity.save(context, targetDescriptor)
                entity.saveIndexes(
                    context,
                    if (moved || putResult.isInsert) 0L else reference.reference,
                    putResult.recordId,
                    targetDescriptor,
                    previousEntity = putResult.previousValue as? IManagedEntity,
                )
                context.queryCacheInteractor.updateCachedQueryResultsForEntity(
                    entity,
                    targetDescriptor,
                    entity.reference(putResult.recordId, context, targetDescriptor),
                    QueryListenerEvent.UPDATE,
                )
                updateCount++
            }
        }

        return updateCount
    }

    /**
     * Get the count for a query.  This is used to get the count without actually executing the query.  It is lighter weight
     * than the entire query and in most cases will use the longSize on the disk map data structure if it is
     * for the entire table.
     *
     * @param query Query to identify count for
     * @return The number of records matching query criterion
     * @throws OnyxException Exception occurred while executing query
     * @since 1.3.0 Added as enhancement #71
     */
    @Throws(OnyxException::class)
    override fun getCountForQuery(query: Query): Long {
        val context = Contexts.get(contextId)!!
        if (query.isDefaultQuery(descriptor)) {

            when (QueryPartitionMode.ALL) {
                query.partition -> {
                    var resultCount = 0L

                    val entries = context.getAllPartitions(query.entityType!!)

                    entries.forEach {
                        val partitionDescriptor = context.getDescriptorForEntity(query.entityType, it.value)
                        val dataFile = context.getDataFile(partitionDescriptor)
                        val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(descriptor.identifier!!.type, partitionDescriptor.entityClass.name)
                        resultCount += records.longSize()
                    }

                    return resultCount
                }
                else -> {
                    val partitionDescriptor = context.getDescriptorForEntity(query.entityType, query.partition)
                    val dataFile = context.getDataFile(partitionDescriptor)
                    val records = dataFile.getHashMap<DiskMap<Any, IManagedEntity>>(descriptor.identifier!!.type, partitionDescriptor.entityClass.name)
                    return records.longSize()
                }
            }
        } else {
            val results = this.getReferencesForQuery<Nothing>(query)
            return results.getNumberOfResults().toLong()
        }
    }

    /**
     * Get references matching a specific criteria
     *
     * @param query Parent query
     * @param criteria Criteria to get references for
     * @param existingReferences Existing matching references from previous criteria.  Null if this is the first criteria.
     * @param forceFullScan Force a full table scan.
     *
     * @return Filtered references matching criteria
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getReferencesForCriteria(query: Query, criteria: QueryCriteria, existingReferences: MutableSet<Reference>?, forceFullScan: Boolean, collect:Boolean = true):Pair<MutableSet<Reference>, QueryCollector<T>?> {
        val context = Contexts.get(contextId)!!
        // Ensure query is still valid
        if (query.isTerminated) {
            return Pair(HashSet(), null)
        }

        val scanner = if (forceFullScan || scannerSelection == ScannerSelection.FULL_TABLE) {
            ScannerFactory.getFullTableScanner(context, criteria, query.entityType!!, query, persistenceManager)
        } else {
            ScannerFactory.getScannerForQueryCriteria(context, criteria, query.entityType!!, query, persistenceManager)
        }

        if(collect &&
                !query.usesImplicitSearchScoreOrder() &&
                (scanner is FullTableScanner || (
                    criteria == query.getAllCriteria().last()
                    && !criteria.isNot
                    && !criteria.flip
                    && !criteria.isOr))){
            scanner.isLast = true
        }

        // Check to see if it is a range criteria
        var subCriteriaIsRange = false
        if((criteria.operator === QueryCriteriaOperator.GREATER_THAN_EQUAL || criteria.operator === QueryCriteriaOperator.GREATER_THAN)
                && (criteria.subCriteria.isNotEmpty() && criteria.subCriteria.first().isAnd && !criteria.subCriteria.first().isNot && (criteria.subCriteria.first().operator === QueryCriteriaOperator.LESS_THAN || criteria.subCriteria.first().operator === QueryCriteriaOperator.LESS_THAN_EQUAL))
                && criteria.attribute == criteria.subCriteria.first().attribute
                && scanner is RangeScanner) {
            scanner.isBetween = true
            scanner.rangeFrom = criteria.value
            scanner.rangeTo = criteria.subCriteria.first().value
            scanner.toOperator = criteria.subCriteria.first().operator
            scanner.fromOperator = criteria.operator
            subCriteriaIsRange = true
        } else if((criteria.operator === QueryCriteriaOperator.LESS_THAN || criteria.operator === QueryCriteriaOperator.LESS_THAN_EQUAL)
                && (criteria.subCriteria.isNotEmpty() && criteria.subCriteria.first().isAnd && !criteria.subCriteria.first().isNot && (criteria.subCriteria.first().operator === QueryCriteriaOperator.GREATER_THAN || criteria.subCriteria.first().operator === QueryCriteriaOperator.GREATER_THAN_EQUAL))
                && criteria.attribute == criteria.subCriteria.first().attribute
                && scanner is RangeScanner) {
            scanner.isBetween = true
            scanner.rangeTo = criteria.value
            scanner.rangeFrom = criteria.subCriteria.first().value
            scanner.fromOperator = criteria.subCriteria.first().operator
            scanner.toOperator = criteria.operator
            subCriteriaIsRange = true
        }


        // Scan for records
        // If there are existing references, use those to narrow it down.  Otherwise
        // start from a clean slate

        val prefilteredConjunct = if (existingReferences == null) {
            (scanner as? VectorIndexScanner)?.selectiveConjunctForDomain()
        } else {
            null
        }
        val prefilteredReferences = prefilteredConjunct?.let {
            getReferencesForCriteria<T>(
                query,
                it,
                existingReferences = null,
                forceFullScan = false,
                collect = false
            ).first
        }

        val criteriaResults: MutableSet<Reference> = if (existingReferences == null && prefilteredReferences == null) {
            scanner.scan()
        } else {
            if (criteria.isOr || criteria.isNot) {
                scanner.scan()
            } else {
                scanner.scan(prefilteredReferences ?: requireNotNull(existingReferences))
            }
        }

        if(scanner !is FullTableScanner) {
            // Go through and ensure all the sub criteria is met
            criteria.subCriteria.forEachIndexed { index, subCriteriaObject ->
                if (subCriteriaObject === prefilteredConjunct) return@forEachIndexed
                if(index == 0 && subCriteriaIsRange)
                    return@forEachIndexed
                val subCriteriaResults = getReferencesForCriteria<T>(query, subCriteriaObject, criteriaResults,
                    forceFullScan = false,
                    collect = false
                )
                aggregateFilteredReferences(subCriteriaObject, criteriaResults, subCriteriaResults.first)
            }
        }

        return Pair(criteriaResults, scanner.collector as QueryCollector<T>?)
    }

    /**
     * Used to correlate existing reference sets with the criteria met from
     * a single criteria.
     *
     * @param criteria Root Criteria
     * @param totalResults Results from previous scan iterations
     * @param criteriaResults Criteria results used to aggregate a contrived list
     */
    private fun aggregateFilteredReferences(criteria: QueryCriteria, totalResults: MutableSet<Reference>, criteriaResults: MutableSet<Reference>) {
        @Suppress("ConvertArgumentToSet") // Nope, not more performant
        when {
            criteria.flip ->  {totalResults.clear(); totalResults += criteriaResults}
            criteria.isOr ->  totalResults += criteriaResults
            criteria.isAnd -> totalResults -= totalResults.filter { !criteriaResults.contains(it) }
        }
    }

    /** Search relevance is the implicit order unless the caller supplied an explicit order. */
    private fun MutableSet<Reference>.orderedBySearchScore(query: Query): MutableSet<Reference> {
        if (!query.queryOrders.isNullOrEmpty()) return this
        val scores = query.fullTextScores?.takeIf(Map<Reference, Float>::isNotEmpty) ?: return this
        return sortedWith(
            compareByDescending<Reference> { scores[it] ?: Float.NEGATIVE_INFINITY }
                .thenBy(Reference::partition)
                .thenBy(Reference::reference)
        )
            .toCollection(LinkedHashSet())
    }

    private fun Query.usesImplicitSearchScoreOrder(): Boolean =
        queryOrders.isNullOrEmpty() && getAllCriteria().any {
            it.operator in setOf(
                QueryCriteriaOperator.MATCHES,
                QueryCriteriaOperator.LIKE,
                QueryCriteriaOperator.SEARCH,
                QueryCriteriaOperator.SEARCH_CANDIDATES,
                QueryCriteriaOperator.HNSW_CANDIDATES
            ) &&
                it.attribute == Query.FULL_TEXT_ATTRIBUTE
        }

    /** Pushes group negation to vector-backed leaves using De Morgan's laws. */
    private fun QueryCriteria.hasGroupNegation(): Boolean =
        isNot || subCriteria.any { !it.flip && it.hasGroupNegation() }

    private fun QueryCriteria.findSearchCriteria(): QueryCriteria? {
        if (operator == QueryCriteriaOperator.SEARCH) return this
        subCriteria.forEach { child -> child.findSearchCriteria()?.let { return it } }
        return null
    }

    private fun QueryCriteria.withoutGroupNegations(parentNegated: Boolean = false): QueryCriteria {
        val negateNode = parentNegated.xor(isNot)
        val normalized = QueryCriteria(
            requireNotNull(attribute),
            if (negateNode) requireNotNull(operator).inverse else requireNotNull(operator),
            value
        ).also { it.level = level }

        subCriteria.filterNot(QueryCriteria::flip).forEach { child ->
            val normalizedChild = child.withoutGroupNegations(negateNode)
            normalizedChild.isAnd = if (negateNode) child.isOr else child.isAnd
            normalizedChild.isOr = if (negateNode) child.isAnd else child.isOr
            normalizedChild.parentCriteria = normalized
            normalized.subCriteria += normalizedChild
        }
        return normalized
    }

}
