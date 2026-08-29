package database.query

import com.onyx.diskmap.DiskMap
import com.onyx.diskmap.store.StoreType
import com.onyx.interactors.scanner.ScannerFactory
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.gt
import com.onyx.persistence.query.search
import com.onyx.persistence.query.select
import com.onyx.vector.FingerprintQueryPlan
import com.onyx.vector.FingerprintQueryPlanner
import com.onyx.vector.VectorCalibration
import com.onyx.vector.VectorEntropy
import entities.VectorPredicateEntity
import entities.VectorPredicateEnum
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class VectorManagedPredicateIntegrationTest(
    private val storeType: StoreType
) {

    private lateinit var databaseDirectory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager
    private lateinit var trackingContext: VectorScannerTrackingSchemaContext
    private var factoryOpen = false

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-vector-predicate-")
        openDatabase()
    }

    @After
    fun cleanup() {
        try {
            closeDatabase()
        } finally {
            if (::databaseDirectory.isInitialized) {
                databaseDirectory.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun compoundPredicateReturnsCompletePersistedEntity() {
        val occurredAt = Date(DAY_START + 20 * HOUR)
        val expected = save(
            entity(
                byteValue = 1,
                shortValue = 2,
                intValue = 7,
                longValue = 100L,
                floatValue = 3.5f,
                price = 10.006,
                exactDouble = 2.5,
                occurredAt = occurredAt,
                charValue = 'Q',
                booleanValue = true,
                enumValue = VectorPredicateEnum.ECHO,
                category = "query-target",
                text = "complete persisted value",
                nullableTag = "hydrated"
            )
        )
        save(entity(intValue = 3, category = "query-target", text = "wrong numeric value"))
        save(entity(intValue = 20, category = "query-decoy", text = "wrong category"))

        val results = manager.from<VectorPredicateEntity>()
            .where("category" eq "query-target")
            .and("intValue" gt 5)
            .list<VectorPredicateEntity>()

        assertEquals(1, results.size)
        val actual = results.single()
        assertEquals(expected.id, actual.id)
        assertEquals(1.toByte(), actual.byteValue)
        assertEquals(2.toShort(), actual.shortValue)
        assertEquals(7, actual.intValue)
        assertEquals(100L, actual.longValue)
        assertEquals(3.5f, actual.floatValue)
        assertEquals(10.006, actual.price)
        assertEquals(2.5, actual.exactDouble)
        assertEquals(occurredAt, actual.occurredAt)
        assertEquals('Q', actual.charValue)
        assertEquals(true, actual.booleanValue)
        assertEquals(VectorPredicateEnum.ECHO, actual.enumValue)
        assertEquals("query-target", actual.category)
        assertEquals("complete persisted value", actual.text)
        assertEquals("hydrated", actual.nullableTag)
    }

    @Test
    fun equalityInNullAndTextQueriesReturnAuthoritativeRecords() {
        val records = saveStandardRecords()

        assertIds(setOf(records.low.id), QueryCriteria("intValue", QueryCriteriaOperator.EQUAL, 7))
        assertIds(
            setOf(records.zero.id, records.high.id),
            QueryCriteria("longValue", QueryCriteriaOperator.IN, listOf(0L, HIGH_LONG))
        )
        assertIds(
            setOf(records.negative.id, records.low.id),
            QueryCriteria("intValue", QueryCriteriaOperator.IN, intArrayOf(-5, 7))
        )
        assertIds(
            setOf(records.zero.id, records.high.id),
            QueryCriteria("intValue", QueryCriteriaOperator.NOT_IN, intArrayOf(-5, 7))
        )
        assertIds(
            setOf(records.zero.id, records.low.id),
            QueryCriteria("category", QueryCriteriaOperator.EQUAL, "alpha")
        )
        assertIds(
            setOf(records.zero.id, records.high.id),
            QueryCriteria("nullableTag", QueryCriteriaOperator.IS_NULL)
        )
        assertIds(
            setOf(records.negative.id, records.low.id),
            QueryCriteria("nullableTag", QueryCriteriaOperator.NOT_NULL)
        )

        trackingContext.resetScannerUsage()
        val textMatches = manager.from<VectorPredicateEntity>()
            .search("amber comet")
            .list<VectorPredicateEntity>()
            .map(VectorPredicateEntity::id)
            .toSet()
        assertEquals(setOf(records.low.id), textMatches)
        assertTrue(trackingContext.vectorScannerScans > 0)
        assertTrue(trackingContext.vectorFingerprintBranchReads > 0)
        assertTrue(trackingContext.fingerprintMatchAllCalls > 0)
        assertEquals(0, trackingContext.fullTableReads, "Search executed FullTableScanner")
    }

    @Test
    fun executionObserverReportsForcedFullTableScanDirectly() {
        save(entity(intValue = 7, text = "forced scan target"))
        save(entity(intValue = 20, text = "forced scan decoy"))
        val criteria = QueryCriteria("intValue", QueryCriteriaOperator.EQUAL, 7)
        val query = Query(VectorPredicateEntity::class.java, criteria)

        trackingContext.resetScannerUsage()
        val references = ScannerFactory.getFullTableScanner(
            trackingContext,
            criteria,
            VectorPredicateEntity::class.java,
            query,
            manager
        ).scan()

        assertEquals(1, references.size)
        assertEquals(1, trackingContext.fullTableReads)
        assertEquals(0, trackingContext.vectorScannerScans)
        assertEquals(0, trackingContext.vectorFingerprintBranchReads)
        assertEquals(0, trackingContext.fingerprintMatchAllCalls)
    }

    @Test
    fun textFieldPredicatesReturnOnlyMatchingPersistedRecords() {
        val records = saveStandardRecords()

        assertIds(
            setOf(records.low.id),
            QueryCriteria("text", QueryCriteriaOperator.STARTS_WITH, "rare amber")
        )
        assertIds(
            setOf(records.low.id),
            QueryCriteria("text", QueryCriteriaOperator.CONTAINS, "amber comet")
        )
        assertIds(
            setOf(records.low.id),
            QueryCriteria("text", QueryCriteriaOperator.CONTAINS_IGNORE_CASE, "AMBER COMET")
        )
    }

    @Test
    fun negativePredicatesReturnTheExactComplementFromPersistedRecords() {
        val records = saveStandardRecords()

        assertIds(
            setOf(records.negative.id, records.zero.id, records.high.id),
            QueryCriteria("intValue", QueryCriteriaOperator.NOT_EQUAL, 7)
        )
        assertIds(
            setOf(records.negative.id, records.low.id),
            QueryCriteria("longValue", QueryCriteriaOperator.NOT_IN, listOf(0L, HIGH_LONG))
        )
        assertIds(
            setOf(records.negative.id, records.high.id),
            QueryCriteria("intValue", QueryCriteriaOperator.NOT_BETWEEN, 0 to 7)
        )
    }

    @Test
    fun rawPostingAliasesAreAuthoritativelyVerified() {
        val target = save(entity(intValue = 7, text = "posting target"))
        val alias = save(entity(intValue = 20, text = "posting alias"))
        val criteria = QueryCriteria("intValue", QueryCriteriaOperator.EQUAL, 7)
        val descriptor = requireNotNull(
            trackingContext.getBaseDescriptorForEntity(VectorPredicateEntity::class.java)
        )
        val feature = assertIs<FingerprintQueryPlan.Feature>(
            FingerprintQueryPlanner(descriptor).compile(criteria)
        ).fingerprint
        val dataFile = trackingContext.getDataFile(descriptor)
        val records: DiskMap<Any, IManagedEntity> = dataFile.getHashMap(
            requireNotNull(descriptor.identifier).type,
            descriptor.entityClass.name
        )
        val indexDescriptor = requireNotNull(
            descriptor.indexes[VectorManagedEntity.REPRESENTATION_FIELD]
        )
        val mapName = descriptor.entityClass.name + indexDescriptor.name +
            "_fingerprint_v${indexDescriptor.encodingVersion}_features"
        val postings = dataFile.getIndexMap(Long::class.java, mapName)

        postings.add(feature.routeKey, records.getRecID(alias.id))

        assertIds(setOf(target.id), criteria)
        assertIds(
            setOf(alias.id),
            QueryCriteria("intValue", QueryCriteriaOperator.NOT_EQUAL, 7)
        )
    }

    @Test
    fun allOrderedPredicatesAndBothBetweenTransportsRespectInclusiveBounds() {
        val records = saveStandardRecords()

        assertIds(
            setOf(records.negative.id),
            QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 0)
        )
        assertIds(
            setOf(records.negative.id, records.zero.id),
            QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN_EQUAL, 0)
        )
        assertIds(
            setOf(records.high.id),
            QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN, 7)
        )
        assertIds(
            setOf(records.low.id, records.high.id),
            QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 7)
        )
        assertIds(
            setOf(records.zero.id, records.low.id),
            QueryCriteria("intValue", QueryCriteriaOperator.BETWEEN, 0 to 7)
        )

        // A two-item List is the shape transported by remote clients.
        assertIds(
            setOf(records.low.id, records.high.id),
            QueryCriteria("intValue", QueryCriteriaOperator.BETWEEN, listOf(7, 20))
        )

        assertIds(
            setOf(records.negative.id, records.zero.id, records.low.id),
            QueryCriteria("longValue", QueryCriteriaOperator.LESS_THAN, HIGH_LONG)
        )
    }

    @Test
    fun compoundAndOrAndNegatedGroupsPreserveBooleanSemantics() {
        val records = saveStandardRecords()

        fun qualifyingGroup(): QueryCriteria =
            QueryCriteria("category", QueryCriteriaOperator.EQUAL, "alpha")
                .and(QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN, 0))
                .or(QueryCriteria("longValue", QueryCriteriaOperator.EQUAL, HIGH_LONG))

        assertIds(setOf(records.low.id, records.high.id), qualifyingGroup())
        trackingContext.resetScannerUsage()
        assertIds(setOf(records.negative.id, records.zero.id), qualifyingGroup().not())
        assertTrue(trackingContext.vectorScannerFeatureReads > 0)
        assertEquals(0, trackingContext.fullTableReads, "Negated vector group executed FullTableScanner")

        val nested = QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 0)
            .and(
                QueryCriteria("category", QueryCriteriaOperator.EQUAL, "alpha")
                    .or(QueryCriteria("nullableTag", QueryCriteriaOperator.IS_NULL))
            )
        assertIds(setOf(records.zero.id, records.low.id, records.high.id), nested)

        val nestedNegatedGroup = QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 0)
            .and(
                QueryCriteria("category", QueryCriteriaOperator.EQUAL, "alpha")
                    .and(QueryCriteria("nullableTag", QueryCriteriaOperator.NOT_NULL))
                    .not()
            )
        trackingContext.resetScannerUsage()
        assertIds(setOf(records.zero.id, records.high.id), nestedNegatedGroup)
        assertTrue(trackingContext.vectorScannerFeatureReads > 0)
        assertEquals(0, trackingContext.fullTableReads, "Nested negated vector group executed FullTableScanner")
    }

    @Test
    fun signedZeroEqualityUsesStoredDoubleSemantics() {
        val negativeZero = save(entity(exactDouble = -0.0, text = "negative zero"))
        val positiveZero = save(entity(exactDouble = 0.0, text = "positive zero"))
        save(entity(exactDouble = 1.0, text = "one"))

        assertIds(
            setOf(positiveZero.id),
            QueryCriteria("exactDouble", QueryCriteriaOperator.EQUAL, 0.0)
        )
        assertIds(
            setOf(negativeZero.id),
            QueryCriteria("exactDouble", QueryCriteriaOperator.EQUAL, -0.0)
        )
    }

    @Test
    fun exactNumericAndMillisecondDateRoutingPreservesBoundaries() {
        val lowerValue = save(entity(price = 10.003, text = "lower value"))
        val exactBoundary = save(entity(price = 10.004, text = "exact boundary"))
        val upperValue = save(entity(price = 10.006, text = "upper value"))

        assertIds(
            setOf(exactBoundary.id),
            QueryCriteria("price", QueryCriteriaOperator.EQUAL, 10.004)
        )
        assertIds(
            setOf(upperValue.id),
            QueryCriteria("price", QueryCriteriaOperator.GREATER_THAN, 10.004)
        )
        assertIds(
            setOf(lowerValue.id, exactBoundary.id),
            QueryCriteria("price", QueryCriteriaOperator.BETWEEN, listOf(10.003, 10.005))
        )

        manager.from<VectorPredicateEntity>().delete()
        val early = save(entity(occurredAt = Date(DAY_START + 1_000), text = "early"))
        val late = save(entity(occurredAt = Date(DAY_START + 20 * HOUR), text = "late"))
        val nextDay = save(entity(occurredAt = Date(DAY_START + DAY + 1_000), text = "next day"))

        assertIds(
            setOf(late.id, nextDay.id),
            QueryCriteria("occurredAt", QueryCriteriaOperator.GREATER_THAN, Date(DAY_START + 12 * HOUR))
        )
        assertIds(
            setOf(early.id, late.id),
            QueryCriteria(
                "occurredAt",
                QueryCriteriaOperator.BETWEEN,
                Date(DAY_START) to Date(DAY_START + DAY - 1)
            )
        )
    }

    @Test
    fun bulkUpdateDeleteAndReopenKeepPredicateRoutesInSync() {
        val changed = save(entity(intValue = 1, category = "lifecycle-old", text = "mutable route"))
        val retained = save(entity(intValue = 200, category = "lifecycle-retained", text = "durable route"))

        assertEquals(
            1,
            manager.from<VectorPredicateEntity>()
                .where("id" eq changed.id)
                .set(
                    AttributeUpdate("category", "lifecycle-new"),
                    AttributeUpdate("intValue", 100)
                )
                .update()
        )
        assertIds(emptySet(), QueryCriteria("category", QueryCriteriaOperator.EQUAL, "lifecycle-old"))
        assertIds(setOf(changed.id), QueryCriteria("category", QueryCriteriaOperator.EQUAL, "lifecycle-new"))
        assertIds(setOf(changed.id, retained.id), QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 100))

        assertEquals(
            1,
            manager.from<VectorPredicateEntity>()
                .where("category" eq "lifecycle-new")
                .delete()
        )
        assertIds(emptySet(), QueryCriteria("category", QueryCriteriaOperator.EQUAL, "lifecycle-new"))

        closeDatabase()
        openDatabase()

        assertIds(
            setOf(retained.id),
            QueryCriteria("category", QueryCriteriaOperator.EQUAL, "lifecycle-retained")
        )
        assertIds(
            setOf(retained.id),
            QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN, 150)
        )
        assertEquals(
            setOf(retained.id),
            manager.from<VectorPredicateEntity>()
                .search("durable route")
                .list<VectorPredicateEntity>()
                .map(VectorPredicateEntity::id)
                .toSet()
        )
    }

    @Test
    fun wildcardSelectionAndEntityMapHideManagedRepresentation() {
        val saved = save(
            entity(
                intValue = 4,
                longValue = 8L,
                price = 12.34,
                exactDouble = 5.0,
                occurredAt = Date(DAY_START),
                category = "visible",
                text = "public fields",
                nullableTag = "also visible"
            )
        )

        val entityMap = saved.toMap(factory.schemaContext)
        assertFalse(entityMap.containsKey("__vectorRepresentation"))
        assertEquals("visible", entityMap["category"])

        val wildcard = manager.from<VectorPredicateEntity>()
            .where("id" eq saved.id)
            .select(Query.WILDCARD_SELECTION)
            .first<Map<String, Any?>>()

        assertFalse(wildcard.containsKey("__vectorRepresentation"))
        assertTrue(wildcard.keys.containsAll(PUBLIC_FIELDS))
    }

    @Test
    fun persistedSemanticSignatureRoutesTheMatchingRecordAndComposesWithPredicates() {
        val calibration = VectorCalibration.fit(
            samples = listOf(
                floatArrayOf(1f, 0f, 0f),
                floatArrayOf(-1f, 0f, 0f),
                floatArrayOf(0f, 1f, 0f),
                floatArrayOf(0f, -1f, 0f),
                floatArrayOf(0f, 0f, 1f),
                floatArrayOf(0f, 0f, -1f)
            ),
            componentCount = 2,
            firstAxisCells = 2,
            otherAxisCells = 2
        )
        val entropy = VectorEntropy(64)
        val queryVector = floatArrayOf(1f, 0f, 0f)
        val querySignature = calibration.encode(queryVector, entropy)

        val target = entity(category = "semantic-target", text = "target document").apply {
            semanticVector(queryVector, calibration)
        }.let(::save)
        entity(category = "semantic-decoy", text = "decoy document").apply {
            semanticVector(floatArrayOf(-1f, 0f, 0f), calibration)
        }.let(::save)

        val results = manager.from<VectorPredicateEntity>()
            .where(search(querySignature, minScore = 0.99f, nearbyBucketRadius = 0))
            .and(QueryCriteria("category", QueryCriteriaOperator.EQUAL, "semantic-target"))
            .list<VectorPredicateEntity>()

        assertEquals(listOf(target.id), results.map(VectorPredicateEntity::id))
    }

    private fun assertIds(expected: Set<Long>, criteria: QueryCriteria) {
        val actual = manager.from<VectorPredicateEntity>()
            .where(criteria)
            .list<VectorPredicateEntity>()
            .map(VectorPredicateEntity::id)
            .toSet()
        assertEquals(expected, actual)
    }

    private fun saveStandardRecords(): StandardRecords {
        val negative = save(
            entity(
                intValue = -5,
                longValue = -900L,
                price = -2.345,
                exactDouble = -1.0,
                occurredAt = Date(DAY_START - DAY),
                category = "bronze",
                text = "negative record",
                nullableTag = "negative tag"
            )
        )
        val zero = save(
            entity(
                intValue = 0,
                longValue = 0L,
                price = 10.004,
                exactDouble = -0.0,
                occurredAt = Date(DAY_START + HOUR),
                category = "alpha",
                text = "zero record"
            )
        )
        val low = save(
            entity(
                intValue = 7,
                longValue = 100L,
                price = 10.006,
                exactDouble = 0.0,
                occurredAt = Date(DAY_START + 20 * HOUR),
                category = "alpha",
                text = "rare amber comet",
                nullableTag = "low tag"
            )
        )
        val high = save(
            entity(
                intValue = 20,
                longValue = HIGH_LONG,
                price = 20.0,
                exactDouble = 1.0,
                occurredAt = Date(DAY_START + DAY),
                category = "beta",
                text = "high record"
            )
        )
        return StandardRecords(negative, zero, low, high)
    }

    private fun entity(
        byteValue: Byte? = null,
        shortValue: Short? = null,
        intValue: Int? = null,
        longValue: Long? = null,
        floatValue: Float? = null,
        price: Double? = null,
        exactDouble: Double? = null,
        occurredAt: Date? = null,
        charValue: Char? = null,
        booleanValue: Boolean? = null,
        enumValue: VectorPredicateEnum? = null,
        category: String? = null,
        text: String? = null,
        nullableTag: String? = null
    ): VectorPredicateEntity = VectorPredicateEntity().also {
        it.byteValue = byteValue
        it.shortValue = shortValue
        it.intValue = intValue
        it.longValue = longValue
        it.floatValue = floatValue
        it.price = price
        it.exactDouble = exactDouble
        it.occurredAt = occurredAt
        it.charValue = charValue
        it.booleanValue = booleanValue
        it.enumValue = enumValue
        it.category = category
        it.text = text
        it.nullableTag = nullableTag
    }

    private fun save(entity: VectorPredicateEntity): VectorPredicateEntity =
        manager.saveEntity<IManagedEntity>(entity) as VectorPredicateEntity

    private fun openDatabase() {
        val location = databaseDirectory.toString()
        trackingContext = VectorScannerTrackingSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = trackingContext,
            addShutdownHook = false
        ).apply {
            storeType = this@VectorManagedPredicateIntegrationTest.storeType
            setCredentials("admin", "admin")
            initialize()
        }
        factoryOpen = true
        manager = factory.persistenceManager
    }

    private fun closeDatabase() {
        if (::factory.isInitialized && factoryOpen) {
            try {
                factory.close()
            } finally {
                factoryOpen = false
            }
        }
    }

    private data class StandardRecords(
        val negative: VectorPredicateEntity,
        val zero: VectorPredicateEntity,
        val low: VectorPredicateEntity,
        val high: VectorPredicateEntity
    )

    companion object {
        private const val HIGH_LONG = 9_000_000_000L
        private const val HOUR = 3_600_000L
        private const val DAY = 86_400_000L
        private const val DAY_START = 1_728_000_000_000L
        private val PUBLIC_FIELDS = setOf(
            "id",
            "byteValue",
            "shortValue",
            "intValue",
            "longValue",
            "floatValue",
            "price",
            "exactDouble",
            "occurredAt",
            "charValue",
            "booleanValue",
            "enumValue",
            "category",
            "text",
            "nullableTag"
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun persistenceManagersToTest(): Collection<Array<Any>> = listOf(
            arrayOf(StoreType.FILE),
            arrayOf(StoreType.MEMORY_MAPPED_FILE)
        )
    }
}
