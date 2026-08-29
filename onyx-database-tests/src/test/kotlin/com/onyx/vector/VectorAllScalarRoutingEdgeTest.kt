package com.onyx.vector

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import org.junit.Test
import java.util.Date
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Focused contracts for the scalar routes that do not fit the operator integration matrix. */
class VectorAllScalarRoutingEdgeTest {
    private val descriptor = EntityDescriptor(AllScalarRoutingEntity::class.java)
    private val configuration = VectorManagedConfiguration.forClass(AllScalarRoutingEntity::class.java)
    private val planner = FingerprintQueryPlanner(descriptor)

    @Test
    fun stringCoordinatesPreserveUtf16OrderAndExposeTruncationCollisions() {
        val naturallyOrdered = listOf(
            "",
            "A",
            "AA",
            "AB",
            "B",
            "\uD7FF",
            "\uD83D\uDE00",
            "\uE000",
            "\uFFFF"
        )
        assertEquals(naturallyOrdered, naturallyOrdered.sorted())

        val coordinates = naturallyOrdered.map { value ->
            requireNotNull(VectorValueCodec.intervalCoordinate(value))
        }
        coordinates.zipWithNext().forEachIndexed { index, (first, second) ->
            assertTrue(
                first.coordinate < second.coordinate,
                "UTF-16 order was not preserved for ${naturallyOrdered[index]} < ${naturallyOrdered[index + 1]}"
            )
        }
        assertTrue(coordinates.all { it.lossy })
        assertTrue(coordinates.all { it.domain.startsWith("string:utf16-prefix") })

        val first = "abcd-alpha"
        val second = "abcd-omega"
        assertTrue(first < second)
        val firstCoordinate = requireNotNull(VectorValueCodec.intervalCoordinate(first))
        val secondCoordinate = requireNotNull(VectorValueCodec.intervalCoordinate(second))
        assertEquals(firstCoordinate, secondCoordinate, "suffixes beyond the routed UTF-16 prefix must collide")

        val firstEquality = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("text", QueryCriteriaOperator.EQUAL, first))
        )
        val secondEquality = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("text", QueryCriteriaOperator.EQUAL, second))
        )
        assertTrue(firstEquality.logicalFeature.endsWith("/attribute:text/category:abcd-alpha"))
        assertTrue(secondEquality.logicalFeature.endsWith("/attribute:text/category:abcd-omega"))
        assertNotEquals(firstEquality.fingerprint, secondEquality.fingerprint)
    }

    @Test
    fun strictStringBoundsRetainLossyBoundaryWhileLosslessBoundsExcludeIt() {
        val textDefinition = configuration.attributes.single { it.name == "text" }
        val stringBound = "abcd-boundary"
        val stringCoordinate = requireNotNull(VectorValueCodec.intervalCoordinate(stringBound))
        assertTrue(stringCoordinate.lossy)
        val stringBoundaryPath = BinaryIntervalTree.path(stringCoordinate.coordinate, stringCoordinate.bits)
            .map { intervalSuffix(textDefinition, stringCoordinate, it) }
            .toSet()

        listOf(QueryCriteriaOperator.GREATER_THAN, QueryCriteriaOperator.LESS_THAN).forEach { operator ->
            val plan = planner.compile(QueryCriteria("text", operator, stringBound))
            assertTrue(
                features(plan).any { feature ->
                    stringBoundaryPath.any { suffix -> feature.logicalFeature.endsWith("/$suffix") }
                },
                "$operator dropped candidates sharing the truncated String boundary coordinate"
            )
        }

        val rankDefinition = configuration.attributes.single { it.name == "rank" }
        val rankCoordinate = requireNotNull(VectorValueCodec.intervalCoordinate(17))
        assertTrue(!rankCoordinate.lossy)
        val rankBoundaryPath = BinaryIntervalTree.path(rankCoordinate.coordinate, rankCoordinate.bits)
            .map { intervalSuffix(rankDefinition, rankCoordinate, it) }
            .toSet()
        listOf(QueryCriteriaOperator.GREATER_THAN, QueryCriteriaOperator.LESS_THAN).forEach { operator ->
            val plan = planner.compile(QueryCriteria("rank", operator, 17))
            assertTrue(
                features(plan).none { feature ->
                    rankBoundaryPath.any { suffix -> feature.logicalFeature.endsWith("/$suffix") }
                },
                "$operator retained a numeric boundary route"
            )
        }
    }

    @Test
    fun booleanAndEnumCoordinatesFollowTheirNaturalOrder() {
        val falseCoordinate = requireNotNull(VectorValueCodec.intervalCoordinate(false))
        val trueCoordinate = requireNotNull(VectorValueCodec.intervalCoordinate(true))
        assertEquals("bool", falseCoordinate.domain)
        assertEquals(1, falseCoordinate.bits)
        assertEquals(0.toBigInteger(), falseCoordinate.coordinate)
        assertEquals(1.toBigInteger(), trueCoordinate.coordinate)
        assertTrue(falseCoordinate.coordinate < trueCoordinate.coordinate)
        assertTrue(!falseCoordinate.lossy && !trueCoordinate.lossy)

        val enumCoordinates = MisleadingState.entries.map { state ->
            requireNotNull(VectorValueCodec.intervalCoordinate(state))
        }
        assertEquals(
            List(MisleadingState.entries.size) { it.toBigInteger() },
            enumCoordinates.map(IntervalCoordinate::coordinate)
        )
        assertTrue(enumCoordinates.all { it.bits == 2 })
        assertTrue(enumCoordinates.all { it.domain == "enum:${MisleadingState::class.java.name}" })
        assertTrue(enumCoordinates.all { !it.lossy })
    }

    @Test
    fun oneAndTwoCodePointContainsPlansMatchStoredVariableNgrams() {
        val entity = AllScalarRoutingEntity().apply { text = "x\uD83D\uDE00Qy" }
        val representation = VectorEntityEncoder.encode(entity, descriptor)

        val oneCodePoint = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("text", QueryCriteriaOperator.CONTAINS, "Q"))
        )
        assertTrue(oneCodePoint.logicalFeature.endsWith("/attribute:text/text/gram:q"))
        assertTrue(representation.containsFeature(oneCodePoint.fingerprint))

        val twoCodePoints = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("text", QueryCriteriaOperator.CONTAINS, "\uD83D\uDE00Q"))
        )
        assertTrue(twoCodePoints.logicalFeature.endsWith("/attribute:text/text/gram:\uD83D\uDE00q"))
        assertTrue(representation.containsFeature(twoCodePoints.fingerprint))
    }

    @Test
    fun comparisonRoutesPreserveRawUnicodeMatchesAndJavaIgnoreCaseEquivalence() {
        val decomposed = "A\u030Aland"
        assertTrue(decomposed.startsWith("A"))
        assertTrue(decomposed.contains("A"))
        val decomposedRepresentation = VectorEntityEncoder.encode(
            AllScalarRoutingEntity().apply { text = decomposed },
            descriptor
        )

        listOf(QueryCriteriaOperator.STARTS_WITH, QueryCriteriaOperator.CONTAINS).forEach { operator ->
            val plan = assertIs<FingerprintQueryPlan.Feature>(
                planner.compile(QueryCriteria("text", operator, "A"))
            )
            assertTrue(
                decomposedRepresentation.containsFeature(plan.fingerprint),
                "$operator lost a raw prefix/substring during routing"
            )
        }

        val dotlessI = "\u0131"
        assertTrue("Istanbul".contains(dotlessI, ignoreCase = true))
        val ignoreCasePlan = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("text", QueryCriteriaOperator.CONTAINS_IGNORE_CASE, dotlessI))
        )
        val ignoreCaseRepresentation = VectorEntityEncoder.encode(
            AllScalarRoutingEntity().apply { text = "Istanbul" },
            descriptor
        )
        assertTrue(ignoreCaseRepresentation.containsFeature(ignoreCasePlan.fingerprint))

        val deseretUpper = String(Character.toChars(0x10400))
        val deseretLower = String(Character.toChars(0x10428))
        assertTrue(deseretUpper.equals(deseretLower, ignoreCase = true))
        val supplementaryPlan = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(
                QueryCriteria("text", QueryCriteriaOperator.CONTAINS_IGNORE_CASE, deseretLower)
            )
        )
        val supplementaryRepresentation = VectorEntityEncoder.encode(
            AllScalarRoutingEntity().apply { text = "prefix${deseretUpper}suffix" },
            descriptor
        )
        assertTrue(supplementaryRepresentation.containsFeature(supplementaryPlan.fingerprint))

        listOf(
            QueryCriteria("text", QueryCriteriaOperator.STARTS_WITH, "\uD801"),
            QueryCriteria("text", QueryCriteriaOperator.CONTAINS, "\uDC00"),
            QueryCriteria("text", QueryCriteriaOperator.CONTAINS_IGNORE_CASE, "\uDC00"),
            QueryCriteria("text", QueryCriteriaOperator.LIKE, "\uDC00"),
            QueryCriteria("text", QueryCriteriaOperator.MATCHES, "\uDC00")
        ).forEach { criteria ->
            assertEquals(
                FingerprintQueryPlan.Universe,
                planner.compile(criteria),
                "${criteria.operator} should fall back for an unpaired-surrogate literal"
            )
        }
    }

    @Test
    fun dateTextIsStableUtcAndStoredPerAttribute() = synchronized(TimeZone::class.java) {
        val originalZone = TimeZone.getDefault()
        try {
            val instant = Date(0L)
            val outcomes = listOf("Pacific/Honolulu", "Asia/Kathmandu").map { zoneId ->
                TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
                val text = VectorValueCodec.predicateText(instant)
                val plan = assertIs<FingerprintQueryPlan.Feature>(
                    planner.compile(QueryCriteria("created", QueryCriteriaOperator.LIKE, instant))
                )
                val representation = VectorEntityEncoder.encode(
                    AllScalarRoutingEntity().apply { created = instant },
                    descriptor
                )
                assertTrue(representation.containsFeature(plan.fingerprint))
                text to plan
            }

            assertEquals("1970-01-01T00:00:00.000Z", outcomes[0].first)
            assertEquals(outcomes[0], outcomes[1])
            assertTrue(
                outcomes[0].second.logicalFeature.endsWith(
                    "/attribute:created/text/exact:1970-01-01t00%3a00%3a00.000z"
                )
            )
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun enumEqualityUsesStableNameWhileTextUsesOverriddenToString() {
        val state = MisleadingState.ALPHA
        assertEquals("ALPHA", VectorValueCodec.categorical(state))
        assertEquals("published alpha", VectorValueCodec.text(state))

        val representation = VectorEntityEncoder.encode(
            AllScalarRoutingEntity().apply { this.state = state },
            descriptor
        )
        val equality = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("state", QueryCriteriaOperator.EQUAL, state))
        )
        assertTrue(equality.logicalFeature.endsWith("/attribute:state/category:ALPHA"))
        assertTrue(representation.containsFeature(equality.fingerprint))

        val like = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("state", QueryCriteriaOperator.LIKE, "PUBLISHED ALPHA"))
        )
        assertTrue(like.logicalFeature.endsWith("/attribute:state/text/exact:published alpha"))
        assertTrue(representation.containsFeature(like.fingerprint))
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

    private fun VectorRepresentation.containsFeature(feature: FeatureFingerprint): Boolean {
        if (featureWordCount != feature.wordCount) return false
        val storedWords = featureWords
        var offset = 0
        while (offset < storedWords.size) {
            if ((0 until feature.wordCount).all { word -> storedWords[offset + word] == feature[word] }) {
                return true
            }
            offset += feature.wordCount
        }
        return false
    }

    @Entity(entropy = 64)
    private class AllScalarRoutingEntity : VectorManagedEntity() {
        @Identifier
        var id: Long = 0L

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
        var text: String = ""

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
        var rank: Int = 0

        @Attribute
        var flag: Boolean = false

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
        var state: MisleadingState = MisleadingState.ALPHA

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
        var created: Date = Date(0L)
    }

    private enum class MisleadingState(private val display: String) {
        ALPHA("published alpha"),
        BRAVO("published bravo"),
        CHARLIE("published charlie");

        override fun toString(): String = display
    }
}
