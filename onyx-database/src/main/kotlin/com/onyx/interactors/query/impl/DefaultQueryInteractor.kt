package com.onyx.interactors.query.impl

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.impl.DiskBTreeMap
import com.onyx.interactors.record.data.Reference
import com.onyx.interactors.record.descriptorForReference
import com.onyx.interactors.scanner.ScannerFactory
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.values.IndexType
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.*
import com.onyx.extension.*
import com.onyx.extension.common.compare
import com.onyx.interactors.query.QueryCollector
import com.onyx.interactors.query.QueryCollectorFactory
import com.onyx.interactors.query.impl.collectors.OrderedPageQueryCollector
import com.onyx.persistence.context.Contexts
import com.onyx.interactors.query.QueryInteractor
import com.onyx.interactors.scanner.impl.*
import java.util.IdentityHashMap
import java.util.concurrent.ExecutionException

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
    override fun <T> getReferencesForQuery(query: Query): QueryCollector<T> =
        query.withPreparedMemberships { collectReferencesForQuery<T>(query) }

    private fun <T> collectReferencesForQuery(query: Query): QueryCollector<T> {
        query.fullTextScores = null
        query.vectorSearchMatches = null
        query.approximateIndexCandidateMatches = null
        if (query.isTerminated) {
            val collector = QueryCollectorFactory.create<T>(Contexts.get(contextId)!!, descriptor, query)
            collector.finalizeResults()
            return collector
        }

        getPrimaryKeyPage<T>(query)?.let { return it }
        if (scannerSelection == ScannerSelection.AUTOMATIC) {
            SecondaryIndexPageQueryPlanner.collect(query, descriptor, Contexts.get(contextId)!!)?.let {
                @Suppress("UNCHECKED_CAST")
                return it as QueryCollector<T>
            }
        }

        val requestedCriteria = query.criteria!!
        val allCriteria = query.getAllCriteria()
        val requestedSearch = allCriteria.singleOrNull {
            it.operator == QueryCriteriaOperator.SEARCH
        }
        val requestedApproximateCandidates = allCriteria.singleOrNull {
            it.operator == QueryCriteriaOperator.CANDIDATES
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

        val approximateCandidateDomain = requestedApproximateCandidates
            ?.takeIf { allCriteria.size > 1 }
            ?.let { candidateCriteria ->
                val candidateScanner = ScannerFactory.getScannerForQueryCriteria(
                    Contexts.get(contextId)!!,
                    candidateCriteria,
                    query.entityType!!,
                    query,
                    persistenceManager,
                )
                candidateScanner.scan().also { admitted ->
                    // Admission happens exactly once, before any structured predicate. This
                    // makes an AND tree order-independent and preserves the physical visit cap.
                    query.approximateIndexCandidateMatches = admitted
                }
            }
        val pair = getReferencesForCriteria<T>(
            query,
            executionCriteria,
            approximateCandidateDomain?.toCollection(linkedSetOf()),
            // FullTableScanner.scan(existing) evaluates the complete criteria tree only over
            // the admitted references; despite its name, it does not traverse the table here.
            forceFullScan = approximateCandidateDomain != null ||
                requestedCriteria.isNot && !vectorGroupNegation
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

    /** The primary tree already supplies the requested order for an unfiltered, uncached page. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrimaryKeyPage(query: Query): QueryCollector<T>? {
        if (scannerSelection != ScannerSelection.AUTOMATIC || descriptor.hasPartition ||
            query.cache || query.changeListener != null || query.isUpdateOrDelete || query.maxResults <= 0 ||
            !query.selections.isNullOrEmpty() || !query.groupBy.isNullOrEmpty() || query.functions().isNotEmpty()
        ) return null

        val identifier = descriptor.identifier ?: return null
        val order = query.queryOrders?.singleOrNull() ?: return null
        if (!order.isAscending || order.attribute != identifier.name) return null
        if (!identifier.type.isPrimitive && !Comparable::class.java.isAssignableFrom(identifier.type)) return null

        // Validation represents an omitted WHERE clause as `identifier != null`. Negated or
        // composed variants must still evaluate their predicates through the ordinary scanners.
        val criteria = query.criteria ?: return null
        if (!query.isDefaultQuery(descriptor) || criteria.isNot || criteria.flip || criteria.isOr ||
            criteria.subCriteria.isNotEmpty()
        ) return null
        if (!descriptor.hasStoredAttributeOrder(identifier.name)) return null

        val context = Contexts.get(contextId)!!
        val records = context.getDataFile(descriptor)
            .getHashMap<DiskMap<Any, IManagedEntity>>(identifier.type, descriptor.entityClass.name)
            as? DiskBTreeMap<Any, IManagedEntity> ?: return null
        val collector = OrderedPageQueryCollector(query, context, descriptor)
        records.visitAscendingReferencePage(query.firstRow.coerceAtLeast(0), query.maxResults, collector::setTotalCount) { recordId ->
            collector.collect(Reference(0L, recordId), if (query.isLazy) null else records.getWithRecID(recordId))
        }
        collector.finalizeResults()
        query.resultsCount = collector.getNumberOfResults()
        return collector as QueryCollector<T>
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
            val entity = reference.toManagedEntity(context, query.entityType!!, descriptor)
                ?: return@forEach
            entity.deleteAllIndexes(context, reference.reference, sourceDescriptor)
            entity.deleteRelationships(context, descriptor = sourceDescriptor)
            entity.recordInteractor(context, sourceDescriptor).delete(entity)
            deleteCount++
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
                val entity = reference.toManagedEntity(context, query.entityType!!, descriptor)
                    ?: return@forEach
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
                return@forEach
            }

            val entity = reference.toManagedEntity(context, query.entityType!!, descriptor)
                ?: return@forEach
            val moved = !entity.get<Any?>(
                context,
                sourceDescriptor,
                partitionUpdate.fieldName!!,
            ).compare(partitionUpdate.value)
            if (moved) {
                entity.deleteAllIndexes(context, reference.reference, sourceDescriptor)
                entity.deleteRelationships(context, descriptor = sourceDescriptor)
                entity.recordInteractor(context, sourceDescriptor).delete(entity)
            }
            query.updates.forEach {
                entity.set(
                    context = context,
                    descriptor = sourceDescriptor,
                    name = it.fieldName!!,
                    value = it.value,
                )
            }
            val targetDescriptor = if (moved) context.getDescriptorForEntity(entity) else sourceDescriptor
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
            getIndexedCount(query, context)?.let { return it }
            val results = this.getReferencesForQuery<Nothing>(query)
            return results.getNumberOfResults().toLong()
        }
    }

    /** Count an exact scalar posting route without hydrating or retaining its matching entities. */
    private fun getIndexedCount(query: Query, context: SchemaContext): Long? {
        if (scannerSelection != ScannerSelection.AUTOMATIC || query.isTerminated || query.isUpdateOrDelete ||
            !query.selections.isNullOrEmpty() || !query.groupBy.isNullOrEmpty() || query.functions().isNotEmpty()
        ) return null
        if (VectorManagedEntity::class.java.isAssignableFrom(descriptor.entityClass)) return null
        // Computed and relationship orders still need the collector's attribute evaluation and validation.
        if (query.queryOrders?.any { it.attribute !in descriptor.attributes } == true) return null

        val criteria = query.criteria ?: return null
        if (criteria.operator != QueryCriteriaOperator.EQUAL || criteria.subCriteria.isNotEmpty() ||
            criteria.isNot || criteria.flip || criteria.isOr ||
            criteria.attribute == descriptor.identifier?.name || !criteria.hasExactScalarComparison(descriptor)
        ) return null
        val indexDescriptor = descriptor.indexes[criteria.attribute]
            ?.takeIf { it.indexType == IndexType.DEFAULT } ?: return null
        val indexValues = listOf(requireNotNull(criteria.value))

        val partitions = when {
            !descriptor.hasPartition -> listOf(descriptor)
            query.partition === QueryPartitionMode.ALL -> context.getAllPartitions(descriptor.entityClass).map {
                context.getDescriptorForEntity(descriptor.entityClass, it.value)
            }
            context.getPartitionWithValue(descriptor.entityClass, query.partition) == null -> emptyList()
            else -> listOf(context.getDescriptorForEntity(descriptor.entityClass, query.partition))
        }

        val maxCardinality = context.maxCardinality
        var count = 0L
        for (partitionDescriptor in partitions) {
            val index = context.getIndexInteractor(partitionDescriptor.indexes.getValue(indexDescriptor.name))
            try {
                index.visitExactPostings(indexValues) {
                    // Preserve the collector's limit across all partitions, even for a paginated query.
                    count++
                    count <= maxCardinality
                }
            } catch (_: UnsupportedOperationException) {
                // Custom index interactors may only implement the original materializing lookup API.
                return null
            }
            if (count > maxCardinality) throw MaxCardinalityExceededException(maxCardinality)
        }

        query.fullTextScores = null
        query.vectorSearchMatches = null
        query.approximateIndexCandidateMatches = null
        query.resultsCount = count.toInt()
        return count
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

        // Evaluate the complete query only over a selective seed. Indexed roots can use a
        // bounded identifier lookup instead of materializing broad secondary-index postings.
        if ((scanner is FullTableScanner || scanner is IndexScanner || scanner is IdentifierScanner) &&
            criteria === query.criteria &&
            existingReferences == null && !forceFullScan &&
            scannerSelection == ScannerSelection.AUTOMATIC && !query.isUpdateOrDelete
        ) {
            val candidates = indexedConjunctReferences(
                query,
                context,
                requireIdentifierSeed = scanner !is FullTableScanner
            )
            if (candidates != null) {
                val candidateScanner = if (scanner is FullTableScanner) scanner else {
                    ScannerFactory.getFullTableScanner(context, criteria, query.entityType!!, query, persistenceManager)
                        .apply { isLast = collect && !query.usesImplicitSearchScoreOrder() }
                }
                return Pair(candidateScanner.scan(candidates), candidateScanner.collector as QueryCollector<T>?)
            }

            if (scanner is IndexScanner) {
                SecondaryIndexQueryPlanner.findReferences(query, descriptor, context)?.let {
                    // These references satisfy every predicate through native index comparisons.
                    // The ordinary collector still owns hydration, ordering, counts and paging.
                    return Pair(it, null)
                }
            }
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
     * Exact equality routes only: range indexes omit nulls, and mixed-type predicates can
     * coerce stored values differently from index keys. Keep those queries on their existing
     * path, along with disjunctions, negated groups, and bounded search admission.
     */
    private fun indexedConjunctReferences(
        query: Query,
        context: SchemaContext,
        requireIdentifierSeed: Boolean
    ): MutableSet<Reference>? {
        if (VectorManagedEntity::class.java.isAssignableFrom(query.entityType!!)) return null
        val root = query.criteria ?: return null
        if (root.subCriteria.isEmpty() || !root.isScalarConjunction()) return null

        val targetDescriptor = context.getDescriptorForEntity(
            query.entityType,
            if (query.partition === QueryPartitionMode.ALL) "" else query.partition
        )
        if (targetDescriptor.hasPartition && query.partition !== QueryPartitionMode.ALL &&
            context.getPartitionWithValue(query.entityType!!, query.partition) == null
        ) return null

        val allCriteria = query.getAllCriteria()
        val indexedCriteria = if (requireIdentifierSeed) {
            // Index ordering and entity equality can differ even for identical operand types
            // (for example BigDecimal scales). Only replace known equivalent comparisons.
            if (allCriteria.any { !it.hasExactScalarComparison(targetDescriptor) }) return null
            val identifier = targetDescriptor.identifier?.name ?: return null
            allCriteria.filter { candidate ->
                candidate.attribute == identifier &&
                    (candidate.operator == QueryCriteriaOperator.EQUAL ||
                        (candidate.value as List<*>).size <= MAX_IDENTIFIER_CONJUNCT_CANDIDATES)
            }.minByOrNull { candidate ->
                if (candidate.operator == QueryCriteriaOperator.EQUAL) 1 else (candidate.value as List<*>).size
            }
        } else allCriteria.mapNotNull { candidate ->
            val isIdentifier = candidate.attribute == targetDescriptor.identifier?.name
            val type = if (isIdentifier) {
                targetDescriptor.identifier!!.type
            } else {
                targetDescriptor.indexes[candidate.attribute]
                    ?.takeIf { it.indexType == IndexType.DEFAULT }?.type ?: return@mapNotNull null
            }.kotlin.javaObjectType

            // Collection predicates have separate comparison semantics; in particular the
            // scalar scanners interpret any List value as an IN lookup, even for EQUAL.
            if (type.isArray || Iterable::class.java.isAssignableFrom(type) ||
                Map::class.java.isAssignableFrom(type) || Pair::class.java.isAssignableFrom(type)
            ) return@mapNotNull null

            val priority = when (candidate.operator) {
                QueryCriteriaOperator.EQUAL -> {
                    if (candidate.value?.javaClass != type) return@mapNotNull null
                    if (isIdentifier) 0 else 1
                }
                QueryCriteriaOperator.IN -> {
                    val values = candidate.value as? List<*> ?: return@mapNotNull null
                    if (values.any { it == null || it.javaClass != type }) return@mapNotNull null
                    2
                }
                else -> return@mapNotNull null
            }
            priority to candidate
        }.minByOrNull { it.first }?.second
        if (indexedCriteria == null) return null

        val indexScanner = ScannerFactory.getScannerForQueryCriteria(
            context, indexedCriteria, query.entityType!!, query, persistenceManager
        )
        if (indexScanner !is IndexScanner && indexScanner !is IdentifierScanner) return null

        // Leave collection to the reference filter, including pagination and residual predicates.
        // Bound new identifier plans across all partitions too. Without selectivity statistics,
        // hydrating a broad seed can cost more than intersecting the original index postings.
        val maxCandidates = if (requireIdentifierSeed) {
            minOf(context.maxCardinality, MAX_IDENTIFIER_CONJUNCT_CANDIDATES)
        } else context.maxCardinality
        return try {
            indexScanner.scan().takeIf { it.size <= maxCandidates }
        } catch (_: MaxCardinalityExceededException) {
            null
        } catch (exception: ExecutionException) {
            if (exception.cause is MaxCardinalityExceededException) null else throw exception
        }
    }

    private fun QueryCriteria.hasExactScalarComparison(descriptor: EntityDescriptor): Boolean {
        if (isRelationship == true) return false
        val type = descriptor.attributes[attribute]?.type?.kotlin?.javaObjectType ?: return false
        if (type !in EXACT_SCALAR_COMPARISON_TYPES && !type.isEnum) return false
        if (descriptor.indexes[attribute]?.let { it.indexType != IndexType.DEFAULT } == true) return false
        return when (operator) {
            QueryCriteriaOperator.EQUAL -> value?.javaClass == type
            QueryCriteriaOperator.IN -> (value as? List<*>)?.all { it?.javaClass == type } == true
            else -> false
        }
    }

    private fun QueryCriteria.isScalarConjunction(): Boolean =
        !isNot && !flip && !isOr && attribute != Query.FULL_TEXT_ATTRIBUTE &&
            operator != QueryCriteriaOperator.CANDIDATES &&
            operator != QueryCriteriaOperator.SEARCH &&
            operator != QueryCriteriaOperator.SEARCH_CANDIDATES &&
            operator != QueryCriteriaOperator.HNSW_CANDIDATES &&
            subCriteria.all { it.isAnd && it.isScalarConjunction() }

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

    private companion object {
        const val MAX_IDENTIFIER_CONJUNCT_CANDIDATES = 1_024
        val EXACT_SCALAR_COMPARISON_TYPES = setOf(
            Boolean::class.javaObjectType,
            Byte::class.javaObjectType,
            Short::class.javaObjectType,
            Int::class.javaObjectType,
            Long::class.javaObjectType,
            Float::class.javaObjectType,
            Double::class.javaObjectType,
            Char::class.javaObjectType,
            String::class.java
        )
    }

}
