package com.onyx.vector

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FingerprintQueryPlannerTest {
    private val planner = FingerprintQueryPlanner(EntityDescriptor(PlannerEntity::class.java))

    @Test
    fun equalityNullInAndSignedZeroCompileToStoredFeatures() {
        assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("age", QueryCriteriaOperator.EQUAL, 73))
        )
        assertTrue(
            features(planner.compile(QueryCriteria("age", QueryCriteriaOperator.IS_NULL)))
                .single().logicalFeature.endsWith("/attribute:age/null")
        )

        val inPlan = planner.compile(QueryCriteria("age", QueryCriteriaOperator.IN, listOf(70, 73, 85)))
        assertEquals(3, features(inPlan).size)

        val zeroPlan = planner.compile(QueryCriteria("ratio", QueryCriteriaOperator.EQUAL, 0.0))
        assertEquals(2, features(zeroPlan).size)
        assertEquals(2, features(zeroPlan).map { it.fingerprint }.toSet().size)
    }

    @Test
    fun betweenPairListAndPrimitiveArrayUseTheSameMinimalCover() {
        val pairPlan = planner.compile(
            QueryCriteria("age", QueryCriteriaOperator.BETWEEN, 70 to 85)
        )
        val listPlan = planner.compile(
            QueryCriteria("age", QueryCriteriaOperator.BETWEEN, listOf(70, 85))
        )
        val arrayPlan = planner.compile(
            QueryCriteria("age", QueryCriteriaOperator.BETWEEN, intArrayOf(70, 85))
        )

        assertEquals(pairPlan, listPlan)
        assertEquals(pairPlan, arrayPlan)
        assertEquals(4, features(pairPlan).size)
        assertTrue(features(pairPlan).all { "/attribute:age/interval:i32:32/interval:" in it.logicalFeature })
    }

    @Test
    fun exactNumericAndDateScalesExcludeStrictBoundaryLeaves() {
        val descriptor = EntityDescriptor(PlannerEntity::class.java)
        val configuration = VectorManagedConfiguration.forClass(PlannerEntity::class.java)

        val priceDefinition = configuration.attributes.single { it.name == "price" }
        val priceCoordinate = VectorValueCodec.intervalCoordinate(1.001)!!
        val pricePath = BinaryIntervalTree.path(priceCoordinate.coordinate, priceCoordinate.bits)
            .map { intervalSuffix(priceDefinition, priceCoordinate, it) }
            .toSet()
        val pricePlan = planner.compile(
            QueryCriteria("price", QueryCriteriaOperator.GREATER_THAN, 1.001)
        )
        assertTrue(features(pricePlan).none { feature ->
            pricePath.any { feature.logicalFeature.endsWith("/$it") }
        })
        assertEquals("f64", priceCoordinate.domain)

        val dateDefinition = configuration.attributes.single { it.name == "created" }
        val threshold = Date(1_234_567L)
        val dateCoordinate = VectorValueCodec.intervalCoordinate(threshold)!!
        val datePath = BinaryIntervalTree.path(dateCoordinate.coordinate, dateCoordinate.bits)
            .map { intervalSuffix(dateDefinition, dateCoordinate, it) }
            .toSet()
        val datePlan = FingerprintQueryPlanner(descriptor).compile(
            QueryCriteria("created", QueryCriteriaOperator.GREATER_THAN, threshold)
        )
        assertTrue(features(datePlan).none { feature ->
            datePath.any { feature.logicalFeature.endsWith("/$it") }
        })
        assertEquals("date:MILLISECONDS", dateCoordinate.domain)
    }

    @Test
    fun unsupportedClausesWidenWhileNegationRetainsItsRoutedPositivePlan() {
        val supported = QueryCriteria("age", QueryCriteriaOperator.EQUAL, 73)
        val unsupportedAnd = QueryCriteria("missing", QueryCriteriaOperator.CONTAINS, "x").apply {
            isAnd = true
        }
        supported.subCriteria += unsupportedAnd
        assertIs<FingerprintQueryPlan.Feature>(planner.compile(supported))

        val supportedOr = QueryCriteria("age", QueryCriteriaOperator.EQUAL, 73)
        val unsupportedOr = QueryCriteria("missing", QueryCriteriaOperator.CONTAINS, "x").apply {
            isOr = true
        }
        supportedOr.subCriteria += unsupportedOr
        assertEquals(FingerprintQueryPlan.Universe, planner.compile(supportedOr))

        val negated = QueryCriteria("age", QueryCriteriaOperator.EQUAL, 73).apply { isNot = true }
        assertIs<FingerprintQueryPlan.Complement>(planner.compile(negated))
        assertEquals(
            FingerprintQueryPlan.Universe,
            planner.compile(QueryCriteria("age", QueryCriteriaOperator.EQUAL, 73L))
        )
    }

    @Test
    fun literalAndMandatorySubstringRegexPredicatesRouteAndFullTextRemainsExplicit() {
        val like = planner.compile(QueryCriteria("name", QueryCriteriaOperator.LIKE, "ALICE"))
        val matches = planner.compile(QueryCriteria("name", QueryCriteriaOperator.MATCHES, "^Alice$"))

        assertIs<FingerprintQueryPlan.Feature>(like)
        assertIs<FingerprintQueryPlan.Feature>(matches)
        assertTrue(
            features(planner.compile(QueryCriteria("name", QueryCriteriaOperator.MATCHES, "Alice.*"))).isNotEmpty()
        )
        assertIs<FingerprintQueryPlan.Search>(
            planner.compile(QueryCriteria(Query.FULL_TEXT_ATTRIBUTE, QueryCriteriaOperator.MATCHES, "liquid QQQ"))
        )
    }

    @Test
    fun startsWithUsesCodePointPrefixesAndSafelyTruncatesLongNeedles() {
        val short = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("name", QueryCriteriaOperator.STARTS_WITH, "Ali"))
        )
        assertTrue(short.logicalFeature.endsWith("/attribute:name/text/prefix:ali"))

        val longNeedle = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val long = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("name", QueryCriteriaOperator.STARTS_WITH, longNeedle))
        )
        assertTrue(long.logicalFeature.endsWith("/attribute:name/text/prefix:abcdefghijklmnopqrstuvwxyz012345"))

        val empty = planner.compile(QueryCriteria("name", QueryCriteriaOperator.STARTS_WITH, ""))
        assertEquals(2, features(empty).size)
        assertTrue(features(empty).any { it.logicalFeature.endsWith("/attribute:name/present") })
        assertTrue(features(empty).any { it.logicalFeature.endsWith("/attribute:name/null") })
    }

    @Test
    fun containsUsesOneThroughThreeGramsSoShortNeedlesRemainRoutable() {
        val short = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("name", QueryCriteriaOperator.CONTAINS, "li"))
        )
        assertTrue(short.logicalFeature.endsWith("/attribute:name/text/gram:li"))

        val exactGram = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("name", QueryCriteriaOperator.CONTAINS, "UID"))
        )
        assertTrue(exactGram.logicalFeature.endsWith("/attribute:name/text/gram:uid"))

        val long = planner.compile(
            QueryCriteria("name", QueryCriteriaOperator.CONTAINS_IGNORE_CASE, "Liquid")
        )
        val grams = features(long).map { it.logicalFeature.substringAfterLast("/text/gram:") }.toSet()
        assertEquals(setOf("liq", "iqu", "qui", "uid"), grams)
    }

    @Test
    fun textRoutingIncludesNullWhenLegacyStringComparisonCanMatchIt() {
        val prefix = planner.compile(
            QueryCriteria("name", QueryCriteriaOperator.STARTS_WITH, "null")
        )
        assertEquals(2, features(prefix).size)
        assertTrue(features(prefix).any { it.logicalFeature.endsWith("/attribute:name/null") })

        val contains = planner.compile(
            QueryCriteria("name", QueryCriteriaOperator.CONTAINS_IGNORE_CASE, "NULL")
        )
        assertTrue(features(contains).any { it.logicalFeature.endsWith("/attribute:name/null") })
    }

    @Test
    fun negatedTextClausesRetainPositiveRoutesForEveryScalar() {
        listOf(
            QueryCriteriaOperator.NOT_STARTS_WITH,
            QueryCriteriaOperator.NOT_CONTAINS,
            QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE
        ).forEach { operator ->
            assertIs<FingerprintQueryPlan.Complement>(
                planner.compile(QueryCriteria("name", operator, "liquid")),
                operator.name
            )
        }
        assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("age", QueryCriteriaOperator.CONTAINS, "123"))
        )
    }

    @Test
    fun executorNeverTruncatesAndRequiresExplicitVerification() {
        val first = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("age", QueryCriteriaOperator.EQUAL, 70))
        )
        val second = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("age", QueryCriteriaOperator.EQUAL, 73))
        )
        val lookup = object : FingerprintCandidateLookup {
            override fun findFeature(feature: FeatureFingerprint): Set<Long> = when (feature) {
                first.fingerprint -> setOf(1L, 2L, 3L)
                second.fingerprint -> setOf(3L, 4L)
                else -> emptySet()
            }
        }
        val executor = FingerprintQueryExecutor(planner, lookup)

        assertFailsWith<IllegalStateException> {
            executor.candidateIds(FingerprintQueryPlan.Universe)
        }
        assertFailsWith<IllegalStateException> {
            executor.candidateIds(FingerprintQueryPlan.Complement(first))
        }
        assertFailsWith<IllegalStateException> {
            executor.candidateIds(FingerprintQueryPlan.Universe, emptySet())
        }
        assertFailsWith<IllegalStateException> {
            executor.candidateIds(FingerprintQueryPlan.Complement(first), emptySet())
        }
        assertFailsWith<IllegalStateException> {
            executor.candidateIds(FingerprintQueryPlan.AllOf(emptyList()), emptySet())
        }
        assertEquals(
            setOf(1L, 2L, 3L, 4L),
            executor.candidateIds(FingerprintQueryPlan.AnyOf(listOf(first, second)))
        )
        assertEquals(
            setOf(3L),
            executor.candidateIds(FingerprintQueryPlan.AllOf(listOf(first, second)))
        )
        assertEquals(
            setOf(2L),
            FingerprintQueryExecutor(planner, lookup) { null }
                .verifiedIds(QueryCriteria("age", QueryCriteriaOperator.EQUAL, 70)) { it % 2L == 0L }
        )
    }

    @Test
    fun rawConjunctionCandidatesAreIntersectedBeforeAuthoritativeVerification() {
        val criteria = QueryCriteria("name", QueryCriteriaOperator.CONTAINS, "abcd")
        val plan = assertIs<FingerprintQueryPlan.AllOf>(planner.compile(criteria))
        assertEquals(2, plan.operands.size)
        val first = assertIs<FingerprintQueryPlan.Feature>(plan.operands[0])
        val second = assertIs<FingerprintQueryPlan.Feature>(plan.operands[1])
        val rawPostings = mapOf(
            first.fingerprint to setOf(1L, 2L, 99L),
            second.fingerprint to setOf(2L, 3L, 99L)
        )
        val lookup = object : FingerprintCandidateLookup {
            override fun findFeature(feature: FeatureFingerprint): Set<Long> =
                rawPostings[feature].orEmpty()
        }
        val executor = FingerprintQueryExecutor(planner, lookup)
        val verifiedCandidates = ArrayList<Long>()

        assertEquals(setOf(2L, 99L), executor.candidateIds(plan))
        assertEquals(
            setOf(2L),
            executor.verifiedIds(criteria) { candidate ->
                verifiedCandidates += candidate
                candidate == 2L
            }
        )
        assertEquals(listOf(2L, 99L), verifiedCandidates)
    }

    @Test
    fun restrictedConjunctionProbesTheExistingDomainWithoutGlobalPostingReads() {
        val first = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("age", QueryCriteriaOperator.EQUAL, 70))
        )
        val second = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("age", QueryCriteriaOperator.EQUAL, 73))
        )
        val rawPostings = mapOf(
            first.fingerprint to setOf(1L, 2L, 99L),
            second.fingerprint to setOf(2L, 3L, 99L)
        )
        var globalReads = 0
        val retainedFeatures = ArrayList<FeatureFingerprint>()
        val lookup = object : FingerprintCandidateLookup {
            override fun findFeature(feature: FeatureFingerprint): Set<Long> {
                globalReads++
                return rawPostings[feature].orEmpty()
            }

            override fun retainFeatureCandidates(
                feature: FeatureFingerprint,
                candidates: MutableSet<Long>
            ) {
                retainedFeatures += feature
                candidates.retainAll(rawPostings[feature].orEmpty())
            }
        }
        val executor = FingerprintQueryExecutor(planner, lookup)
        val plan = FingerprintQueryPlan.AllOf(listOf(first, second))

        assertEquals(
            setOf(2L, 99L),
            executor.candidateIds(plan, linkedSetOf(2L, 3L, 4L, 99L))
        )
        assertEquals(0, globalReads)
        assertEquals(listOf(first.fingerprint, second.fingerprint), retainedFeatures)
    }

    @Test
    fun restrictedUnionAppliesEveryBranchToTheOriginalDomain() {
        val first = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("age", QueryCriteriaOperator.EQUAL, 70))
        )
        val second = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("age", QueryCriteriaOperator.EQUAL, 73))
        )
        val rawPostings = mapOf(
            first.fingerprint to setOf(1L, 2L),
            second.fingerprint to setOf(2L, 3L)
        )
        val probedDomains = ArrayList<Set<Long>>()
        val lookup = object : FingerprintCandidateLookup {
            override fun findFeature(feature: FeatureFingerprint): Set<Long> =
                error("Restricted union must not materialize a global posting")

            override fun retainFeatureCandidates(
                feature: FeatureFingerprint,
                candidates: MutableSet<Long>
            ) {
                probedDomains += candidates.toSet()
                candidates.retainAll(rawPostings[feature].orEmpty())
            }
        }
        val restrictedTo = linkedSetOf(1L, 2L, 3L, 4L)
        val executor = FingerprintQueryExecutor(planner, lookup)

        assertEquals(
            setOf(1L, 2L, 3L),
            executor.candidateIds(FingerprintQueryPlan.AnyOf(listOf(first, second)), restrictedTo)
        )
        assertEquals(listOf<Set<Long>>(restrictedTo, restrictedTo), probedDomains)
    }

    private fun features(plan: FingerprintQueryPlan): List<FingerprintQueryPlan.Feature> = when (plan) {
        FingerprintQueryPlan.Empty,
        FingerprintQueryPlan.Universe,
        is FingerprintQueryPlan.Search -> emptyList()
        is FingerprintQueryPlan.Complement -> features(plan.operand)
        is FingerprintQueryPlan.Feature -> listOf(plan)
        is FingerprintQueryPlan.AllOf -> plan.operands.flatMap(::features)
        is FingerprintQueryPlan.AnyOf -> plan.operands.flatMap(::features)
    }

    private fun intervalSuffix(
        definition: VectorAttributeDefinition,
        coordinate: IntervalCoordinate,
        node: IntervalNode
    ): String = "attribute:${definition.name}/interval:${coordinate.domain}:${coordinate.bits}/${node.canonicalToken}"

    @Entity
    private class PlannerEntity : VectorManagedEntity() {
        @Identifier
        @Attribute(nullable = false)
        var id: Long = 0L

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
        var age: Int = 0

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
        var price: Double = 0.0

        @Attribute
        var ratio: Double = 0.0

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
        var name: String = ""

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
        var created: Date = Date(0L)
    }
}
