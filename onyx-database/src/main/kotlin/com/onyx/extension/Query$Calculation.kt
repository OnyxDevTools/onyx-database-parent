package com.onyx.extension

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.extension.common.compare
import com.onyx.extension.common.get
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.compareCriterion
import com.onyx.persistence.query.resolveVectorSearchQuery
import com.onyx.persistence.query.relationship
import com.onyx.vector.VectorEntityEncoder
import com.onyx.vector.VectorSearchEvaluator
import com.onyx.vector.VectorValueCodec
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Entity meets the query criteria.  This method is used to determine whether the entity meets all the
 * criteria of the query.  It was implemented so that we no longer have logic in the query controller
 * to sift through scans.  We can now only perform a full table scan once.
 *
 * @param entity Entity to check for criteria
 * @param entityReference The entities reference
 * @param context Schema context used to pull entity descriptors, and such
 * @param descriptor Quick reference to the entities descriptor so we do not have to pull it from the schema context
 * @return Whether the entity meets all the criteria.
 * @throws OnyxException Cannot hydrate or pull an attribute from an entity
 *
 * @since 1.3.0 Simplified query criteria management
 */
@Throws(OnyxException::class)
fun Query.meetsCriteria(entity: IManagedEntity?, entityReference: Reference, context: SchemaContext, descriptor: EntityDescriptor): Boolean {
    val evaluation = evaluateCriteria(entity, entityReference, context, descriptor)
    recordVectorSearchScore(entityReference, evaluation)
    return evaluation.matches
}

/**
 * Installs the score accumulator used by workers before a parallel scan starts. Predicate
 * evaluation can then publish a score before its collector consumes the row without taking the
 * query monitor. Existing scores are retained when a preceding scanner supplied candidates.
 */
internal fun Query.prepareConcurrentScoreCollection(descriptor: EntityDescriptor) {
    if (!VectorManagedEntity::class.java.isAssignableFrom(descriptor.entityClass)) return
    if (criteria?.evaluatesVectorSearchScore() != true) return

    val existingScores = fullTextScores
    if (existingScores is ConcurrentMap<*, *>) return
    fullTextScores = ConcurrentHashMap<Reference, Float>().apply {
        if (existingScores != null) putAll(existingScores)
    }
}

private fun Query.evaluateCriteria(
    entity: IManagedEntity?,
    entityReference: Reference,
    context: SchemaContext,
    descriptor: EntityDescriptor,
): CriteriaEvaluation {
    val evaluation = CriteriaEvaluation()

    fun evaluate(criteria: QueryCriteria): Boolean {
        var matches = if (criteria.flip) {
            false
        } else {
            evaluateCriterion(
                entity,
                entityReference,
                criteria,
                context,
                descriptor,
                evaluation,
            )
        }

        criteria.subCriteria.forEach { child ->
            val childMatches = evaluate(child)
            if (!child.flip) {
                matches = if (child.isOr) childMatches || matches else childMatches && matches
            }
        }
        return if (criteria.isNot) !matches else matches
    }

    val rootCriteria = criteria
    evaluation.matches = rootCriteria == null || evaluate(rootCriteria)
    return evaluation
}

private fun Query.evaluateCriterion(
    entity: IManagedEntity?,
    entityReference: Reference,
    criterion: QueryCriteria,
    context: SchemaContext,
    descriptor: EntityDescriptor,
    evaluation: CriteriaEvaluation,
): Boolean {
    return if (criterion.isRelationship == true) {
        if (descriptor.relationships.contains(criterion.relationship)) {
            relationshipMeetsCriteria(entity, entityReference, criterion, context)
        } else {
            graphMeetsCriteria(entity, criterion)
        }
    }
    else if (criterion.operator == QueryCriteriaOperator.CANDIDATES) {
        // Composed approximate-index queries pre-admit this bounded set once. The
        // remaining predicate tree is then evaluated only for those references.
        approximateIndexCandidateMatches?.contains(entityReference) == true
    }
    else if (criterion.attribute == Query.FULL_TEXT_ATTRIBUTE) {
        val isHighLevelAdmission = criterion.operator == QueryCriteriaOperator.SEARCH
        val evaluatedScore = if (entity is VectorManagedEntity && !isHighLevelAdmission) {
            evaluation.evaluatedVectorSearch = true
            resolveVectorSearchQuery(criterion.value)?.let { searchQuery ->
                VectorSearchEvaluator.evaluate(entity, descriptor, searchQuery)
            }
        } else {
            null
        }
        if (evaluatedScore != null) {
            evaluation.vectorSearchScore = maxOf(evaluation.vectorSearchScore ?: evaluatedScore, evaluatedScore)
        }
        val matches = if (isHighLevelAdmission) {
            vectorSearchMatches?.get(criterion)?.contains(entityReference) == true
        } else if (entity is VectorManagedEntity) {
            evaluatedScore != null
        } else {
            vectorSearchMatches?.get(criterion)?.contains(entityReference)
                ?: (fullTextScores?.containsKey(entityReference) == true)
        }
        when (criterion.operator) {
            QueryCriteriaOperator.NOT_MATCHES,
            QueryCriteriaOperator.NOT_LIKE,
            QueryCriteriaOperator.NOT_CONTAINS,
            QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE,
            QueryCriteriaOperator.NOT_STARTS_WITH,
            QueryCriteriaOperator.NOT_EQUAL,
            QueryCriteriaOperator.NOT_IN,
            QueryCriteriaOperator.NOT_BETWEEN,
            QueryCriteriaOperator.NOT_NULL -> !matches
            else -> matches
        }
    } else {
        val attributeDescriptor = criterion.attributeDescriptor
            ?: descriptor.attributes[criterion.attribute!!]?.also { criterion.attributeDescriptor = it }
        val attribute = if (attributeDescriptor != null) {
            entity?.get<Any?>(context = context, descriptor = descriptor, name = criterion.attribute!!)
        } else {
            entity?.get<Any?>(criterion.attribute!!) // Use Kotlin property accessors
        }

        val usesStableVectorDateText = entity is VectorManagedEntity &&
            attribute is Date &&
            criterion.operator in VECTOR_TEXT_OPERATORS
        val comparableAttribute = if (usesStableVectorDateText) {
            VectorValueCodec.predicateText(attribute)
        } else {
            attribute.normalizeForComparison(criterion.operator, context)
        }
        val comparisonValue = if (usesStableVectorDateText && criterion.value is Date) {
            VectorValueCodec.predicateText(criterion.value as Date)
        } else {
            criterion.value
        }
        if (
            entity is VectorManagedEntity &&
            criterion.operator in setOf(QueryCriteriaOperator.LIKE, QueryCriteriaOperator.NOT_LIKE) &&
            attribute is CharSequence
        ) {
            val queryTerms = VectorEntityEncoder.tokens(comparisonValue?.toString() ?: "null").distinct()
            val matches = if (queryTerms.isEmpty()) {
                comparisonValue.compare(comparableAttribute, criterion.operator!!)
            } else {
                val attributeTerms = VectorEntityEncoder.tokens(comparableAttribute.toString()).toSet()
                queryTerms.all(attributeTerms::contains)
            }
            if (criterion.operator == QueryCriteriaOperator.NOT_LIKE && queryTerms.isNotEmpty()) !matches else matches
        } else {
            compareCriterion(criterion, comparableAttribute, comparisonValue)
        }
    }
}

private fun Query.recordVectorSearchScore(reference: Reference, evaluation: CriteriaEvaluation) {
    if (!evaluation.evaluatedVectorSearch) return

    val currentScores = fullTextScores
    if (currentScores is ConcurrentMap<*, *>) {
        @Suppress("UNCHECKED_CAST")
        evaluation.recordScore(reference, currentScores as MutableMap<Reference, Float>)
        return
    }

    // Direct criteria evaluation and non-partitioned scans lazily install the same concurrent
    // accumulator. Only that first installation is serialized; all predicate work remains local.
    synchronized(this) {
        val latestScores = fullTextScores
        if (latestScores is ConcurrentMap<*, *>) {
            @Suppress("UNCHECKED_CAST")
            evaluation.recordScore(reference, latestScores as MutableMap<Reference, Float>)
        } else {
            val concurrentScores = ConcurrentHashMap<Reference, Float>().apply {
                if (latestScores != null) putAll(latestScores)
            }
            evaluation.recordScore(reference, concurrentScores)
            fullTextScores = concurrentScores
        }
    }
}

private class CriteriaEvaluation {
    var matches = false
    var evaluatedVectorSearch = false
    var vectorSearchScore: Float? = null

    fun recordScore(reference: Reference, scores: MutableMap<Reference, Float>) {
        val score = vectorSearchScore
        if (score == null) scores.remove(reference)
        else scores[reference] = score
    }
}

private fun QueryCriteria.evaluatesVectorSearchScore(): Boolean =
    (!flip && attribute == Query.FULL_TEXT_ATTRIBUTE &&
        operator != QueryCriteriaOperator.CANDIDATES && operator != QueryCriteriaOperator.SEARCH) ||
        subCriteria.any { it.evaluatesVectorSearchScore() }

/**
 * Relationship meets criteria.  This method will hydrate a relationship for an entity and
 * check its criteria to ensure the criteria is met
 *
 * @param entity Original entity containing the relationship.  This entity may or may not have
 * hydrated relationships.  For that reason we have to go back to the store to
 * retrieve the relationship entities.
 *
 * @param entityReference Used for quick reference so we do not have to retrieve the entities
 * reference before retrieving the relationship.
 *
 * @param criteria Criteria to check for to see if we meet the requirements
 *
 * @param context Schema context used to pull entity descriptors and record controllers and such
 *
 * @return Whether the relationship value has met all of the criteria
 *
 * @throws OnyxException Something bad happened.
 *
 * @since 1.3.0 - Used to remove the dependency on relationship scanners and to allow query caching
 * to do a quick reference to see if newly saved entities meet the criteria
 */
@Throws(OnyxException::class)
private fun Query.relationshipMeetsCriteria(entity: IManagedEntity?, entityReference: Reference, criteria: QueryCriteria, context: SchemaContext): Boolean {
    var meetsCriteria = false
    val operator = criteria.operator

    // Grab the relationship from the store
    val relationshipEntities = entity?.getRelationshipFromStore(context, criteria.attribute!!, entityReference = entityReference)

    // If there are relationship values, check to see if they meet criteria
    if (relationshipEntities?.isNotEmpty() == true) {
        val attribute = criteria.attribute!!.split(".").lastOrNull()

        // All we need is a single match.  If there is a relationship that meets the criteria, move along
        for (relationshipEntity in relationshipEntities) {
            val comparisonValue = if (criteria.attribute!!.contains(".")) {
                relationshipEntity?.get(context = context, name = attribute!!)
            } else {
                relationshipEntity?.identifier(context)
            }

            meetsCriteria = compareCriterion(criteria, comparisonValue.normalizeForComparison(operator, context))
            if (meetsCriteria)
                break
        }
    } else {
        meetsCriteria = (operator == QueryCriteriaOperator.IS_NULL)
    }
    return meetsCriteria
}

/**
 * Graph meets criteria.  This method will iterate through a graph to
 * check its criteria to ensure the criteria is met
 *
 * @param entity Original entity containing the graph.  This entity may or may not have
 * criteria property.  For that reason we have to go back to the store to
 * retrieve the relationship entities.
 *
 * @param criteria Criteria to check for to see if we meet the requirements
 *
 * @return Whether the graph value has met the criteria
 *
 * @throws OnyxException Something bad happened.
 *
 */
@Throws(OnyxException::class)
private fun Query.graphMeetsCriteria(entity: IManagedEntity?, criteria: QueryCriteria): Boolean {
    val value = entity.get<Any?>(criteria.attribute!!)
    if (value is List<*>) {
        return value.any {
            compareCriterion(criteria, it.normalizeForComparison(criteria.operator, null))
        }
    }
    return compareCriterion(criteria, value.normalizeForComparison(criteria.operator, null))
}

private fun Any?.normalizeForComparison(operator: QueryCriteriaOperator?, context: SchemaContext?): Any? {
    if (operator != QueryCriteriaOperator.IN && operator != QueryCriteriaOperator.NOT_IN) return this

    return when (this) {
        is IManagedEntity -> runCatching { this.identifier(context) }.getOrNull() ?: this
        is Map<*, *> -> if (this.size == 1) this.values.firstOrNull() else this
        is Iterable<*> -> this.map { it.normalizeForComparison(operator, context) }
        is Array<*> -> this.map { it.normalizeForComparison(operator, context) }
        else -> this
    }
}

private val VECTOR_TEXT_OPERATORS = setOf(
    QueryCriteriaOperator.STARTS_WITH,
    QueryCriteriaOperator.NOT_STARTS_WITH,
    QueryCriteriaOperator.CONTAINS,
    QueryCriteriaOperator.NOT_CONTAINS,
    QueryCriteriaOperator.CONTAINS_IGNORE_CASE,
    QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE,
    QueryCriteriaOperator.LIKE,
    QueryCriteriaOperator.NOT_LIKE,
    QueryCriteriaOperator.MATCHES,
    QueryCriteriaOperator.NOT_MATCHES
)
