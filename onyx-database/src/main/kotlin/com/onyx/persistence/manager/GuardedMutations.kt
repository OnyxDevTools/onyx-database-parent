package com.onyx.persistence.manager

import com.onyx.descriptor.EntityDescriptor
import com.onyx.descriptor.IndexDescriptor
import com.onyx.extension.get
import com.onyx.extension.common.getAny
import com.onyx.extension.isValid
import com.onyx.extension.meetsCriteria
import com.onyx.extension.nullPartition
import com.onyx.extension.resolveSubQueries
import com.onyx.extension.validate
import com.onyx.interactors.index.IndexInteractor
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.annotations.values.IndexType
import com.onyx.persistence.context.GuardedDeleteWork
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.reportGuardedDeleteWork
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.interactors.record.withRecordMutationLocks
import com.onyx.interactors.record.withRelationshipMutationLock
import java.io.Serializable
import java.util.LinkedHashMap
import java.util.Objects

/** Maximum number of child rows accepted by one guarded save operation. */
const val MAX_GUARDED_SAVE_ENTITIES: Int = 500

/** Maximum number of child rows removed by one guarded delete operation. */
const val MAX_GUARDED_DELETE_ENTITIES: Int = 500

/** A guarded conditional update is deliberately a single-row compare-and-set. */
const val MAX_GUARDED_UPDATE_ENTITIES: Int = 1

/**
 * Identifies the lease/ownership row and the exact persisted fields that must still match.
 *
 * [expectedFields] are compared without numeric or string coercion. Callers crossing a wire must
 * therefore decode values to the field types declared by [entityType]. Partitioned guards require
 * an explicit, concrete [partition].
 */
data class EntityMutationGuard @JvmOverloads constructor(
    val entityType: Class<out IManagedEntity>,
    val identifier: Any?,
    val expectedFields: Map<String, Any?>,
    val partition: Any? = null,
) : Serializable

/** The guard outcome is independent of the number of affected child rows. */
enum class GuardedMutationStatus {
    APPLIED,
    GUARD_MISMATCH,
}

/**
 * Result of a server-side guarded mutation. [value] is null on [GuardedMutationStatus.GUARD_MISMATCH].
 * An applied delete may legitimately return zero and still has [guardMatched] set to true.
 */
data class GuardedMutationResult<out T> @JvmOverloads constructor(
    val status: GuardedMutationStatus,
    val value: T? = null,
) : Serializable {
    val guardMatched: Boolean
        get() = status == GuardedMutationStatus.APPLIED

    /** Number of committed target calls/rows represented by [value]. */
    val affected: Int
        get() = if (!guardMatched) 0 else when (value) {
            is Collection<*> -> value.size
            is Number -> value.toInt()
            null -> 0
            else -> 1
        }
}

/**
 * Saves one entity only while [guard] still matches its persisted ownership row.
 *
 * This primitive is intentionally embedded/server-side. The guard store and target store remain
 * locked through the complete row, index (including HNSW), relationship, cache and listener path.
 */
fun <E : IManagedEntity> PersistenceManager.saveEntityIfGuardMatches(
    entity: E,
    guard: EntityMutationGuard,
): GuardedMutationResult<E> {
    val result = saveEntitiesIfGuardMatches(listOf(entity), guard)
    @Suppress("UNCHECKED_CAST")
    return GuardedMutationResult(result.status, result.value?.single() as E?)
}

/**
 * Saves between one and [MAX_GUARDED_SAVE_ENTITIES] entities under one guard comparison.
 *
 * Targets may occupy multiple concrete partitions; all stores are acquired in a deterministic
 * order. This is an ordered, guarded batch rather than a rollback transaction: an exception is
 * propagated and never reported as [GuardedMutationStatus.APPLIED], but preceding row writes are
 * not rolled back. A save always addresses the partition carried by the entity and cannot request
 * an ALL-partition mutation or a partition move.
 */
fun PersistenceManager.saveEntitiesIfGuardMatches(
    entities: List<IManagedEntity>,
    guard: EntityMutationGuard,
): GuardedMutationResult<List<IManagedEntity>> {
    val embedded = requireEmbeddedGuardedMutationManager()
    val targets = entities.toList()
    require(targets.isNotEmpty()) { "A guarded save requires at least one entity" }
    require(targets.size <= MAX_GUARDED_SAVE_ENTITIES) {
        "A guarded save supports at most $MAX_GUARDED_SAVE_ENTITIES entities"
    }

    val preparedGuard = context.prepareGuard(guard)
    val targetDescriptors = targets.map { entity ->
        require(entity.isValid(context)) { "The guarded save target is invalid" }
        context.concreteDescriptorForGuardedEntity(entity, "Guarded save target")
    }
    val descriptors = listOf(preparedGuard.descriptor) + targetDescriptors
    val mutate = {
        context.withRecordMutationLocks(descriptors) {
            if (!context.guardMatches(preparedGuard)) {
                GuardedMutationResult(GuardedMutationStatus.GUARD_MISMATCH)
            } else {
                targets.forEach(embedded::saveEntity)
                GuardedMutationResult(GuardedMutationStatus.APPLIED, targets)
            }
        }
    }
    return if (targetDescriptors.any(EntityDescriptor::hasRelationships)) {
        context.withRelationshipMutationLock(mutate)
    } else {
        mutate()
    }
}

/**
 * Deletes rows selected by one concrete-store [query] only while [guard] still matches.
 *
 * [GuardedMutationStatus.APPLIED] with value `0` means the guard matched but no child matched the
 * filter. [GuardedMutationStatus.GUARD_MISMATCH] is distinct and carries a null value. ALL/blank
 * partition queries for partitioned entities are rejected before any mutation.
 */
@JvmOverloads
fun PersistenceManager.executeDeleteIfGuardMatches(
    query: Query,
    guard: EntityMutationGuard,
    maxDeletes: Int = MAX_GUARDED_DELETE_ENTITIES,
): GuardedMutationResult<Int> {
    val embedded = requireEmbeddedGuardedMutationManager()
    require(maxDeletes in 1..MAX_GUARDED_DELETE_ENTITIES) {
        "maxDeletes must be between 1 and $MAX_GUARDED_DELETE_ENTITIES"
    }
    require(query.criteria != null) {
        "A guarded delete requires filters and cannot truncate a complete partition"
    }
    require(query.updates.isEmpty()) { "A guarded delete query cannot contain attribute updates" }
    require(query.firstRow == 0) { "A guarded delete cannot use an offset" }
    require(query.queryOrders.isNullOrEmpty()) { "A guarded delete cannot use result ordering" }
    require(query.maxResults <= 0 || query.maxResults <= maxDeletes) {
        "A guarded delete cannot exceed its maxDeletes bound of $maxDeletes"
    }
    if (query.maxResults <= 0) query.maxResults = maxDeletes
    val effectiveDeleteLimit = query.maxResults
    val targetType = requireNotNull(query.entityType) { "A guarded delete query requires an entity type" }
    require(IManagedEntity::class.java.isAssignableFrom(targetType)) {
        "A guarded delete query requires a managed entity type"
    }

    val preparedGuard = context.prepareGuard(guard)
    val targetDescriptor = context.concreteDescriptorForGuardedQuery(
        targetType,
        query.partition,
        "Guarded delete",
    )
    // Nested queries can touch unrelated stores. Resolve them and validate the now-concrete
    // mutation shape before acquiring the deterministic guard/target lock set.
    query.resolveSubQueries(embedded)
    query.isUpdateOrDelete = true
    query.validate(context, targetDescriptor)
    val mutate = {
        context.withRecordMutationLocks(listOf(preparedGuard.descriptor, targetDescriptor)) {
            if (!context.guardMatches(preparedGuard)) {
                GuardedMutationResult(GuardedMutationStatus.GUARD_MISMATCH)
            } else {
                val indexedDelete = context.collectGuardedDeleteIndexPage(
                    query,
                    targetDescriptor,
                    effectiveDeleteLimit,
                )
                if (indexedDelete == null) {
                    GuardedMutationResult(GuardedMutationStatus.APPLIED, embedded.executeDelete(query))
                } else {
                    val deleted = embedded.executeGuardedDeleteWithReferences(
                        query,
                        targetDescriptor,
                        indexedDelete.references,
                    )
                    require(deleted == indexedDelete.references.size) {
                        "Indexed guarded delete selected ${indexedDelete.references.size} rows but deleted $deleted"
                    }
                    context.reportGuardedDeleteWork(
                        GuardedDeleteWork(
                            pageLimit = effectiveDeleteLimit,
                            eligibleIndexCount = indexedDelete.eligibleIndexCount,
                            cardinalityProbePostingVisits = indexedDelete.cardinalityProbePostingVisits,
                            postingVisits = indexedDelete.postingVisits,
                            recordLookups = indexedDelete.recordLookups,
                            matchedReferenceCount = indexedDelete.references.size,
                            deletedCount = deleted,
                            drivingAttribute = indexedDelete.drivingAttribute,
                        )
                    )
                    GuardedMutationResult(GuardedMutationStatus.APPLIED, deleted)
                }
            }
        }
    }
    return if (targetDescriptor.hasRelationships) context.withRelationshipMutationLock(mutate) else mutate()
}

private data class GuardedDeleteIndexCandidate(
    val attribute: String,
    val indexInteractor: IndexInteractor,
    val values: List<Any>,
)

private data class GuardedDeleteIndexPage(
    val references: List<Reference>,
    val eligibleIndexCount: Int,
    val cardinalityProbePostingVisits: Int,
    val postingVisits: Int,
    val recordLookups: Int,
    val drivingAttribute: String,
)

private data class GuardedDeleteDriver(
    val candidate: GuardedDeleteIndexCandidate,
    val cardinalityProbePostingVisits: Int,
)

/**
 * Select and stream one exact posting route for a local, non-negated AND predicate.
 *
 * Returning null means the shape is intentionally unsupported and must retain the normal
 * full-table mutation path. No row has been changed before this function returns.
 */
private fun SchemaContext.collectGuardedDeleteIndexPage(
    query: Query,
    descriptor: EntityDescriptor,
    pageLimit: Int,
): GuardedDeleteIndexPage? {
    val candidates = guardedDeleteIndexCandidates(requireNotNull(query.criteria), descriptor)
        ?: return null
    if (candidates.isEmpty()) return null

    val driver = try {
        chooseGuardedDeleteDriver(candidates, pageLimit)
    } catch (_: UnsupportedOperationException) {
        return null
    }
    val targetType = requireNotNull(query.entityType)
    val partitionId = if (descriptor.hasPartition) {
        requireNotNull(getPartitionWithValue(targetType, query.partition)) {
            "Guarded delete target partition disappeared before its indexed scan"
        }.index
    } else {
        0L
    }
    val recordInteractor = getRecordInteractor(descriptor)
    val references = ArrayList<Reference>(pageLimit)
    var recordLookups = 0
    var callbackVisits = 0
    val postingVisits = try {
        driver.candidate.indexInteractor.visitExactPostings(driver.candidate.values) { recordId ->
            callbackVisits++
            recordLookups++
            val entity = requireNotNull(recordInteractor.getWithReferenceId(recordId)) {
                "Index '${driver.candidate.attribute}' contains dangling record reference $recordId"
            }
            val reference = Reference(partitionId, recordId)
            if (query.meetsCriteria(entity, reference, this, descriptor)) {
                references += reference
            }
            references.size < pageLimit
        }
    } catch (_: UnsupportedOperationException) {
        return null
    }
    require(postingVisits == callbackVisits) {
        "Exact posting visitor reported $postingVisits visits after invoking $callbackVisits callbacks"
    }
    require(references.size <= pageLimit) {
        "Indexed guarded delete exceeded its $pageLimit-row page bound"
    }
    return GuardedDeleteIndexPage(
        references = references,
        eligibleIndexCount = candidates.size,
        cardinalityProbePostingVisits = driver.cardinalityProbePostingVisits,
        postingVisits = postingVisits,
        recordLookups = recordLookups,
        drivingAttribute = driver.candidate.attribute,
    )
}

/** Every leaf must be safe to evaluate while only the guard and target stores are locked. */
private fun SchemaContext.guardedDeleteIndexCandidates(
    root: QueryCriteria,
    descriptor: EntityDescriptor,
): List<GuardedDeleteIndexCandidate>? {
    val candidates = ArrayList<GuardedDeleteIndexCandidate>()

    fun visit(criteria: QueryCriteria, isRoot: Boolean): Boolean {
        val operator = criteria.operator ?: return false
        if (
            criteria.isNot ||
            criteria.flip ||
            criteria.isOr ||
            (!isRoot && !criteria.isAnd) ||
            operator in NEGATED_GUARDED_DELETE_OPERATORS ||
            operator in READ_ONLY_CANDIDATE_OPERATORS
        ) {
            return false
        }
        val attribute = criteria.attribute ?: return false
        if (!descriptor.attributes.containsKey(attribute)) return false

        val indexDescriptor = descriptor.indexes[attribute]
        if (indexDescriptor?.indexType == IndexType.DEFAULT) {
            exactGuardedDeleteValues(criteria, indexDescriptor)?.let { values ->
                candidates += GuardedDeleteIndexCandidate(
                    attribute = attribute,
                    indexInteractor = getIndexInteractor(indexDescriptor),
                    values = values,
                )
            }
        }
        return criteria.subCriteria.all { visit(it, false) }
    }

    return if (visit(root, true)) candidates else null
}

private fun exactGuardedDeleteValues(
    criteria: QueryCriteria,
    indexDescriptor: IndexDescriptor,
): List<Any>? {
    val rawValues: List<Any?> = when (criteria.operator) {
        QueryCriteriaOperator.EQUAL -> listOf(criteria.value)
        QueryCriteriaOperator.IN -> criteria.value as? List<*> ?: return null
        else -> return null
    }
    val valueType = boxedType(indexDescriptor.type)
    if (rawValues.any { value ->
            value == null ||
                value is Iterable<*> ||
                value is Sequence<*> ||
                value is Map<*, *> ||
                value.javaClass.isArray ||
                !valueType.isInstance(value)
        }
    ) {
        return null
    }
    return rawValues.filterNotNull()
}

private fun chooseGuardedDeleteDriver(
    candidates: List<GuardedDeleteIndexCandidate>,
    pageLimit: Int,
): GuardedDeleteDriver {
    val ordered = candidates.sortedBy(GuardedDeleteIndexCandidate::attribute)
    if (ordered.size == 1) return GuardedDeleteDriver(ordered.single(), 0)

    val probeLimit = pageLimit + 1
    var totalProbeVisits = 0
    var selected: Pair<GuardedDeleteIndexCandidate, Int>? = null
    ordered.forEach { candidate ->
        var callbacks = 0
        val visits = candidate.indexInteractor.visitExactPostings(candidate.values) {
            callbacks++
            callbacks < probeLimit
        }
        require(visits == callbacks) {
            "Exact posting visitor reported $visits probe visits after invoking $callbacks callbacks"
        }
        require(visits <= probeLimit) {
            "Exact posting cardinality probe exceeded its $probeLimit-visit bound"
        }
        totalProbeVisits += visits
        val scored = candidate to visits
        val current = selected
        if (
            current == null ||
            scored.second < current.second ||
            scored.second == current.second && scored.first.attribute < current.first.attribute
        ) {
            selected = scored
        }
    }
    return GuardedDeleteDriver(requireNotNull(selected).first, totalProbeVisits)
}

private val NEGATED_GUARDED_DELETE_OPERATORS = setOf(
    QueryCriteriaOperator.NOT_EQUAL,
    QueryCriteriaOperator.NOT_STARTS_WITH,
    QueryCriteriaOperator.NOT_NULL,
    QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE,
    QueryCriteriaOperator.NOT_CONTAINS,
    QueryCriteriaOperator.NOT_LIKE,
    QueryCriteriaOperator.NOT_MATCHES,
    QueryCriteriaOperator.NOT_BETWEEN,
    QueryCriteriaOperator.NOT_IN,
)

private val READ_ONLY_CANDIDATE_OPERATORS = setOf(
    QueryCriteriaOperator.CANDIDATES,
    QueryCriteriaOperator.SEARCH_CANDIDATES,
    QueryCriteriaOperator.HNSW_CANDIDATES,
)

/**
 * Conditionally updates at most one row while [guard] still matches its persisted ownership row.
 *
 * Both the guard store and the one concrete target store remain locked from the guard comparison
 * through target-criteria evaluation and the complete row/index/cache mutation. A matched guard
 * with no matching target is [GuardedMutationStatus.APPLIED] with value `0`; it is distinct from a
 * stale guard. Partition moves are rejected because they would escape the pre-acquired target-store
 * lock and are not a single-store compare-and-set.
 */
fun PersistenceManager.executeUpdateIfGuardMatches(
    query: Query,
    guard: EntityMutationGuard,
): GuardedMutationResult<Int> {
    val embedded = requireEmbeddedGuardedMutationManager()
    require(query.criteria != null) {
        "A guarded update requires target criteria"
    }
    require(query.updates.isNotEmpty()) {
        "A guarded update requires at least one attribute update"
    }
    require(query.firstRow == 0) { "A guarded update cannot use an offset" }
    require(query.queryOrders.isNullOrEmpty()) { "A guarded update cannot use result ordering" }
    require(query.maxResults <= 0 || query.maxResults <= MAX_GUARDED_UPDATE_ENTITIES) {
        "A guarded update cannot affect more than $MAX_GUARDED_UPDATE_ENTITIES row"
    }
    if (query.maxResults <= 0) query.maxResults = MAX_GUARDED_UPDATE_ENTITIES

    val targetType = requireNotNull(query.entityType) { "A guarded update query requires an entity type" }
    require(IManagedEntity::class.java.isAssignableFrom(targetType)) {
        "A guarded update query requires a managed entity type"
    }

    val preparedGuard = context.prepareGuard(guard)
    val targetDescriptor = context.concreteDescriptorForGuardedQuery(
        targetType,
        query.partition,
        "Guarded update",
    )
    val identifierName = requireNotNull(targetDescriptor.identifier).name
    require(query.updates.none { it.fieldName.equals(identifierName, ignoreCase = true) }) {
        "A guarded update cannot modify an identifier"
    }
    val partitionName = targetDescriptor.partition?.name
    require(
        partitionName == null ||
            query.updates.none { it.fieldName.equals(partitionName, ignoreCase = true) }
    ) {
        "A guarded update cannot move a row between partitions"
    }
    // Resolve external query inputs before validating the keyed predicate and taking the
    // deterministic guard/target lock set. No relationship or unknown-field leaf is admitted,
    // because evaluating it could otherwise escape the pre-acquired target-store lock.
    query.resolveSubQueries(embedded)
    val targetIdentifier = requireGuardedUpdateIdentifier(query.criteria!!, targetDescriptor)
    query.isUpdateOrDelete = true
    query.validate(context, targetDescriptor)
    return context.withRecordMutationLocks(listOf(preparedGuard.descriptor, targetDescriptor)) {
        if (!context.guardMatches(preparedGuard)) {
            GuardedMutationResult(GuardedMutationStatus.GUARD_MISMATCH)
        } else {
            GuardedMutationResult(
                GuardedMutationStatus.APPLIED,
                embedded.executeGuardedSingleUpdate(query, targetDescriptor, targetIdentifier),
            )
        }
    }
}

private val NEGATED_GUARDED_UPDATE_OPERATORS = setOf(
    QueryCriteriaOperator.NOT_EQUAL,
    QueryCriteriaOperator.NOT_IN,
    QueryCriteriaOperator.NOT_BETWEEN,
    QueryCriteriaOperator.NOT_MATCHES,
    QueryCriteriaOperator.NOT_LIKE,
    QueryCriteriaOperator.NOT_CONTAINS,
    QueryCriteriaOperator.NOT_STARTS_WITH,
    QueryCriteriaOperator.NOT_NULL,
    QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE,
)

/** Returns the unique-key lookup value after proving the target predicate is local and conjunctive. */
private fun requireGuardedUpdateIdentifier(
    criteria: QueryCriteria,
    descriptor: EntityDescriptor,
): Any {
    val identifierDescriptor = requireNotNull(descriptor.identifier)
    val identifierName = identifierDescriptor.name
    var identifier: Any? = null

    fun visit(current: QueryCriteria, isRoot: Boolean) {
        val operator = requireNotNull(current.operator) {
            "A guarded update target predicate requires an operator"
        }
        require(
            !current.isNot &&
                !current.flip &&
                !current.isOr &&
                (isRoot || current.isAnd) &&
                operator !in NEGATED_GUARDED_UPDATE_OPERATORS
        ) {
            "A guarded update requires a non-negated AND-only target predicate"
        }
        val attribute = current.attribute
        require(attribute != null && descriptor.attributes.containsKey(attribute)) {
            "A guarded update target predicate may contain only persisted local attributes"
        }
        if (attribute == identifierName && operator == QueryCriteriaOperator.EQUAL) {
            val value = requireNotNull(current.value) {
                "A guarded update identifier value cannot be null"
            }
            require(
                value !is Iterable<*> &&
                    value !is Sequence<*> &&
                    value !is Map<*, *> &&
                    !value.javaClass.isArray &&
                    boxedType(identifierDescriptor.type).isInstance(value)
            ) {
                "A guarded update identifier must be one exact scalar ${identifierDescriptor.type.simpleName} value"
            }
            if (identifier == null) identifier = value
        }
        current.subCriteria.forEach { visit(it, false) }
    }

    visit(criteria, true)
    return requireNotNull(identifier) {
        "A guarded update requires an identifier EQUAL target predicate"
    }
}

private fun boxedType(type: Class<*>): Class<*> = when (type) {
    java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
    java.lang.Character.TYPE -> java.lang.Character::class.java
    java.lang.Byte.TYPE -> java.lang.Byte::class.java
    java.lang.Short.TYPE -> java.lang.Short::class.java
    java.lang.Integer.TYPE -> java.lang.Integer::class.java
    java.lang.Long.TYPE -> java.lang.Long::class.java
    java.lang.Float.TYPE -> java.lang.Float::class.java
    java.lang.Double.TYPE -> java.lang.Double::class.java
    else -> type
}

private data class PreparedEntityMutationGuard(
    val descriptor: EntityDescriptor,
    val identifier: Any,
    val expectedFields: Map<String, Any?>,
)

private fun PersistenceManager.requireEmbeddedGuardedMutationManager(): EmbeddedPersistenceManager =
    this as? EmbeddedPersistenceManager
        ?: throw UnsupportedOperationException(
            "Guarded mutations must execute inside an EmbeddedPersistenceManager on the database server"
        )

private fun SchemaContext.prepareGuard(guard: EntityMutationGuard): PreparedEntityMutationGuard {
    require(guard.expectedFields.isNotEmpty()) { "A mutation guard requires at least one expected field" }
    val expected = LinkedHashMap(guard.expectedFields)
    expected.keys.forEach { require(it.isNotBlank()) { "Mutation guard field names cannot be blank" } }

    val baseDescriptor = requireNotNull(getBaseDescriptorForEntity(guard.entityType))
    val descriptor = if (baseDescriptor.hasPartition) {
        val partition = requireConcretePartition(guard.partition, "Mutation guard")
        getDescriptorForEntity(guard.entityType, partition)
    } else {
        require(guard.partition == null || guard.partition.toString().isBlank()) {
            "An unpartitioned mutation guard cannot specify a partition"
        }
        baseDescriptor
    }
    expected.keys.forEach { fieldName ->
        require(descriptor.attributes.containsKey(fieldName)) {
            "Mutation guard field '$fieldName' is not a persisted attribute of ${guard.entityType.name}"
        }
    }
    val identifier = requireNotNull(guard.identifier) { "A mutation guard requires an identifier" }
    return PreparedEntityMutationGuard(descriptor, identifier, expected)
}

private fun SchemaContext.concreteDescriptorForGuardedEntity(
    entity: IManagedEntity,
    label: String,
): EntityDescriptor {
    val baseDescriptor = requireNotNull(getBaseDescriptorForEntity(entity.javaClass))
    if (!baseDescriptor.hasPartition) return baseDescriptor
    val partitionField = requireNotNull(baseDescriptor.partition).field
    val partition: Any? = entity.getAny(partitionField)
    return getDescriptorForEntity(entity.javaClass, requireConcretePartition(partition, label))
}

private fun SchemaContext.concreteDescriptorForGuardedQuery(
    entityType: Class<*>,
    partition: Any?,
    label: String,
): EntityDescriptor {
    val baseDescriptor = requireNotNull(getBaseDescriptorForEntity(entityType))
    if (!baseDescriptor.hasPartition) {
        require(partition == null || partition.toString().isBlank()) {
            "$label cannot specify a partition for an unpartitioned entity"
        }
        return baseDescriptor
    }
    return getDescriptorForEntity(entityType, requireConcretePartition(partition, label))
}

private fun requireConcretePartition(partition: Any?, label: String): Any {
    val partitionText = partition?.toString()
    require(
        partition != null &&
            partition != QueryPartitionMode.ALL &&
            !partitionText.isNullOrBlank() &&
            !partitionText.equals(QueryPartitionMode.ALL.name, ignoreCase = true)
    ) {
        "$label requires a concrete partition"
    }
    require(partitionText != nullPartition) { "$label requires a concrete partition" }
    return partition
}

private fun SchemaContext.guardMatches(guard: PreparedEntityMutationGuard): Boolean {
    val persisted = getRecordInteractor(guard.descriptor).getWithId(guard.identifier) ?: return false
    return guard.expectedFields.all { (fieldName, expected) ->
        val actual: Any? = persisted[this, guard.descriptor, fieldName]
        Objects.deepEquals(actual, expected)
    }
}
