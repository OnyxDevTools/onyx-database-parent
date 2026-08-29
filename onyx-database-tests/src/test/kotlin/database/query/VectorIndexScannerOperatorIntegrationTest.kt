package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.from
import com.onyx.vector.FingerprintQueryExecutor
import com.onyx.vector.FingerprintQueryPlan
import com.onyx.vector.FingerprintQueryPlanner
import entities.VectorPredicateEntity
import entities.VectorPredicateEnum
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end proof that every applicable scalar predicate executes through VectorIndexScanner.
 *
 * The parameter matrix crosses all eleven persisted scalar families with all 22 public operators
 * and every store implementation: 242 type/operator pairs, 726 JUnit parameters, and 3,630 public
 * queries. Each parameter is queried alone and on both sides of AND and OR. Expected record labels
 * are declared independently of the database query, while
 * [VectorScannerTrackingSchemaContext] makes the test fail if public query execution bypasses
 * the scanner or never reads the fingerprint index.
 */
@RunWith(Parameterized::class)
class VectorIndexScannerOperatorIntegrationTest(
    private val operatorCase: OperatorCase,
    private val storeType: StoreType
) {

    private lateinit var databaseDirectory: Path
    private lateinit var trackingContext: VectorScannerTrackingSchemaContext
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager
    private lateinit var records: Map<String, VectorPredicateEntity>

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-vector-scanner-operator-")
        val location = databaseDirectory.toString()
        trackingContext = VectorScannerTrackingSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = trackingContext,
            addShutdownHook = false
        ).apply {
            this.storeType = this@VectorIndexScannerOperatorIntegrationTest.storeType
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
    fun operatorUsesVectorIndexScannerForStandaloneAndBothCompoundOrders() {
        assertQuery(
            "standalone",
            operatorCase.expected,
            operatorCase.criteria(),
            expectedScannerCount = 1,
            expectedDomainReads = operatorCase.expectedUnrestrictedDomainReads
        )
        assertQuery(
            "operator AND anchor",
            operatorCase.expected intersect ANCHOR_EXPECTED,
            operatorCase.criteria().and(anchorCriteria()),
            expectedScannerCount = 2,
            expectedDomainReads = 0
        )
        assertQuery(
            "anchor AND operator",
            ANCHOR_EXPECTED intersect operatorCase.expected,
            anchorCriteria().and(operatorCase.criteria()),
            expectedScannerCount = 2,
            expectedDomainReads = 0
        )
        assertQuery(
            "operator OR anchor",
            operatorCase.expected union ANCHOR_EXPECTED,
            operatorCase.criteria().or(anchorCriteria()),
            expectedScannerCount = 2,
            expectedDomainReads = operatorCase.expectedUnrestrictedDomainReads
        )
        assertQuery(
            "anchor OR operator",
            ANCHOR_EXPECTED union operatorCase.expected,
            anchorCriteria().or(operatorCase.criteria()),
            expectedScannerCount = 2,
            expectedDomainReads = operatorCase.expectedUnrestrictedDomainReads
        )
    }

    private fun assertQuery(
        shape: String,
        expected: Set<String>,
        criteria: QueryCriteria,
        expectedScannerCount: Int,
        expectedDomainReads: Int
    ) {
        trackingContext.resetScannerUsage()

        val descriptor = trackingContext.getDescriptorForEntity(VectorPredicateEntity::class.java, "")
        val leafPlan = FingerprintQueryPlanner(descriptor).compile(operatorCase.criteria())
        assertTrue(
            leafPlan !== FingerprintQueryPlan.Universe,
            "${operatorCase.scalarType}.${operatorCase.operator} widened to the indexed domain"
        )
        assertEquals(
            operatorCase.usesVerifiedComplement,
            leafPlan is FingerprintQueryPlan.Complement,
            "${operatorCase.scalarType}.${operatorCase.operator} complement routing"
        )
        val routedPlan = (leafPlan as? FingerprintQueryPlan.Complement)?.operand ?: leafPlan
        val fixtureIds = records.values.mapTo(LinkedHashSet(), VectorPredicateEntity::id)
        val candidateIds = FingerprintQueryExecutor.forPartition(descriptor, trackingContext)
            .candidateIds(routedPlan)
        assertTrue(
            candidateIds.size < fixtureIds.size,
            "${operatorCase.scalarType}.${operatorCase.operator} routed all ${fixtureIds.size} fixture IDs"
        )

        // Candidate-plan inspection above also executes the interactor directly. Only count the
        // physical path taken by the public query below.
        trackingContext.resetScannerUsage()
        val labelsById = records.entries.associate { (label, entity) -> entity.id to label }
        val actual = manager.from<VectorPredicateEntity>()
            .where(criteria)
            .list<VectorPredicateEntity>()
            .mapTo(LinkedHashSet()) { labelsById.getValue(it.id) }

        val message = "${operatorCase.scalarType}.${operatorCase.operator}: $shape"
        assertEquals(expected, actual, message)
        assertEquals(
            expectedScannerCount,
            trackingContext.vectorScannerIndexLookups,
            "$message constructed an unexpected number of VectorIndexScanners"
        )
        assertEquals(
            expectedScannerCount,
            trackingContext.vectorScannerScans,
            "$message executed an unexpected number of VectorIndexScanner scans"
        )
        assertTrue(
            trackingContext.vectorScannerFeatureReads > 0,
            "$message did not execute a selective fingerprint feature lookup"
        )
        assertTrue(
            trackingContext.vectorFingerprintBranchReads > 0,
            "$message did not execute VectorIndexScanner.scanFingerprint"
        )
        assertEquals(
            expectedDomainReads,
            trackingContext.vectorScannerDomainReads,
            "$message used an unexpected indexed-domain enumeration"
        )
        assertEquals(0, trackingContext.fullTableReads, "$message executed FullTableScanner")
    }

    private fun anchorCriteria(): QueryCriteria =
        QueryCriteria("text", QueryCriteriaOperator.LIKE, "anchor route")

    private fun saveFixture(): Map<String, VectorPredicateEntity> = linkedMapOf(
        A to entity(-5, 'A', false, VectorPredicateEnum.ALPHA, TEXT_A),
        B to entity(0, 'B', true, VectorPredicateEnum.BRAVO, TEXT_B),
        C to entity(7, 'C', false, VectorPredicateEnum.CHARLIE, TEXT_C),
        D to entity(10, 'D', true, VectorPredicateEnum.DELTA, TEXT_D),
        E to entity(20, 'E', false, VectorPredicateEnum.ECHO, TEXT_E),
        F to entity(30, 'F', true, VectorPredicateEnum.FOXTROT, TEXT_F).apply {
            floatValue = Float.POSITIVE_INFINITY
            exactDouble = Double.POSITIVE_INFINITY
        },
        N to entity(null, null, null, null, null)
    ).mapValues { (_, entity) ->
        manager.saveEntity<IManagedEntity>(entity) as VectorPredicateEntity
    }

    private fun entity(
        orderedValue: Int?,
        charValue: Char?,
        booleanValue: Boolean?,
        enumValue: VectorPredicateEnum?,
        text: String?
    ): VectorPredicateEntity = VectorPredicateEntity().also {
        it.byteValue = orderedValue?.toByte()
        it.shortValue = orderedValue?.toShort()
        it.intValue = orderedValue
        it.longValue = orderedValue?.toLong()
        it.floatValue = orderedValue?.toFloat()
        it.exactDouble = orderedValue?.toDouble()
        it.occurredAt = orderedValue?.let(::date)
        it.charValue = charValue
        it.booleanValue = booleanValue
        it.enumValue = enumValue
        it.text = text
    }

    data class OperatorCase(
        val scalarType: String,
        val operator: QueryCriteriaOperator,
        val expected: Set<String>,
        val criteria: () -> QueryCriteria
    ) {
        val usesVerifiedComplement: Boolean
            get() = operator in COMPLEMENT_OPERATORS

        val expectedUnrestrictedDomainReads: Int
            get() = if (usesVerifiedComplement) 1 else 0

        override fun toString(): String = "$scalarType-${operator.name}"
    }

    companion object {
        private const val A = "A"
        private const val B = "B"
        private const val C = "C"
        private const val D = "D"
        private const val E = "E"
        private const val F = "F"
        private const val N = "N"

        private const val TEXT_A = "anchor route Crimson Launch"
        private const val TEXT_B = "anchor route Rare Amber Comet"
        private const val TEXT_C = "rare amber comet"
        private const val TEXT_D = "anchor route amber archive"
        private const val TEXT_E = "rare anchor route GALAXY archive"
        private const val TEXT_F = "amber comet"

        private const val DAY = 86_400_000L
        private const val DATE_BASE = 1_700_000_000_000L
        private val DATE_TEXT_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC)

        private val ANCHOR_EXPECTED = setOf(A, B, D, E)
        private val ALL_EXPECTED = setOf(A, B, C, D, E, F, N)

        private val COMPLEMENT_OPERATORS = setOf(
            QueryCriteriaOperator.NOT_EQUAL,
            QueryCriteriaOperator.NOT_STARTS_WITH,
            QueryCriteriaOperator.NOT_CONTAINS,
            QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE,
            QueryCriteriaOperator.NOT_LIKE,
            QueryCriteriaOperator.NOT_MATCHES,
            QueryCriteriaOperator.NOT_BETWEEN,
            QueryCriteriaOperator.NOT_IN
        )

        // These are bounded admission plans, not exact scalar predicates. CANDIDATES is handled
        // by ApproximateIndexCandidateScanner; SEARCH_CANDIDATES and HNSW_CANDIDATES target the
        // whole-record text pseudo-field. Their dedicated suites assert budgets/rejection rules;
        // adding them to the scalar matrix would test invalid queries.
        private val ADMISSION_ONLY_OPERATORS = setOf(
            QueryCriteriaOperator.CANDIDATES,
            QueryCriteriaOperator.SEARCH_CANDIDATES,
            QueryCriteriaOperator.HNSW_CANDIDATES,
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun operatorAndStoreCases(): Collection<Array<Any>> = buildList {
            StoreType.entries.forEach { storeType ->
                operatorCases().forEach { operatorCase ->
                    add(arrayOf(operatorCase, storeType))
                }
            }
        }

        private fun operatorCases(): List<OperatorCase> = buildList {
            addAll(
                orderedScalarCases(
                    "Byte",
                    "byteValue",
                    (-5).toByte(),
                    0.toByte(),
                    7.toByte(),
                    10.toByte(),
                    20.toByte()
                )
            )
            addAll(
                orderedScalarCases(
                    "Short",
                    "shortValue",
                    (-5).toShort(),
                    0.toShort(),
                    7.toShort(),
                    10.toShort(),
                    20.toShort()
                )
            )
            addAll(orderedScalarCases("Int", "intValue", -5, 0, 7, 10, 20))
            addAll(orderedScalarCases("Long", "longValue", -5L, 0L, 7L, 10L, 20L))
            addAll(
                orderedScalarCases(
                    "Float",
                    "floatValue",
                    -5.0f,
                    0.0f,
                    7.0f,
                    10.0f,
                    20.0f,
                    textStartsWithValue = "Infin",
                    textContainsValue = "finit",
                    textContainsIgnoreCaseValue = "INFINITY",
                    textLikeValue = "infinity",
                    textExpected = setOf(F),
                    regexPattern = "Infinity",
                    regexMatchesExpected = setOf(F),
                    regexNotMatchesExpected = setOf(A, B, C, D, E, N)
                )
            )
            addAll(
                orderedScalarCases(
                    "Double",
                    "exactDouble",
                    -5.0,
                    0.0,
                    7.0,
                    10.0,
                    20.0,
                    textStartsWithValue = "Infin",
                    textContainsValue = "finit",
                    textContainsIgnoreCaseValue = "INFINITY",
                    textLikeValue = "infinity",
                    textExpected = setOf(F),
                    regexPattern = "Infinity",
                    regexMatchesExpected = setOf(F),
                    regexNotMatchesExpected = setOf(A, B, C, D, E, N)
                )
            )
            addAll(
                orderedScalarCases(
                    "Date",
                    "occurredAt",
                    date(-5),
                    date(0),
                    date(7),
                    date(10),
                    date(20),
                    textStartsWithValue = "2023-11-21",
                    textContainsValue = "11-21T22:13",
                    textContainsIgnoreCaseValue = "11-21t22:13",
                    textLikeValue = dateText(7).lowercase(Locale.ROOT),
                    regexPattern = ".*11-21T22:13:20.*"
                )
            )
            addAll(orderedScalarCases("Char", "charValue", 'A', 'B', 'C', 'D', 'E'))

            addAll(
                commonScalarCases(
                    scalarType = "Boolean",
                    attribute = "booleanValue",
                    equalValue = true,
                    equalExpected = setOf(B, D, F),
                    notEqualExpected = setOf(A, C, E, N),
                    inValues = listOf(false),
                    inExpected = setOf(A, C, E),
                    notInExpected = setOf(B, D, F, N)
                )
            )
            addAll(
                regexScalarCases(
                    scalarType = "Boolean",
                    attribute = "booleanValue",
                    pattern = "true",
                    matchesExpected = setOf(B, D, F),
                    notMatchesExpected = setOf(A, C, E, N)
                )
            )
            addAll(
                scalarTextCases(
                    scalarType = "Boolean",
                    attribute = "booleanValue",
                    startsWithValue = "tr",
                    containsValue = "ru",
                    containsIgnoreCaseValue = "TRUE",
                    likeValue = "TRUE",
                    expected = setOf(B, D, F)
                )
            )
            addAll(
                orderRangeCases(
                    scalarType = "Boolean",
                    attribute = "booleanValue",
                    pivot = false,
                    lowerBound = false,
                    upperBound = false,
                    lessThanExpected = setOf(N),
                    lessThanEqualExpected = setOf(A, C, E, N),
                    greaterThanExpected = setOf(B, D, F),
                    greaterThanEqualExpected = setOf(A, B, C, D, E, F),
                    betweenExpected = setOf(A, C, E),
                    notBetweenExpected = setOf(B, D, F, N)
                )
            )
            addAll(
                commonScalarCases(
                    scalarType = "String",
                    attribute = "text",
                    equalValue = TEXT_C,
                    inValues = listOf(TEXT_A, TEXT_C, TEXT_E)
                )
            )
            addAll(textScalarCases())
            addAll(
                orderRangeCases(
                    scalarType = "String",
                    attribute = "text",
                    pivot = TEXT_D,
                    lowerBound = TEXT_B,
                    upperBound = TEXT_C,
                    lessThanExpected = setOf(A, B, F, N),
                    lessThanEqualExpected = setOf(A, B, D, F, N),
                    greaterThanExpected = setOf(C, E),
                    greaterThanEqualExpected = setOf(C, D, E),
                    betweenExpected = setOf(B, C, D),
                    notBetweenExpected = setOf(A, E, F, N)
                )
            )
            addAll(
                commonScalarCases(
                    scalarType = "Enum",
                    attribute = "enumValue",
                    equalValue = VectorPredicateEnum.CHARLIE,
                    inValues = listOf(
                        VectorPredicateEnum.ALPHA,
                        VectorPredicateEnum.CHARLIE,
                        VectorPredicateEnum.ECHO
                    )
                )
            )
            addAll(
                scalarTextCases(
                    scalarType = "Enum",
                    attribute = "enumValue",
                    startsWithValue = "CHAR",
                    containsValue = "ARL",
                    containsIgnoreCaseValue = "charlie",
                    likeValue = "charlie",
                    expected = setOf(C)
                )
            )
            addAll(
                regexScalarCases(
                    scalarType = "Enum",
                    attribute = "enumValue",
                    pattern = "CHARLIE",
                    matchesExpected = setOf(C),
                    notMatchesExpected = setOf(A, B, D, E, F, N)
                )
            )
            addAll(
                orderRangeCases(
                    scalarType = "Enum",
                    attribute = "enumValue",
                    pivot = VectorPredicateEnum.CHARLIE,
                    lowerBound = VectorPredicateEnum.BRAVO,
                    upperBound = VectorPredicateEnum.DELTA,
                    lessThanExpected = setOf(A, B, N),
                    lessThanEqualExpected = setOf(A, B, C, N),
                    greaterThanExpected = setOf(D, E, F),
                    greaterThanEqualExpected = setOf(C, D, E, F),
                    betweenExpected = setOf(B, C, D),
                    notBetweenExpected = setOf(A, E, F, N)
                )
            )
        }.also { cases ->
            val expectedCasesByType = mapOf(
                "Byte" to 22,
                "Short" to 22,
                "Int" to 22,
                "Long" to 22,
                "Float" to 22,
                "Double" to 22,
                "Date" to 22,
                "Char" to 22,
                "Boolean" to 22,
                "String" to 22,
                "Enum" to 22
            )
            check(cases.size == 242 && cases.groupingBy(OperatorCase::scalarType).eachCount() == expectedCasesByType) {
                "The VectorIndexScanner matrix must cover all 242 public scalar type/operator pairs"
            }
            check(cases.map { it.scalarType to it.operator }.toSet().size == cases.size) {
                "The VectorIndexScanner matrix contains a duplicate scalar type/operator pair"
            }
            check(
                cases.mapTo(linkedSetOf(), OperatorCase::operator) ==
                    QueryCriteriaOperator.entries.toSet() - ADMISSION_ONLY_OPERATORS
            ) {
                "The VectorIndexScanner matrix must cover every exact scalar QueryCriteriaOperator"
            }
        }

        private fun orderedScalarCases(
            scalarType: String,
            attribute: String,
            lowValue: Any,
            lowerBound: Any,
            pivot: Any,
            upperBound: Any,
            highValue: Any,
            textStartsWithValue: String = pivot.toString(),
            textContainsValue: String = pivot.toString(),
            textContainsIgnoreCaseValue: String = pivot.toString(),
            textLikeValue: String = pivot.toString(),
            textExpected: Set<String> = setOf(C),
            regexPattern: String = pivot.toString(),
            regexMatchesExpected: Set<String> = setOf(C),
            regexNotMatchesExpected: Set<String> = setOf(A, B, D, E, F, N)
        ): List<OperatorCase> =
            commonScalarCases(
                scalarType = scalarType,
                attribute = attribute,
                equalValue = pivot,
                inValues = listOf(lowValue, pivot, highValue)
            ) + listOf(
                case(scalarType, QueryCriteriaOperator.LESS_THAN, setOf(A, B, N), attribute, pivot),
                case(scalarType, QueryCriteriaOperator.LESS_THAN_EQUAL, setOf(A, B, C, N), attribute, pivot),
                case(scalarType, QueryCriteriaOperator.GREATER_THAN, setOf(D, E, F), attribute, pivot),
                case(scalarType, QueryCriteriaOperator.GREATER_THAN_EQUAL, setOf(C, D, E, F), attribute, pivot),
                case(
                    scalarType,
                    QueryCriteriaOperator.BETWEEN,
                    setOf(B, C, D),
                    attribute,
                    lowerBound to upperBound
                ),
                case(
                    scalarType,
                    QueryCriteriaOperator.NOT_BETWEEN,
                    setOf(A, E, F, N),
                    attribute,
                    lowerBound to upperBound
                )
            ) + scalarTextCases(
                scalarType = scalarType,
                attribute = attribute,
                startsWithValue = textStartsWithValue,
                containsValue = textContainsValue,
                containsIgnoreCaseValue = textContainsIgnoreCaseValue,
                likeValue = textLikeValue,
                expected = textExpected
            ) + regexScalarCases(
                scalarType = scalarType,
                attribute = attribute,
                pattern = regexPattern,
                matchesExpected = regexMatchesExpected,
                notMatchesExpected = regexNotMatchesExpected
            )

        private fun commonScalarCases(
            scalarType: String,
            attribute: String,
            equalValue: Any,
            equalExpected: Set<String> = setOf(C),
            notEqualExpected: Set<String> = setOf(A, B, D, E, F, N),
            inValues: List<Any>,
            inExpected: Set<String> = setOf(A, C, E),
            notInExpected: Set<String> = setOf(B, D, F, N)
        ): List<OperatorCase> = listOf(
            case(scalarType, QueryCriteriaOperator.EQUAL, equalExpected, attribute, equalValue),
            case(scalarType, QueryCriteriaOperator.NOT_EQUAL, notEqualExpected, attribute, equalValue),
            case(scalarType, QueryCriteriaOperator.IS_NULL, setOf(N), attribute),
            case(scalarType, QueryCriteriaOperator.NOT_NULL, setOf(A, B, C, D, E, F), attribute),
            case(scalarType, QueryCriteriaOperator.IN, inExpected, attribute, inValues),
            case(scalarType, QueryCriteriaOperator.NOT_IN, notInExpected, attribute, inValues)
        )

        private fun textScalarCases(): List<OperatorCase> = listOf(
            case("String", QueryCriteriaOperator.STARTS_WITH, setOf(C, E), "text", "rare"),
            case("String", QueryCriteriaOperator.NOT_STARTS_WITH, setOf(A, B, D, F, N), "text", "rare"),
            case("String", QueryCriteriaOperator.CONTAINS, setOf(C, D, F), "text", "amber"),
            case("String", QueryCriteriaOperator.NOT_CONTAINS, setOf(A, B, E, N), "text", "amber"),
            case("String", QueryCriteriaOperator.CONTAINS_IGNORE_CASE, setOf(B, C, D, F), "text", "AMBER"),
            case("String", QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE, setOf(A, E, N), "text", "AMBER"),
            case("String", QueryCriteriaOperator.LIKE, setOf(B, C, F), "text", "AMBER COMET"),
            case("String", QueryCriteriaOperator.NOT_LIKE, setOf(A, D, E, N), "text", "AMBER COMET"),
            case("String", QueryCriteriaOperator.MATCHES, setOf(C, D, F), "text", ".*amber.*"),
            case("String", QueryCriteriaOperator.NOT_MATCHES, setOf(A, B, E, N), "text", ".*amber.*")
        )

        private fun scalarTextCases(
            scalarType: String,
            attribute: String,
            startsWithValue: String,
            containsValue: String,
            containsIgnoreCaseValue: String,
            likeValue: String,
            expected: Set<String>
        ): List<OperatorCase> {
            val notExpected = ALL_EXPECTED - expected
            return listOf(
                case(scalarType, QueryCriteriaOperator.STARTS_WITH, expected, attribute, startsWithValue),
                case(scalarType, QueryCriteriaOperator.NOT_STARTS_WITH, notExpected, attribute, startsWithValue),
                case(scalarType, QueryCriteriaOperator.CONTAINS, expected, attribute, containsValue),
                case(scalarType, QueryCriteriaOperator.NOT_CONTAINS, notExpected, attribute, containsValue),
                case(
                    scalarType,
                    QueryCriteriaOperator.CONTAINS_IGNORE_CASE,
                    expected,
                    attribute,
                    containsIgnoreCaseValue
                ),
                case(
                    scalarType,
                    QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE,
                    notExpected,
                    attribute,
                    containsIgnoreCaseValue
                ),
                case(scalarType, QueryCriteriaOperator.LIKE, expected, attribute, likeValue),
                case(scalarType, QueryCriteriaOperator.NOT_LIKE, notExpected, attribute, likeValue)
            )
        }

        private fun orderRangeCases(
            scalarType: String,
            attribute: String,
            pivot: Any,
            lowerBound: Any,
            upperBound: Any,
            lessThanExpected: Set<String>,
            lessThanEqualExpected: Set<String>,
            greaterThanExpected: Set<String>,
            greaterThanEqualExpected: Set<String>,
            betweenExpected: Set<String>,
            notBetweenExpected: Set<String>
        ): List<OperatorCase> = listOf(
            case(scalarType, QueryCriteriaOperator.LESS_THAN, lessThanExpected, attribute, pivot),
            case(scalarType, QueryCriteriaOperator.LESS_THAN_EQUAL, lessThanEqualExpected, attribute, pivot),
            case(scalarType, QueryCriteriaOperator.GREATER_THAN, greaterThanExpected, attribute, pivot),
            case(
                scalarType,
                QueryCriteriaOperator.GREATER_THAN_EQUAL,
                greaterThanEqualExpected,
                attribute,
                pivot
            ),
            case(scalarType, QueryCriteriaOperator.BETWEEN, betweenExpected, attribute, lowerBound to upperBound),
            case(
                scalarType,
                QueryCriteriaOperator.NOT_BETWEEN,
                notBetweenExpected,
                attribute,
                lowerBound to upperBound
            )
        )

        private fun regexScalarCases(
            scalarType: String,
            attribute: String,
            pattern: String,
            matchesExpected: Set<String>,
            notMatchesExpected: Set<String>
        ): List<OperatorCase> = listOf(
            case(scalarType, QueryCriteriaOperator.MATCHES, matchesExpected, attribute, pattern),
            case(scalarType, QueryCriteriaOperator.NOT_MATCHES, notMatchesExpected, attribute, pattern)
        )

        private fun case(
            scalarType: String,
            operator: QueryCriteriaOperator,
            expected: Set<String>,
            attribute: String,
            value: Any? = null
        ): OperatorCase = OperatorCase(scalarType, operator, expected) {
            QueryCriteria(attribute, operator, value)
        }

        private fun date(offsetDays: Int): Date = Date(DATE_BASE + offsetDays.toLong() * DAY)

        private fun dateText(offsetDays: Int): String = DATE_TEXT_FORMATTER.format(
            Instant.ofEpochMilli(DATE_BASE + offsetDays.toLong() * DAY)
        )
    }
}
