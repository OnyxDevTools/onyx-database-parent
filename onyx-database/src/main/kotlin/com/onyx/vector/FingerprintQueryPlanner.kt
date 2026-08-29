package com.onyx.vector

import com.onyx.descriptor.EntityDescriptor
import com.onyx.interactors.index.impl.FingerprintIndexInteractor
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.VectorFeatureFamily
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.resolveVectorSearchQuery
import com.onyx.persistence.query.resolveHnswSearchQuery
import java.lang.reflect.Array as ReflectArray
import java.math.BigInteger
import java.util.Date

/**
 * Conservative candidate algebra for fingerprint-backed predicates.
 *
 * A plan is never a statement that a record satisfies a predicate. Feature hashes can collide,
 * quantized intervals deliberately share coordinates, and text features are routing hints. Every
 * returned record must therefore be checked with Onyx's ordinary predicate evaluator.
 */
sealed interface FingerprintQueryPlan {
    /** No selective fingerprint route exists. Executors must not silently scan the domain. */
    object Universe : FingerprintQueryPlan

    /** No record can satisfy a well-typed positive predicate. */
    object Empty : FingerprintQueryPlan

    /** Records routed by one complete, multiword logical-feature fingerprint. */
    data class Feature(
        val logicalFeature: String,
        val fingerprint: FeatureFingerprint
    ) : FingerprintQueryPlan

    /** Union of candidate supersets. */
    data class AnyOf(val operands: List<FingerprintQueryPlan>) : FingerprintQueryPlan

    /** Intersection of candidate supersets. */
    data class AllOf(val operands: List<FingerprintQueryPlan>) : FingerprintQueryPlan

    /**
     * Exact complement of a routed positive predicate.
     *
     * [operand] is only a candidate superset, so it must be authoritatively verified before it
     * is subtracted from the indexed domain. [FingerprintQueryExecutor] deliberately refuses to
     * execute this node without that verification step.
     */
    data class Complement(val operand: FingerprintQueryPlan) : FingerprintQueryPlan

    /** Full-text/semantic routing delegated to the operator-specific search candidate provider. */
    data class Search(
        val operator: QueryCriteriaOperator,
        val value: Any?
    ) : FingerprintQueryPlan
}

/** Compiles [QueryCriteria] into a safe fingerprint candidate superset. */
class FingerprintQueryPlanner(
    private val descriptor: EntityDescriptor
) {
    private val configuration = VectorManagedConfiguration.forClass(descriptor.entityClass)
    private val namespace = VectorEntityEncoder.namespace(descriptor, configuration)
    private val definitions = configuration.attributes.associateBy(VectorAttributeDefinition::name)

    init {
        require(VectorManagedEntity::class.java.isAssignableFrom(descriptor.entityClass)) {
            "${descriptor.entityClass.name} is not vector managed"
        }
    }

    /** Recursively compiles the root predicate and its ordered AND/OR children. */
    fun compile(criteria: QueryCriteria): FingerprintQueryPlan {
        if (criteria.flip) return FingerprintQueryPlan.Universe

        var plan = compileLeafValue(criteria)
        criteria.subCriteria.forEach { child ->
            if (child.flip) return@forEach
            val childPlan = compile(child)
            plan = if (child.isOr) anyOf(plan, childPlan) else allOf(plan, childPlan)
        }
        return if (criteria.isNot && plan !== FingerprintQueryPlan.Universe) {
            FingerprintQueryPlan.Complement(plan)
        } else {
            plan
        }
    }

    /** Compiles only this criterion's leaf, ignoring children when selecting its scanner. */
    internal fun compileLeaf(criteria: QueryCriteria): FingerprintQueryPlan {
        if (criteria.flip) return FingerprintQueryPlan.Universe
        val plan = compileLeafValue(criteria)
        return if (criteria.isNot && plan !== FingerprintQueryPlan.Universe) {
            FingerprintQueryPlan.Complement(plan)
        } else {
            plan
        }
    }

    /** Whether this criterion's own leaf has a complete fingerprint candidate route. */
    fun canRouteLeaf(criteria: QueryCriteria): Boolean =
        compileLeaf(criteria) !== FingerprintQueryPlan.Universe

    private fun compileLeafValue(criteria: QueryCriteria): FingerprintQueryPlan {
        val operator = criteria.operator ?: return FingerprintQueryPlan.Universe
        if (criteria.attribute == Query.FULL_TEXT_ATTRIBUTE) {
            if (operator == QueryCriteriaOperator.HNSW_CANDIDATES) {
                if (runCatching { resolveHnswSearchQuery(criteria.value) }.isFailure) {
                    return FingerprintQueryPlan.Universe
                }
                return FingerprintQueryPlan.Search(operator, criteria.value)
            }
            if (!hasSearchRoute(criteria.value)) return FingerprintQueryPlan.Universe
            return when (operator) {
                QueryCriteriaOperator.MATCHES,
                QueryCriteriaOperator.LIKE,
                QueryCriteriaOperator.SEARCH_CANDIDATES ->
                    FingerprintQueryPlan.Search(operator, criteria.value)
                QueryCriteriaOperator.NOT_MATCHES -> FingerprintQueryPlan.Complement(
                    FingerprintQueryPlan.Search(QueryCriteriaOperator.MATCHES, criteria.value)
                )
                QueryCriteriaOperator.NOT_LIKE -> FingerprintQueryPlan.Complement(
                    FingerprintQueryPlan.Search(QueryCriteriaOperator.LIKE, criteria.value)
                )
                else -> FingerprintQueryPlan.Universe
            }
        }

        val attribute = criteria.attribute ?: return FingerprintQueryPlan.Universe
        if ('.' in attribute) return FingerprintQueryPlan.Universe
        val definition = definitions[attribute] ?: return FingerprintQueryPlan.Universe
        val attributeType = descriptor.attributes[attribute]?.type ?: return FingerprintQueryPlan.Universe

        return when (operator) {
            QueryCriteriaOperator.EQUAL -> equality(definition, attributeType, criteria.value)
            QueryCriteriaOperator.IN -> inValues(definition, attributeType, criteria.value)
            QueryCriteriaOperator.IS_NULL -> nullFeature(definition)
            QueryCriteriaOperator.NOT_NULL -> presentFeature(definition)
            QueryCriteriaOperator.NOT_EQUAL -> if (criteria.value == null) {
                presentFeature(definition)
            } else {
                complement(equality(definition, attributeType, criteria.value))
            }
            QueryCriteriaOperator.GREATER_THAN,
            QueryCriteriaOperator.GREATER_THAN_EQUAL,
            QueryCriteriaOperator.LESS_THAN,
            QueryCriteriaOperator.LESS_THAN_EQUAL,
            QueryCriteriaOperator.BETWEEN -> range(definition, attributeType, operator, criteria.value)
            QueryCriteriaOperator.LIKE -> like(definition, attributeType, criteria.value)
            QueryCriteriaOperator.MATCHES -> matches(definition, attributeType, criteria.value)
            QueryCriteriaOperator.STARTS_WITH -> startsWith(definition, attributeType, criteria.value)
            QueryCriteriaOperator.CONTAINS -> contains(definition, attributeType, criteria.value, ignoreCase = false)
            QueryCriteriaOperator.CONTAINS_IGNORE_CASE ->
                contains(definition, attributeType, criteria.value, ignoreCase = true)
            QueryCriteriaOperator.NOT_STARTS_WITH ->
                complement(startsWith(definition, attributeType, criteria.value))
            QueryCriteriaOperator.NOT_CONTAINS ->
                complement(contains(definition, attributeType, criteria.value, ignoreCase = false))
            QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE ->
                complement(contains(definition, attributeType, criteria.value, ignoreCase = true))
            QueryCriteriaOperator.NOT_LIKE -> complement(like(definition, attributeType, criteria.value))
            QueryCriteriaOperator.NOT_MATCHES -> complement(matches(definition, attributeType, criteria.value))
            QueryCriteriaOperator.NOT_BETWEEN ->
                complement(range(definition, attributeType, QueryCriteriaOperator.BETWEEN, criteria.value))
            QueryCriteriaOperator.NOT_IN -> complement(inValues(definition, attributeType, criteria.value))
            QueryCriteriaOperator.CANDIDATES,
            QueryCriteriaOperator.SEARCH_CANDIDATES,
            QueryCriteriaOperator.HNSW_CANDIDATES -> FingerprintQueryPlan.Universe
        }
    }

    private fun equality(
        definition: VectorAttributeDefinition,
        attributeType: Class<*>,
        value: Any?
    ): FingerprintQueryPlan {
        if (value == null) return nullFeature(definition)
        if (value is Iterable<*> || value.javaClass.isArray) return FingerprintQueryPlan.Universe
        if (!isCompatible(attributeType, value)) return FingerprintQueryPlan.Universe

        if (value is Float && value == 0f) {
            return anyOf(scalarFeature(definition, -0.0f), scalarFeature(definition, 0.0f))
        }
        if (value is Double && value == 0.0) {
            return anyOf(scalarFeature(definition, -0.0), scalarFeature(definition, 0.0))
        }
        return scalarFeature(definition, value)
    }

    private fun inValues(
        definition: VectorAttributeDefinition,
        attributeType: Class<*>,
        rawValues: Any?
    ): FingerprintQueryPlan {
        val values = unpackValues(rawValues) ?: return FingerprintQueryPlan.Universe
        if (values.isEmpty()) return FingerprintQueryPlan.Empty
        return anyOf(values.map { equality(definition, attributeType, it) })
    }

    private fun range(
        definition: VectorAttributeDefinition,
        attributeType: Class<*>,
        operator: QueryCriteriaOperator,
        rawValue: Any?
    ): FingerprintQueryPlan {
        if (!definition.supports(VectorFeatureFamily.INTERVAL)) return FingerprintQueryPlan.Universe
        if (
            boxed(attributeType) in FLOATING_TYPES &&
            !definition.supports(VectorFeatureFamily.CATEGORICAL)
        ) {
            // NaN and infinities have no interval coordinate. Without their categorical routes a
            // range plan cannot remain a complete candidate superset.
            return FingerprintQueryPlan.Universe
        }
        val rawBounds: Pair<Any?, Any?> = if (operator == QueryCriteriaOperator.BETWEEN) {
            unpackPair(rawValue) ?: return FingerprintQueryPlan.Universe
        } else {
            when (operator) {
                QueryCriteriaOperator.GREATER_THAN,
                QueryCriteriaOperator.GREATER_THAN_EQUAL -> rawValue to null
                QueryCriteriaOperator.LESS_THAN,
                QueryCriteriaOperator.LESS_THAN_EQUAL -> null to rawValue
                else -> return FingerprintQueryPlan.Universe
            }
        }

        val lowerValue = rawBounds.first
        val upperValue = rawBounds.second
        if (operator == QueryCriteriaOperator.BETWEEN && (lowerValue == null || upperValue == null)) {
            return FingerprintQueryPlan.Universe
        }
        if (lowerValue != null && !isCompatible(attributeType, lowerValue)) return FingerprintQueryPlan.Universe
        if (upperValue != null && !isCompatible(attributeType, upperValue)) return FingerprintQueryPlan.Universe
        if (operator != QueryCriteriaOperator.BETWEEN && rawValue == null) return FingerprintQueryPlan.Universe

        val lowerCoordinate = lowerValue?.let(::coordinateOrNull)
        val upperCoordinate = upperValue?.let(::coordinateOrNull)
        if (lowerValue != null && lowerCoordinate == null) return FingerprintQueryPlan.Universe
        if (upperValue != null && upperCoordinate == null) return FingerprintQueryPlan.Universe

        val coordinate = lowerCoordinate ?: upperCoordinate ?: return FingerprintQueryPlan.Universe
        if (lowerCoordinate != null && !sameDomain(coordinate, lowerCoordinate)) return FingerprintQueryPlan.Universe
        if (upperCoordinate != null && !sameDomain(coordinate, upperCoordinate)) return FingerprintQueryPlan.Universe

        val maximum = BigInteger.ONE.shiftLeft(coordinate.bits).subtract(BigInteger.ONE)
        var lower = lowerCoordinate?.coordinate ?: BigInteger.ZERO
        var upper = upperCoordinate?.coordinate ?: maximum
        // A lossy monotonic coordinate (currently String's retained UTF-16 prefix) can map the
        // strict boundary and valid values beyond it to the same leaf. Keep that leaf and rely on
        // exact verification; excluding it would create false negatives.
        if (operator == QueryCriteriaOperator.GREATER_THAN && lowerCoordinate?.lossy != true) {
            lower = lower.add(BigInteger.ONE)
        }
        if (operator == QueryCriteriaOperator.LESS_THAN && upperCoordinate?.lossy != true) {
            upper = upper.subtract(BigInteger.ONE)
        }

        val routed = ArrayList<FingerprintQueryPlan>()
        if (lower <= upper && lower <= maximum && upper >= BigInteger.ZERO) {
            val boundedLower = lower.max(BigInteger.ZERO)
            val boundedUpper = upper.min(maximum)
            BinaryIntervalTree.cover(boundedLower, boundedUpper, coordinate.bits).forEach { node ->
                routed += intervalFeature(definition, coordinate, node)
            }
        }

        // The existing comparison semantics treat null as below every non-null value.
        if (operator == QueryCriteriaOperator.LESS_THAN || operator == QueryCriteriaOperator.LESS_THAN_EQUAL) {
            routed += nullFeature(definition)
        }
        addFloatingBoundaryCandidates(routed, definition, attributeType)
        return anyOf(routed)
    }

    private fun like(
        definition: VectorAttributeDefinition,
        attributeType: Class<*>,
        rawValue: Any?
    ): FingerprintQueryPlan {
        if (!hasCompatibleTextEncoding(attributeType)) return FingerprintQueryPlan.Universe
        val literal = predicateLiteral(rawValue)
        val boxed = boxed(attributeType)
        val textual = CharSequence::class.java.isAssignableFrom(boxed)
        val terms = if (textual) VectorEntityEncoder.tokens(literal).distinct() else emptyList()
        val nonNull = if (textual && terms.isNotEmpty()) {
            if (!definition.supports(VectorFeatureFamily.TEXT_TERM)) return FingerprintQueryPlan.Universe
            allOf(*terms.map { term ->
                feature(
                    "attribute:${definition.name}/text/term:" +
                        VectorEntityEncoder.escape(term)
                )
            }.toTypedArray())
        } else if (
            definition.supports(VectorFeatureFamily.TEXT_EXACT) &&
            !VectorEntityEncoder.hasUnpairedSurrogate(literal)
        ) {
            textExactFeature(definition, literal)
        } else if (textual && definition.supports(VectorFeatureFamily.TEXT_TERM)) {
            return FingerprintQueryPlan.Universe
        } else {
            return FingerprintQueryPlan.Universe
        }
        return if (literal.equals("null", ignoreCase = true)) {
            anyOf(nonNull, nullFeature(definition))
        } else {
            nonNull
        }
    }

    private fun matches(
        definition: VectorAttributeDefinition,
        attributeType: Class<*>,
        rawValue: Any?
    ): FingerprintQueryPlan {
        if (!hasCompatibleTextEncoding(attributeType)) return FingerprintQueryPlan.Universe
        val pattern = predicateLiteral(rawValue)
        if (runCatching { Regex(pattern) }.isFailure) return FingerprintQueryPlan.Universe
        val exactLiteral = exactRegexLiteral(pattern)
        val requiredLiteral = exactLiteral ?: requiredRegexLiteral(pattern)
            ?: return FingerprintQueryPlan.Universe
        val nonNull = if (exactLiteral != null) {
            when {
                definition.supports(VectorFeatureFamily.TEXT_EXACT) &&
                    !VectorEntityEncoder.hasUnpairedSurrogate(exactLiteral) ->
                    textExactFeature(definition, exactLiteral)
                definition.supports(VectorFeatureFamily.TEXT_NGRAM) ->
                    contains(definition, attributeType, exactLiteral, ignoreCase = false)
                else -> return FingerprintQueryPlan.Universe
            }
        } else {
            contains(definition, attributeType, requiredLiteral, ignoreCase = false)
        }
        return if (exactLiteral == "null") anyOf(nonNull, nullFeature(definition)) else nonNull
    }

    private fun startsWith(
        definition: VectorAttributeDefinition,
        attributeType: Class<*>,
        rawValue: Any?
    ): FingerprintQueryPlan {
        if (!hasCompatibleTextEncoding(attributeType)) return FingerprintQueryPlan.Universe
        if (!definition.supports(VectorFeatureFamily.TEXT_PREFIX)) return FingerprintQueryPlan.Universe
        if (configuration.maxTextPrefixLength <= 0) return FingerprintQueryPlan.Universe

        val rawPrefix = predicateLiteral(rawValue)
        if (VectorEntityEncoder.hasUnpairedSurrogate(rawPrefix)) return FingerprintQueryPlan.Universe
        val foldedCodePoints = VectorEntityEncoder.comparisonFold(rawPrefix).codePoints().toArray()
        if (foldedCodePoints.isEmpty()) {
            return anyOf(presentFeature(definition), nullFeature(definition))
        }

        val routedLength = minOf(foldedCodePoints.size, configuration.maxTextPrefixLength)
        val routedPrefix = String(foldedCodePoints, 0, routedLength)
        val routed = feature(
            "attribute:${definition.name}/text/prefix:${VectorEntityEncoder.escape(routedPrefix)}"
        )

        // Onyx's existing comparison treats a null attribute as the literal string "null".
        return if ("null".startsWith(rawPrefix)) anyOf(routed, nullFeature(definition)) else routed
    }

    private fun contains(
        definition: VectorAttributeDefinition,
        attributeType: Class<*>,
        rawValue: Any?,
        ignoreCase: Boolean
    ): FingerprintQueryPlan {
        if (!hasCompatibleTextEncoding(attributeType)) return FingerprintQueryPlan.Universe
        if (!definition.supports(VectorFeatureFamily.TEXT_NGRAM)) return FingerprintQueryPlan.Universe

        val rawNeedle = predicateLiteral(rawValue)
        if (VectorEntityEncoder.hasUnpairedSurrogate(rawNeedle)) return FingerprintQueryPlan.Universe
        val foldedCodePoints = VectorEntityEncoder.comparisonFold(rawNeedle).codePoints().toArray()

        if (foldedCodePoints.isEmpty()) {
            return anyOf(presentFeature(definition), nullFeature(definition))
        }
        if (configuration.textNGramSize <= 0) return FingerprintQueryPlan.Universe

        val gramSize = minOf(foldedCodePoints.size, configuration.textNGramSize)

        val grams = LinkedHashSet<String>()
        for (start in 0..foldedCodePoints.size - gramSize) {
            grams += String(foldedCodePoints, start, gramSize)
        }
        val gramPlans: List<FingerprintQueryPlan> = grams.map { gram ->
            feature("attribute:${definition.name}/text/gram:${VectorEntityEncoder.escape(gram)}")
        }
        val routed = allOf(*gramPlans.toTypedArray())

        val nullMatches = "null".contains(rawNeedle, ignoreCase = ignoreCase)
        return if (nullMatches) anyOf(routed, nullFeature(definition)) else routed
    }

    private fun hasCompatibleTextEncoding(attributeType: Class<*>): Boolean {
        val boxed = boxed(attributeType)
        return CharSequence::class.java.isAssignableFrom(boxed) ||
            Date::class.java.isAssignableFrom(boxed) ||
            boxed.isEnum ||
            boxed in TEXT_SCALAR_TYPES
    }

    private fun predicateLiteral(value: Any?): String =
        value?.let(VectorValueCodec::predicateText) ?: "null"

    private fun textExactFeature(
        definition: VectorAttributeDefinition,
        value: String
    ): FingerprintQueryPlan = feature(
        "attribute:${definition.name}/text/exact:" +
            VectorEntityEncoder.escape(VectorEntityEncoder.comparisonFold(value))
    )

    private fun scalarFeature(
        definition: VectorAttributeDefinition,
        value: Any
    ): FingerprintQueryPlan {
        if (definition.supports(VectorFeatureFamily.CATEGORICAL)) {
            return categoryFeature(definition, value)
        }
        if (!definition.supports(VectorFeatureFamily.INTERVAL)) return FingerprintQueryPlan.Universe
        // String equality and IN retain the complete raw value. Its interval coordinate is a
        // deliberately truncated ordering hint and is only appropriate inside range().
        if (value is String || value is Enum<*>) return FingerprintQueryPlan.Universe
        val result = runCatching { VectorValueCodec.intervalCoordinate(value) }
        if (result.isFailure) return FingerprintQueryPlan.Universe
        val coordinate = result.getOrNull()
        return if (coordinate == null) {
            FingerprintQueryPlan.Universe
        } else {
            intervalFeature(definition, coordinate, IntervalNode(coordinate.bits, coordinate.coordinate))
        }
    }

    private fun intervalFeature(
        definition: VectorAttributeDefinition,
        coordinate: IntervalCoordinate,
        node: IntervalNode
    ): FingerprintQueryPlan = feature(VectorEntityEncoder.intervalFeatureSuffix(definition, coordinate, node))

    private fun categoryFeature(
        definition: VectorAttributeDefinition,
        value: Any
    ): FingerprintQueryPlan = feature(VectorEntityEncoder.categoricalFeatureSuffix(definition, value))

    private fun nullFeature(definition: VectorAttributeDefinition): FingerprintQueryPlan =
        feature("attribute:${definition.name}/null")

    private fun presentFeature(definition: VectorAttributeDefinition): FingerprintQueryPlan =
        feature("attribute:${definition.name}/present")

    private fun feature(logicalSuffix: String): FingerprintQueryPlan.Feature {
        val logicalFeature = "$namespace/$logicalSuffix"
        return FingerprintQueryPlan.Feature(
            logicalFeature,
            VectorFeatureHasher.fingerprint(logicalFeature, configuration.entropy)
        )
    }

    private fun coordinateOrNull(value: Any): IntervalCoordinate? =
        runCatching { VectorValueCodec.intervalCoordinate(value) }.getOrNull()

    private fun hasSearchRoute(value: Any?): Boolean {
        val search = resolveVectorSearchQuery(value) ?: return false
        if (search.semantic != null) return true
        return !search.text.isNullOrBlank() && configuration.supports(VectorFeatureFamily.TEXT_TERM)
    }

    private fun addFloatingBoundaryCandidates(
        output: MutableList<FingerprintQueryPlan>,
        definition: VectorAttributeDefinition,
        attributeType: Class<*>
    ) {
        when (boxed(attributeType)) {
            java.lang.Float::class.java -> listOf(
                -0.0f, 0.0f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY
            ).forEach { output += scalarFeature(definition, it) }
            java.lang.Double::class.java -> listOf(
                -0.0, 0.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY
            ).forEach { output += scalarFeature(definition, it) }
        }
    }

    private fun sameDomain(first: IntervalCoordinate, second: IntervalCoordinate): Boolean =
        first.bits == second.bits && first.domain == second.domain

    private fun isCompatible(attributeType: Class<*>, value: Any): Boolean = boxed(attributeType).isInstance(value)

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        else -> type
    }

    private fun unpackValues(value: Any?): List<Any?>? = when {
        value is Iterable<*> -> value.toList()
        value != null && value.javaClass.isArray -> List(ReflectArray.getLength(value)) {
            ReflectArray.get(value, it)
        }
        else -> null
    }

    private fun unpackPair(value: Any?): Pair<Any?, Any?>? = when (value) {
        is Pair<*, *> -> value.first to value.second
        is Iterable<*> -> value.iterator().let { iterator ->
            if (!iterator.hasNext()) return@let null
            val first = iterator.next()
            if (!iterator.hasNext()) return@let null
            val second = iterator.next()
            if (iterator.hasNext()) null else first to second
        }
        null -> null
        else -> if (value.javaClass.isArray && ReflectArray.getLength(value) == 2) {
            ReflectArray.get(value, 0) to ReflectArray.get(value, 1)
        } else {
            null
        }
    }

    private fun exactRegexLiteral(pattern: String): String? {
        var literal = pattern
        if (literal.startsWith('^')) literal = literal.substring(1)
        if (literal.endsWith('$')) literal = literal.dropLast(1)
        return literal.takeUnless { candidate -> candidate.any { it in REGEX_META_CHARACTERS } }
    }

    /**
     * Extracts a literal that every match must contain from the deliberately small regex form
     * `literal.*literal`. More general expressions remain explicitly unroutable instead of
     * silently widening to every record.
     */
    private fun requiredRegexLiteral(pattern: String): String? {
        var body = pattern
        if (body.startsWith('^')) body = body.substring(1)
        if (body.endsWith('$') && !body.endsWith("\\$")) body = body.dropLast(1)

        val parts = body.split(".*")
        if (parts.size == 1 || parts.any { part -> part.any { it in REGEX_META_CHARACTERS } }) {
            return null
        }
        return parts.filter(String::isNotEmpty).maxByOrNull(String::length)
    }

    private fun complement(plan: FingerprintQueryPlan): FingerprintQueryPlan = when (plan) {
        FingerprintQueryPlan.Universe -> FingerprintQueryPlan.Universe
        else -> FingerprintQueryPlan.Complement(plan)
    }

    private fun anyOf(vararg plans: FingerprintQueryPlan): FingerprintQueryPlan = anyOf(plans.asList())

    private fun anyOf(plans: Collection<FingerprintQueryPlan>): FingerprintQueryPlan {
        val flattened = LinkedHashSet<FingerprintQueryPlan>()
        plans.forEach { plan ->
            when (plan) {
                FingerprintQueryPlan.Universe -> return FingerprintQueryPlan.Universe
                FingerprintQueryPlan.Empty -> Unit
                is FingerprintQueryPlan.AnyOf -> flattened.addAll(plan.operands)
                else -> flattened += plan
            }
        }
        return when (flattened.size) {
            0 -> FingerprintQueryPlan.Empty
            1 -> flattened.first()
            else -> FingerprintQueryPlan.AnyOf(flattened.toList())
        }
    }

    private fun allOf(vararg plans: FingerprintQueryPlan): FingerprintQueryPlan {
        val flattened = LinkedHashSet<FingerprintQueryPlan>()
        plans.forEach { plan ->
            when (plan) {
                FingerprintQueryPlan.Empty -> return FingerprintQueryPlan.Empty
                FingerprintQueryPlan.Universe -> Unit
                is FingerprintQueryPlan.AllOf -> flattened.addAll(plan.operands)
                else -> flattened += plan
            }
        }
        return when (flattened.size) {
            0 -> FingerprintQueryPlan.Universe
            1 -> flattened.first()
            else -> FingerprintQueryPlan.AllOf(flattened.toList())
        }
    }

    private companion object {
        const val REGEX_META_CHARACTERS = ".^$*+?{}[]()|\\"

        val TEXT_SCALAR_TYPES = setOf(
            java.lang.Byte::class.java,
            java.lang.Short::class.java,
            java.lang.Integer::class.java,
            java.lang.Long::class.java,
            java.lang.Float::class.java,
            java.lang.Double::class.java,
            java.lang.Boolean::class.java,
            java.lang.Character::class.java
        )

        val FLOATING_TYPES = setOf(
            java.lang.Float::class.java,
            java.lang.Double::class.java
        )
    }
}

/** Non-truncating feature-candidate lookup used by [FingerprintQueryExecutor]. */
interface FingerprintCandidateLookup {
    /** Returns a conservative posting superset; callers must still verify the exact predicate. */
    fun findFeature(feature: FeatureFingerprint): Set<Long>

    /**
     * Intersects [candidates] with a feature posting. Implementations may override this to probe
     * the posting directly instead of materializing every ID in it.
     */
    fun retainFeatureCandidates(feature: FeatureFingerprint, candidates: MutableSet<Long>) {
        candidates.retainAll(findFeature(feature))
    }
}

/**
 * Executes a candidate plan against one partition's fingerprint index.
 *
 * [searchCandidates] must return a complete candidate superset. An unroutable plan fails
 * explicitly; this executor never disguises a table scan as an index lookup.
 */
class FingerprintQueryExecutor(
    private val planner: FingerprintQueryPlanner,
    private val lookup: FingerprintCandidateLookup,
    private val searchCandidates: ((FingerprintQueryPlan.Search) -> Set<Long>?)? = null
) {
    @JvmOverloads
    constructor(
        descriptor: EntityDescriptor,
        indexInteractor: FingerprintIndexInteractor,
        searchCandidates: ((FingerprintQueryPlan.Search) -> Set<Long>?)? = null
    ) : this(
        FingerprintQueryPlanner(descriptor),
        object : FingerprintCandidateLookup {
            override fun findFeature(feature: FeatureFingerprint): Set<Long> =
                indexInteractor.findFeatureCandidates(feature)

            override fun retainFeatureCandidates(
                feature: FeatureFingerprint,
                candidates: MutableSet<Long>
            ) = indexInteractor.retainFeatureCandidates(feature, candidates)
        },
        searchCandidates
    )

    fun plan(criteria: QueryCriteria): FingerprintQueryPlan = planner.compile(criteria)

    /** Returns an untruncated candidate superset. This is not a final predicate result. */
    fun candidateIds(criteria: QueryCriteria): Set<Long> = candidateIds(plan(criteria))

    /** Returns an untruncated candidate superset. This is not a final predicate result. */
    fun candidateIds(plan: FingerprintQueryPlan): Set<Long> = execute(plan)

    /**
     * Returns candidates inside an already selected AND-domain. Small restricted domains can be
     * probed directly without first materializing a much broader global posting.
     */
    fun candidateIds(plan: FingerprintQueryPlan, restrictedTo: Set<Long>): Set<Long> =
        executeRestricted(plan, restrictedTo)

    /** Applies the caller's authoritative predicate evaluator to every routed candidate. */
    fun verifiedIds(criteria: QueryCriteria, exactPredicate: (Long) -> Boolean): Set<Long> =
        candidateIds(criteria).filterTo(LinkedHashSet(), exactPredicate)

    private fun execute(plan: FingerprintQueryPlan): Set<Long> = when (plan) {
        FingerprintQueryPlan.Universe -> throw IllegalStateException(
            "The predicate has no fingerprint route; refusing to scan the indexed domain"
        )
        FingerprintQueryPlan.Empty -> emptySet()
        is FingerprintQueryPlan.Complement -> throw IllegalStateException(
            "A fingerprint complement requires authoritative verification before subtraction"
        )
        is FingerprintQueryPlan.Feature -> lookup.findFeature(plan.fingerprint).toCollection(LinkedHashSet())
        is FingerprintQueryPlan.Search -> searchCandidates?.invoke(plan)
            ?.toCollection(LinkedHashSet())
            ?: throw IllegalStateException("No complete fingerprint search route is available")
        is FingerprintQueryPlan.AnyOf -> LinkedHashSet<Long>().apply {
            plan.operands.forEach { addAll(execute(it)) }
        }
        is FingerprintQueryPlan.AllOf -> {
            val iterator = plan.operands.iterator()
            if (!iterator.hasNext()) {
                throw IllegalStateException("An empty fingerprint intersection has no selective route")
            } else {
                execute(iterator.next()).toMutableSet().apply {
                    while (iterator.hasNext() && isNotEmpty()) retain(this, iterator.next())
                }
            }
        }
    }

    private fun executeRestricted(
        plan: FingerprintQueryPlan,
        restrictedTo: Set<Long>
    ): Set<Long> {
        when (plan) {
            FingerprintQueryPlan.Universe -> throw IllegalStateException(
                "The predicate has no fingerprint route; refusing to scan the indexed domain"
            )
            is FingerprintQueryPlan.Complement -> throw IllegalStateException(
                "A fingerprint complement requires authoritative verification before subtraction"
            )
            is FingerprintQueryPlan.AllOf -> if (plan.operands.isEmpty()) {
                throw IllegalStateException("An empty fingerprint intersection has no selective route")
            }
            else -> Unit
        }
        if (restrictedTo.isEmpty()) return emptySet()

        return when (plan) {
            FingerprintQueryPlan.Universe,
            is FingerprintQueryPlan.Complement -> error("Unsupported restricted fingerprint plan")
            FingerprintQueryPlan.Empty -> emptySet()
            is FingerprintQueryPlan.Feature -> restrictedTo.toCollection(LinkedHashSet()).apply {
                lookup.retainFeatureCandidates(plan.fingerprint, this)
            }
            is FingerprintQueryPlan.Search -> execute(plan).filterTo(LinkedHashSet()) { it in restrictedTo }
            is FingerprintQueryPlan.AnyOf -> LinkedHashSet<Long>().apply {
                plan.operands.forEach { addAll(executeRestricted(it, restrictedTo)) }
            }
            is FingerprintQueryPlan.AllOf -> restrictedTo.toCollection(LinkedHashSet()).apply {
                plan.operands.forEach { operand ->
                    if (isNotEmpty()) retainRestricted(this, operand)
                }
            }
        }
    }

    private fun retain(candidates: MutableSet<Long>, plan: FingerprintQueryPlan) {
        if (plan is FingerprintQueryPlan.Feature) {
            lookup.retainFeatureCandidates(plan.fingerprint, candidates)
        } else {
            candidates.retainAll(execute(plan))
        }
    }

    private fun retainRestricted(candidates: MutableSet<Long>, plan: FingerprintQueryPlan) {
        if (plan is FingerprintQueryPlan.Feature) {
            lookup.retainFeatureCandidates(plan.fingerprint, candidates)
        } else {
            candidates.retainAll(executeRestricted(plan, candidates))
        }
    }

    companion object {
        /** Resolves the fingerprint index belonging to a concrete partition descriptor. */
        @JvmStatic
        fun forPartition(descriptor: EntityDescriptor, context: SchemaContext): FingerprintQueryExecutor {
            val indexDescriptor = requireNotNull(descriptor.indexes[VectorManagedEntity.REPRESENTATION_FIELD]) {
                "${descriptor.entityClass.name} has no fingerprint representation index"
            }
            val interactor = context.getIndexInteractor(indexDescriptor)
            require(interactor is FingerprintIndexInteractor) {
                "${indexDescriptor.name} is not backed by FingerprintIndexInteractor"
            }
            return FingerprintQueryExecutor(descriptor, interactor)
        }
    }
}
