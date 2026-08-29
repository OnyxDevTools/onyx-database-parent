package com.onyx.vector

import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.VectorAttribute
import com.onyx.persistence.annotations.VectorAttributeMode
import com.onyx.persistence.annotations.VectorFeatureFamily
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VectorAttributeFeatureFamiliesTest {

    private val descriptor = EntityDescriptor(ControlledEntity::class.java)
    private val configuration = VectorManagedConfiguration.forClass(ControlledEntity::class.java)

    @Test
    fun encoderEmitsOnlyTheSelectedFamiliesForEachField() {
        val representation = VectorEntityEncoder.encode(
            ControlledEntity().apply {
                automatic = "Alpha Beta"
                intervalOnly = 73
                lexical = "Amber Comet"
                universal = true
                presenceOnly = "present"
                ignored = "invisible"
            },
            descriptor
        )

        assertStored(representation, "attribute:automatic/present")
        assertStored(representation, "attribute:automatic/category:Alpha Beta")
        assertMissing(representation, "attribute:automatic/text/exact:alpha beta")
        val automaticCoordinate = requireNotNull(VectorValueCodec.intervalCoordinate("Alpha Beta"))
        assertMissing(
            representation,
            intervalFeatureSuffix(
                definition("automatic"),
                automaticCoordinate,
                IntervalNode(automaticCoordinate.bits, automaticCoordinate.coordinate)
            )
        )

        val interval = requireNotNull(VectorValueCodec.intervalCoordinate(73))
        assertStored(
            representation,
            intervalFeatureSuffix(
                definition("intervalOnly"),
                interval,
                IntervalNode(interval.bits, interval.coordinate)
            )
        )
        assertMissing(representation, "attribute:intervalOnly/category:73")

        assertStored(representation, "attribute:lexical/text/exact:amber comet")
        assertStored(representation, "attribute:lexical/text/term:amber")
        assertStored(representation, "text/term:amber")
        assertStored(representation, "attribute:lexical/text/prefix:amb")
        assertStored(representation, "attribute:lexical/text/gram:mbe")
        assertMissing(representation, "attribute:lexical/category:Amber Comet")

        assertStored(representation, "attribute:universal/category:true")
        assertStored(representation, "attribute:universal/text/exact:true")
        assertStored(representation, "attribute:presenceOnly/present")
        assertFalse(configuration.attributes.any { it.name == "ignored" })
    }

    @Test
    fun plannerAndScannerCapabilityFallBackWhenTheRequiredFamilyIsDisabled() {
        val defaultDescriptor = EntityDescriptor(DefaultOnlyEntity::class.java)
        val planner = FingerprintQueryPlanner(defaultDescriptor)

        val equality = assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("label", QueryCriteriaOperator.EQUAL, "alpha"))
        )
        assertTrue(equality.logicalFeature.endsWith("/attribute:label/category:alpha"))

        listOf(
            QueryCriteria("rank", QueryCriteriaOperator.BETWEEN, 1 to 10),
            QueryCriteria("rank", QueryCriteriaOperator.NOT_BETWEEN, 1 to 10),
            QueryCriteria("label", QueryCriteriaOperator.CONTAINS, "ph"),
            QueryCriteria("label", QueryCriteriaOperator.NOT_CONTAINS, "ph"),
            QueryCriteria(Query.FULL_TEXT_ATTRIBUTE, QueryCriteriaOperator.MATCHES, "alpha")
        ).forEach { criteria ->
            assertEquals(FingerprintQueryPlan.Universe, planner.compile(criteria), criteria.operator.toString())
            assertFalse(
                planner.canRouteLeaf(criteria),
                "${criteria.operator} should select a full-table scanner"
            )
        }
    }

    @Test
    fun optedInLexicalAndIntervalFamiliesRemainRoutable() {
        val planner = FingerprintQueryPlanner(descriptor)

        assertTrue(
            features(planner.compile(QueryCriteria("intervalOnly", QueryCriteriaOperator.GREATER_THAN, 70)))
                .all { "/interval:i32:32/" in it.logicalFeature }
        )
        assertTrue(
            features(planner.compile(QueryCriteria("lexical", QueryCriteriaOperator.LIKE, "AMBER COMET")))
                .all { "/text/term:" in it.logicalFeature }
        )
        assertIs<FingerprintQueryPlan.Feature>(
            planner.compile(QueryCriteria("lexical", QueryCriteriaOperator.CONTAINS, "mb"))
        )
        assertIs<FingerprintQueryPlan.Search>(
            planner.compile(
                QueryCriteria(Query.FULL_TEXT_ATTRIBUTE, QueryCriteriaOperator.MATCHES, "amber")
            )
        )
    }

    @Test
    fun familyOnlyConfigurationChangePreservesCompatibleSemanticMetadata() {
        val semanticFingerprint = longArrayOf(0x1234L)
        val existing = VectorRepresentation(
            encodingVersion = 1,
            featureHashBits = 64,
            configurationId = configuration.configurationId xor Long.MIN_VALUE,
            calibrationId = 91L,
            bucketId = 2,
            boundaryConfidence = 0.75f,
            cells = intArrayOf(2),
            cellCounts = intArrayOf(8),
            semanticFingerprint = semanticFingerprint,
            semanticBands = SemanticVectorSignature.splitIntoFourBands(semanticFingerprint)
        )

        val rebuilt = VectorEntityEncoder.encode(ControlledEntity(), descriptor, existing)

        assertNotEquals(existing.configurationId, rebuilt.configurationId)
        assertEquals(configuration.configurationId, rebuilt.configurationId)
        assertEquals(existing.calibrationId, rebuilt.calibrationId)
        assertEquals(existing.bucketId, rebuilt.bucketId)
        assertEquals(existing.boundaryConfidence, rebuilt.boundaryConfidence)
        assertTrue(existing.cells.contentEquals(rebuilt.cells))
        assertTrue(existing.cellCounts.contentEquals(rebuilt.cellCounts))
        assertTrue(existing.semanticFingerprint.contentEquals(rebuilt.semanticFingerprint))
        assertTrue(existing.semanticBands.contentEquals(rebuilt.semanticBands))
    }

    private fun definition(name: String): VectorAttributeDefinition =
        configuration.attributes.single { it.name == name }

    private fun assertStored(representation: VectorRepresentation, suffix: String) {
        assertTrue(
            representation.containsFeature(fingerprint(suffix)),
            "Expected stored feature $suffix"
        )
    }

    private fun assertMissing(representation: VectorRepresentation, suffix: String) {
        assertFalse(
            representation.containsFeature(fingerprint(suffix)),
            "Unexpected stored feature $suffix"
        )
    }

    private fun fingerprint(suffix: String): FeatureFingerprint =
        VectorFeatureHasher.fingerprint("$namespace/$suffix", configuration.entropy)

    private fun intervalFeatureSuffix(
        definition: VectorAttributeDefinition,
        coordinate: IntervalCoordinate,
        node: IntervalNode
    ): String = "attribute:${definition.name}/interval:${coordinate.domain}:${coordinate.bits}/${node.canonicalToken}"

    private fun VectorRepresentation.containsFeature(feature: FeatureFingerprint): Boolean {
        if (featureWordCount != feature.wordCount) return false
        val stored = featureWords
        var offset = 0
        while (offset < stored.size) {
            if ((0 until feature.wordCount).all { stored[offset + it] == feature[it] }) return true
            offset += feature.wordCount
        }
        return false
    }

    private val namespace: String =
        "onyx-vector/1/seed:${java.lang.Long.toUnsignedString(7_640_891_576_956_012_809L, 16)}/" +
            ControlledEntity::class.java.name

    private fun features(plan: FingerprintQueryPlan): List<FingerprintQueryPlan.Feature> = when (plan) {
        FingerprintQueryPlan.Empty,
        FingerprintQueryPlan.Universe,
        is FingerprintQueryPlan.Search -> emptyList()
        is FingerprintQueryPlan.Complement -> features(plan.operand)
        is FingerprintQueryPlan.Feature -> listOf(plan)
        is FingerprintQueryPlan.AllOf -> plan.operands.flatMap(::features)
        is FingerprintQueryPlan.AnyOf -> plan.operands.flatMap(::features)
    }

    @Entity(entropy = 64)
    private class ControlledEntity : VectorManagedEntity() {
        @Identifier
        @VectorAttribute(mode = VectorAttributeMode.IGNORE)
        var id: Long = 0L

        @Attribute
        var automatic: String = ""

        @Attribute
        @VectorAttribute(
            mode = VectorAttributeMode.SELECTED,
            families = [VectorFeatureFamily.INTERVAL]
        )
        var intervalOnly: Int = 0

        @Attribute
        @VectorAttribute(
            mode = VectorAttributeMode.SELECTED,
            families = [
                VectorFeatureFamily.TEXT_EXACT,
                VectorFeatureFamily.TEXT_TERM,
                VectorFeatureFamily.TEXT_PREFIX,
                VectorFeatureFamily.TEXT_NGRAM
            ]
        )
        var lexical: String = ""

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.UNIVERSAL)
        var universal: Boolean = false

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.SELECTED)
        var presenceOnly: String? = null

        @Attribute
        @VectorAttribute(mode = VectorAttributeMode.IGNORE)
        var ignored: String = ""
    }

    @Entity(entropy = 64)
    private class DefaultOnlyEntity : VectorManagedEntity() {
        @Identifier
        var id: Long = 0L

        @Attribute
        var rank: Int = 0

        @Attribute
        var label: String = ""
    }
}
