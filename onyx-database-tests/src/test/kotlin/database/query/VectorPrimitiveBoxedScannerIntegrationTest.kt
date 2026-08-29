package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.from
import com.onyx.vector.FingerprintQueryPlan
import com.onyx.vector.FingerprintQueryPlanner
import entities.VectorPrimitiveBoxedEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Public-query coverage for JVM scalar representations and values that are easy to mishandle.
 *
 * Every non-empty assertion proves that candidate discovery reached VectorIndexScanner, read a
 * concrete fingerprint feature, returned the independently declared result set, and never
 * constructed a FullTableScanner. The suite runs against every storage implementation.
 */
@RunWith(Parameterized::class)
class VectorPrimitiveBoxedScannerIntegrationTest(
    private val storeType: StoreType
) {

    private lateinit var databaseDirectory: Path
    private lateinit var trackingContext: VectorScannerTrackingSchemaContext
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager
    private lateinit var records: Map<String, VectorPrimitiveBoxedEntity>

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-vector-primitive-boxed-")
        val location = databaseDirectory.toString()
        trackingContext = VectorScannerTrackingSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = trackingContext,
            addShutdownHook = false
        ).apply {
            this.storeType = this@VectorPrimitiveBoxedScannerIntegrationTest.storeType
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager
        records = saveFixture()
    }

    @After
    fun cleanup() {
        try {
            if (::factory.isInitialized) factory.close()
        } finally {
            if (::databaseDirectory.isInitialized) databaseDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun primitiveAndBoxedScalarEqualityUseSelectiveFeatureRoutes() {
        equalityCases().forEach { case ->
            assertSelectiveQuery(
                case.description,
                QueryCriteria(case.attribute, QueryCriteriaOperator.EQUAL, case.value),
                setOf(MATCH)
            )
        }
    }

    @Test
    fun boxedEqualNullAndNotEqualNullUseNullAndPresenceFeatures() {
        boxedAttributes().forEach { attribute ->
            assertSelectiveQuery(
                "$attribute EQUAL null",
                QueryCriteria(attribute, QueryCriteriaOperator.EQUAL, null),
                setOf(NULLS)
            )
            assertSelectiveQuery(
                "$attribute NOT_EQUAL null",
                QueryCriteria(attribute, QueryCriteriaOperator.NOT_EQUAL, null),
                NON_NULL_RECORDS
            )
        }
    }

    @Test
    fun emptyInUsesIndexedEmptyPlanForEveryPrimitiveAndBoxedScalar() {
        allScalarAttributes().forEach { attribute ->
            val criteria = QueryCriteria(
                attribute,
                QueryCriteriaOperator.IN,
                emptyList<Any?>()
            )
            val plan = planner().compile(criteria)
            assertSame(
                FingerprintQueryPlan.Empty,
                plan,
                "$attribute IN [] must compile to the indexed empty plan"
            )

            trackingContext.resetScannerUsage()
            val actual = queryLabels(criteria)

            assertEquals(emptySet(), actual, "$attribute IN []")
            assertTrue(
                trackingContext.vectorScannerIndexLookups > 0,
                "$attribute IN [] did not construct VectorIndexScanner"
            )
            assertEquals(
                0,
                trackingContext.vectorScannerFeatureReads,
                "$attribute IN [] should not read a feature for a statically empty plan"
            )
            assertEquals(0, trackingContext.vectorScannerDomainReads, "$attribute IN [] enumerated the indexed domain")
            assertEquals(0, trackingContext.fullTableReads, "$attribute IN [] executed FullTableScanner")
        }
    }

    @Test
    fun floatingPointSpecialValuesUseCategoricalRoutesAndExactVerification() {
        floatingSpecialEqualityCases().forEach { case ->
            assertSelectiveQuery(
                case.description,
                QueryCriteria(case.attribute, QueryCriteriaOperator.EQUAL, case.value),
                case.expected
            )
        }

        floatingAttributes().forEach { (attribute, zero) ->
            val greaterExpected = if (attribute.startsWith("primitive")) {
                setOf(MATCH, OTHER, NAN, POSITIVE_INFINITY)
            } else {
                setOf(MATCH, NAN, POSITIVE_INFINITY)
            }
            val lessExpected = if (attribute.startsWith("primitive")) {
                setOf(NULLS, NEGATIVE_INFINITY)
            } else {
                // Onyx's established ordering treats null as below every non-null value.
                setOf(NULLS, OTHER, NEGATIVE_INFINITY)
            }
            assertSelectiveQuery(
                "$attribute GREATER_THAN zero includes Java-comparable IEEE values",
                QueryCriteria(attribute, QueryCriteriaOperator.GREATER_THAN, zero),
                greaterExpected
            )
            assertSelectiveQuery(
                "$attribute LESS_THAN zero includes negative infinity",
                QueryCriteria(attribute, QueryCriteriaOperator.LESS_THAN, zero),
                lessExpected
            )
        }
    }

    @Test
    fun floatingPointNotEqualNaNUsesVerifiedIndexedComplement() {
        listOf(
            "primitiveFloat" to Float.NaN,
            "boxedFloat" to Float.NaN,
            "primitiveDouble" to Double.NaN,
            "boxedDouble" to Double.NaN
        ).forEach { (attribute, nan) ->
            assertSelectiveQuery(
                "$attribute NOT_EQUAL NaN",
                QueryCriteria(attribute, QueryCriteriaOperator.NOT_EQUAL, nan),
                ALL_RECORDS - NAN,
                expectedDomainReads = 1
            )
        }
    }

    private fun assertSelectiveQuery(
        description: String,
        criteria: QueryCriteria,
        expected: Set<String>,
        expectedDomainReads: Int = 0
    ) {
        val plan = planner().compile(criteria)
        assertTrue(plan !== FingerprintQueryPlan.Universe, "$description has no fingerprint route")
        assertTrue(plan !== FingerprintQueryPlan.Empty, "$description unexpectedly compiled to an empty plan")

        trackingContext.resetScannerUsage()
        val actual = queryLabels(criteria)

        assertEquals(expected, actual, description)
        assertTrue(
            trackingContext.vectorScannerIndexLookups > 0,
            "$description did not construct VectorIndexScanner"
        )
        assertTrue(
            trackingContext.vectorScannerFeatureReads > 0,
            "$description did not execute a fingerprint feature lookup"
        )
        assertTrue(
            trackingContext.vectorFingerprintBranchReads > 0,
            "$description did not execute VectorIndexScanner.scanFingerprint"
        )
        assertEquals(
            expectedDomainReads,
            trackingContext.vectorScannerDomainReads,
            "$description used an unexpected indexed-domain enumeration"
        )
        assertEquals(0, trackingContext.fullTableReads, "$description executed FullTableScanner")
    }

    private fun queryLabels(criteria: QueryCriteria): Set<String> {
        val labelsById = records.entries.associate { (label, entity) -> entity.id to label }
        return manager.from<VectorPrimitiveBoxedEntity>()
            .where(criteria)
            .list<VectorPrimitiveBoxedEntity>()
            .mapTo(LinkedHashSet()) { labelsById.getValue(it.id) }
    }

    private fun planner(): FingerprintQueryPlanner {
        val descriptor = trackingContext.getDescriptorForEntity(VectorPrimitiveBoxedEntity::class.java, "")
        return FingerprintQueryPlanner(descriptor)
    }

    private fun saveFixture(): Map<String, VectorPrimitiveBoxedEntity> = linkedMapOf(
        NULLS to finiteEntity(-7, null, 'A', null, false, null),
        MATCH to finiteEntity(7, 7, 'M', 'M', true, true),
        OTHER to finiteEntity(21, -7, 'Z', 'Z', false, false),
        NAN to finiteEntity(91, 91, 'N', 'N', false, false).apply {
            primitiveFloat = Float.NaN
            boxedFloat = Float.NaN
            primitiveDouble = Double.NaN
            boxedDouble = Double.NaN
        },
        NEGATIVE_INFINITY to finiteEntity(92, 92, 'I', 'I', false, false).apply {
            primitiveFloat = Float.NEGATIVE_INFINITY
            boxedFloat = Float.NEGATIVE_INFINITY
            primitiveDouble = Double.NEGATIVE_INFINITY
            boxedDouble = Double.NEGATIVE_INFINITY
        },
        POSITIVE_INFINITY to finiteEntity(93, 93, 'P', 'P', false, false).apply {
            primitiveFloat = Float.POSITIVE_INFINITY
            boxedFloat = Float.POSITIVE_INFINITY
            primitiveDouble = Double.POSITIVE_INFINITY
            boxedDouble = Double.POSITIVE_INFINITY
        }
    ).mapValues { (_, entity) ->
        manager.saveEntity<IManagedEntity>(entity) as VectorPrimitiveBoxedEntity
    }

    private fun finiteEntity(
        primitiveNumber: Int,
        boxedNumber: Int?,
        primitiveChar: Char,
        boxedChar: Char?,
        primitiveBoolean: Boolean,
        boxedBoolean: Boolean?
    ): VectorPrimitiveBoxedEntity = VectorPrimitiveBoxedEntity().also {
        it.primitiveByte = primitiveNumber.toByte()
        it.boxedByte = boxedNumber?.toByte()
        it.primitiveShort = primitiveNumber.toShort()
        it.boxedShort = boxedNumber?.toShort()
        it.primitiveInt = primitiveNumber
        it.boxedInt = boxedNumber
        it.primitiveLong = primitiveNumber.toLong()
        it.boxedLong = boxedNumber?.toLong()
        it.primitiveFloat = primitiveNumber.toFloat()
        it.boxedFloat = boxedNumber?.toFloat()
        it.primitiveDouble = primitiveNumber.toDouble()
        it.boxedDouble = boxedNumber?.toDouble()
        it.primitiveChar = primitiveChar
        it.boxedChar = boxedChar
        it.primitiveBoolean = primitiveBoolean
        it.boxedBoolean = boxedBoolean
    }

    private data class EqualityCase(
        val description: String,
        val attribute: String,
        val value: Any
    )

    private data class FloatingSpecialCase(
        val description: String,
        val attribute: String,
        val value: Any,
        val expected: Set<String>
    )

    companion object {
        private const val NULLS = "nulls"
        private const val MATCH = "match"
        private const val OTHER = "other"
        private const val NAN = "nan"
        private const val NEGATIVE_INFINITY = "negative-infinity"
        private const val POSITIVE_INFINITY = "positive-infinity"

        private val ALL_RECORDS = setOf(NULLS, MATCH, OTHER, NAN, NEGATIVE_INFINITY, POSITIVE_INFINITY)
        private val NON_NULL_RECORDS = ALL_RECORDS - NULLS

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stores(): Collection<Array<Any>> = StoreType.entries.map { arrayOf(it) }

        private fun equalityCases(): List<EqualityCase> = listOf(
            EqualityCase("primitive Byte", "primitiveByte", 7.toByte()),
            EqualityCase("boxed Byte", "boxedByte", 7.toByte()),
            EqualityCase("primitive Short", "primitiveShort", 7.toShort()),
            EqualityCase("boxed Short", "boxedShort", 7.toShort()),
            EqualityCase("primitive Int", "primitiveInt", 7),
            EqualityCase("boxed Int", "boxedInt", 7),
            EqualityCase("primitive Long", "primitiveLong", 7L),
            EqualityCase("boxed Long", "boxedLong", 7L),
            EqualityCase("primitive Float", "primitiveFloat", 7.0f),
            EqualityCase("boxed Float", "boxedFloat", 7.0f),
            EqualityCase("primitive Double", "primitiveDouble", 7.0),
            EqualityCase("boxed Double", "boxedDouble", 7.0),
            EqualityCase("primitive Boolean", "primitiveBoolean", true),
            EqualityCase("boxed Boolean", "boxedBoolean", true),
            EqualityCase("primitive Char", "primitiveChar", 'M'),
            EqualityCase("boxed Char", "boxedChar", 'M')
        )

        private fun boxedAttributes(): List<String> = listOf(
            "boxedByte",
            "boxedShort",
            "boxedInt",
            "boxedLong",
            "boxedFloat",
            "boxedDouble",
            "boxedBoolean",
            "boxedChar"
        )

        private fun allScalarAttributes(): List<String> = listOf(
            "primitiveByte",
            "boxedByte",
            "primitiveShort",
            "boxedShort",
            "primitiveInt",
            "boxedInt",
            "primitiveLong",
            "boxedLong",
            "primitiveFloat",
            "boxedFloat",
            "primitiveDouble",
            "boxedDouble",
            "primitiveBoolean",
            "boxedBoolean",
            "primitiveChar",
            "boxedChar"
        )

        private fun floatingAttributes(): List<Pair<String, Any>> = listOf(
            "primitiveFloat" to 0.0f,
            "boxedFloat" to 0.0f,
            "primitiveDouble" to 0.0,
            "boxedDouble" to 0.0
        )

        private fun floatingSpecialEqualityCases(): List<FloatingSpecialCase> = listOf(
            FloatingSpecialCase("primitive Float NaN", "primitiveFloat", Float.NaN, setOf(NAN)),
            FloatingSpecialCase("boxed Float NaN", "boxedFloat", Float.NaN, setOf(NAN)),
            FloatingSpecialCase(
                "primitive Float negative infinity",
                "primitiveFloat",
                Float.NEGATIVE_INFINITY,
                setOf(NEGATIVE_INFINITY)
            ),
            FloatingSpecialCase(
                "boxed Float negative infinity",
                "boxedFloat",
                Float.NEGATIVE_INFINITY,
                setOf(NEGATIVE_INFINITY)
            ),
            FloatingSpecialCase(
                "primitive Float positive infinity",
                "primitiveFloat",
                Float.POSITIVE_INFINITY,
                setOf(POSITIVE_INFINITY)
            ),
            FloatingSpecialCase(
                "boxed Float positive infinity",
                "boxedFloat",
                Float.POSITIVE_INFINITY,
                setOf(POSITIVE_INFINITY)
            ),
            FloatingSpecialCase("primitive Double NaN", "primitiveDouble", Double.NaN, setOf(NAN)),
            FloatingSpecialCase("boxed Double NaN", "boxedDouble", Double.NaN, setOf(NAN)),
            FloatingSpecialCase(
                "primitive Double negative infinity",
                "primitiveDouble",
                Double.NEGATIVE_INFINITY,
                setOf(NEGATIVE_INFINITY)
            ),
            FloatingSpecialCase(
                "boxed Double negative infinity",
                "boxedDouble",
                Double.NEGATIVE_INFINITY,
                setOf(NEGATIVE_INFINITY)
            ),
            FloatingSpecialCase(
                "primitive Double positive infinity",
                "primitiveDouble",
                Double.POSITIVE_INFINITY,
                setOf(POSITIVE_INFINITY)
            ),
            FloatingSpecialCase(
                "boxed Double positive infinity",
                "boxedDouble",
                Double.POSITIVE_INFINITY,
                setOf(POSITIVE_INFINITY)
            )
        )
    }
}
