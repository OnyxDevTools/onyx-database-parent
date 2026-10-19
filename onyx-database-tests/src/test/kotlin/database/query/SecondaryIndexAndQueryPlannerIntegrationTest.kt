package database.query

import com.onyx.buffer.BufferStream
import com.onyx.diskmap.store.StoreType
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.interactors.query.impl.DefaultQueryInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import com.onyx.persistence.query.QueryPartitionMode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecondaryIndexAndQueryPlannerIntegrationTest {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var context: IndexedAndTrackingSchemaContext
    private lateinit var manager: PersistenceManager
    private lateinit var automatic: DefaultQueryInteractor
    private lateinit var fullTable: DefaultQueryInteractor
    private val expected = listOf(32L, 96L, 160L, 224L)

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-secondary-and-planner-")
        val location = directory.toString()
        context = IndexedAndTrackingSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(location, location, context, false).apply {
            storeType = StoreType.IN_MEMORY
            initialize()
        }
        manager = factory.persistenceManager
        for (identifier in 1L..256L) {
            manager.saveEntity<IManagedEntity>(SecondaryAndEntity().apply {
                id = identifier
                status = if (identifier % 64L == 0L) 2 else 1
                bucket = if (identifier % 32L == 0L) 7 else 0
                zone = (identifier % 3L).toInt()
            })
        }
        val descriptor = context.getDescriptorForEntity(SecondaryAndEntity::class.java, "")
        automatic = DefaultQueryInteractor(descriptor, manager, context)
        fullTable = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, context)
    }

    @After
    fun cleanup() {
        try {
            if (::factory.isInitialized) factory.close()
        } finally {
            if (::directory.isInitialized) directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `selective secondary equality avoids broad postings in either order`() {
        for (broadFirst in listOf(true, false)) {
            val query = query(conjunction(broadFirst))
            context.resetWork()
            assertEquals(expected, execute(automatic, query))
            assertEquals(expected.size, query.resultsCount)
            assertSelectiveWork()
            assertEquals(8, context.pointProbes)
            assertEquals(if (broadFirst) 73L else 17L, context.postingVisits)
            assertEquals(expected, execute(fullTable, query(conjunction(broadFirst))))
        }
    }

    @Test
    fun `empty and disjoint intersections remain exact without scanning broad postings`() {
        for (broadFirst in listOf(true, false)) {
            val missing = eq("bucket", 99)
            val broad = eq("status", 1)
            context.resetWork()
            assertEquals(emptyList(), execute(automatic, query(if (broadFirst) broad.and(missing) else missing.and(broad))))
            assertSelectiveWork()
            assertEquals(0, context.pointProbes)
            assertEquals(if (broadFirst) 65L else 0L, context.postingVisits)
        }
        context.resetWork()
        assertEquals(emptyList(), execute(automatic, query(eq("status", 1).and(eq("status", 2)))))
        assertSelectiveWork()
        assertEquals(4, context.pointProbes)
    }

    @Test
    fun `nested three index conjunctions keep their complete semantics and criteria shape`() {
        val criteria = eq("status", 1).and(eq("zone", 0).and(eq("bucket", 7)))
        val query = query(criteria)
        query.getAllCriteria()
        val original = shape(criteria)
        repeat(2) {
            context.resetWork()
            assertEquals(listOf(96L), execute(automatic, query))
            assertEquals(1, query.resultsCount)
            assertEquals(original, shape(criteria))
            assertSelectiveWork(maxVisits = 138)
        }
        assertEquals(listOf(96L), execute(fullTable, query(eq("bucket", 7).and(eq("status", 1)).and(eq("zone", 0)))))
    }

    @Test
    fun `broad intermediate postings do not exceed a small final cardinality limit`() {
        context.maxCardinality = 4
        context.resetWork()
        assertEquals(expected, execute(automatic, query(conjunction())))
        assertSelectiveWork()

        context.maxCardinality = 3
        assertFailsWith<MaxCardinalityExceededException> {
            execute(automatic, query(conjunction()))
        }
    }

    @Test
    fun `eager lazy cached count and ordered pages retain complete result counts`() {
        for (cacheEnabled in listOf(false, true)) {
            repeat(2) {
                val query = query(conjunction()).apply {
                    cache = cacheEnabled
                    queryOrders = listOf(QueryOrder("id", false))
                    firstRow = 1
                    maxResults = 2
                }
                context.resetWork()
                assertEquals(listOf(160L, 96L), manager.executeQuery<SecondaryAndEntity>(query).map { it.id })
                assertEquals(4, query.resultsCount)
                assertSelectiveWork()
            }
        }
        context.resetWork()
        val lazy = query(conjunction()).apply { firstRow = 1; maxResults = 2 }
        assertEquals(listOf(96L, 160L), manager.executeLazyQuery<SecondaryAndEntity>(lazy).map { it.id })
        assertEquals(4, lazy.resultsCount)
        assertSelectiveWork()

        context.resetWork()
        assertEquals(4L, manager.countForQuery(query(conjunction())))
        assertSelectiveWork()
    }

    @Test
    fun `broad indexes retain exact results by materializing the root and probing its candidates`() {
        val criteria = eq("status", 1).and(eq("bucket", 0))
        val query = query(criteria)
        context.resetWork()
        val actual = execute(automatic, query)
        assertEquals((1L..256L).filter { it % 32L != 0L }, actual)
        assertEquals(248, query.resultsCount)
        assertEquals(130L, context.postingVisits)
        assertEquals(252, context.pointProbes)
        assertEquals(1, context.indexLookups)
        assertEquals(252L, context.materializedPostings)
        assertEquals(actual, execute(fullTable, query(eq("status", 1).and(eq("bucket", 0)))))
    }

    @Test
    fun `64 candidates seed directly while 65 use root enumeration and residual probes`() {
        for (candidateCount in listOf(64, 65)) {
            for (identifier in 1L..256L) {
                manager.saveEntity<IManagedEntity>(SecondaryAndEntity().apply {
                    id = identifier
                    status = 1
                    bucket = if (identifier <= candidateCount) 7 else 0
                })
            }
            context.resetWork()
            val query = query(conjunction()).apply { maxResults = 1 }
            assertEquals(listOf(1L), execute(automatic, query))
            assertEquals(candidateCount, query.resultsCount)
            if (candidateCount == 64) {
                assertSelectiveWork(maxVisits = 129)
                assertEquals(64, context.pointProbes)
            } else {
                assertEquals(130L, context.postingVisits)
                assertEquals(1, context.indexLookups)
                assertEquals(256L, context.materializedPostings)
                assertEquals(256, context.pointProbes)
            }
        }
    }

    @Test
    fun `custom indexes without streaming or posting probes retain the ordinary query path`() {
        for (streamingSupported in listOf(false, true)) {
            context.streamingSupported = streamingSupported
            context.pointProbesSupported = false
            context.resetWork()
            assertEquals(expected, execute(automatic, query(conjunction())))
            assertEquals(2, context.indexLookups)
            assertEquals(260L, context.materializedPostings)
            assertEquals(0, context.fullTableScans)
        }
    }

    @Test
    fun `cancellation during seed selection does not fall back to broad scans`() {
        val query = query(conjunction())
        context.afterStreaming = Runnable { query.isTerminated = true }
        context.resetWork()
        assertEquals(emptyList(), execute(automatic, query))
        assertSelectiveWork()
        assertEquals(1, context.streamingLookups)
        assertEquals(65L, context.postingVisits)
        assertEquals(0, context.pointProbes)
    }

    @Test
    fun `projected results use the ordinary collector after index intersection`() {
        fun projection() = Query(SecondaryAndEntity::class.java, listOf("id", "bucket"), conjunction(),
            listOf(QueryOrder("id", false))).apply { firstRow = 1; maxResults = 2 }
        context.resetWork()
        val query = projection()
        val actual = automatic.getReferencesForQuery<Map<String, Any>>(query).results.toList()
        assertEquals(listOf(160L, 96L), actual.map { it["id"] })
        assertEquals(listOf(7, 7), actual.map { it["bucket"] })
        assertEquals(4, query.resultsCount)
        assertSelectiveWork()
        assertEquals(actual, fullTable.getReferencesForQuery<Map<String, Any>>(projection()).results.toList())
    }

    @Test
    fun `fallback predicates retain semantics and eligible residual equalities use candidate probes`() {
        val cases = listOf<Pair<Int, () -> QueryCriteria>>(
            0 to { eq("status", 1).or(eq("bucket", 7)) },
            0 to { conjunction().not() },
            0 to { eq("status", 1).and(QueryCriteria("bucket", QueryCriteriaOperator.GREATER_THAN, 0)) },
            252 to { eq("status", 1).and(QueryCriteria("bucket", QueryCriteriaOperator.IN, listOf(7, 7))) },
            252 to { conjunction().and(eq("residual", "present")) }
        )
        for ((expectedProbes, criteria) in cases) {
            context.resetWork()
            val query = query(criteria())
            val actual = execute(automatic, query)
            assertEquals(0, context.streamingLookups)
            assertEquals(expectedProbes, context.pointProbes)
            val referenceQuery = query(criteria())
            assertEquals(execute(fullTable, referenceQuery), actual)
            assertEquals(referenceQuery.resultsCount, query.resultsCount)
        }
    }

    @Test
    fun `point probes preserve numeric coercion and native date comparisons`() {
        context.resetWork()
        assertEquals(expected, execute(automatic, query(eq("status", 1.5).and(eq("bucket", 7)))))
        assertSelectiveWork()

        for (identifier in 1L..100L) {
            manager.saveEntity<IManagedEntity>(SecondaryAndValueEntity().apply {
                id = identifier
                marker = if (identifier == 42L) "needle" else "hay"
                recordedAt = Date(1_000L)
            })
        }
        val descriptor = context.getDescriptorForEntity(SecondaryAndValueEntity::class.java, "")
        val interactor = DefaultQueryInteractor(descriptor, manager, context)
        context.resetWork()
        val query = Query(SecondaryAndValueEntity::class.java,
            eq("marker", "needle").and(eq("recordedAt", Date(1_000L))))
        val results = interactor.getReferencesForQuery<SecondaryAndValueEntity>(query).results.map { it as SecondaryAndValueEntity }
        assertEquals(listOf(42L), results.map { it.id })
        assertTrue(results.single().recordedAt is Timestamp)
        assertEquals(1, query.resultsCount)
        assertSelectiveWork()
        assertEquals(1, context.pointProbes)

        // Null equality remains on the native scanners, whose indexes omit null values.
        context.resetWork()
        val nullable = Query(SecondaryAndValueEntity::class.java, eq("marker", "needle").and(eq("recordedAt", null)))
        assertEquals(emptyList(), interactor.getReferencesForQuery<SecondaryAndValueEntity>(nullable).results.toList())
        assertEquals(0, context.streamingLookups)
    }

    @Test
    fun `requested missing and all partitions preserve reference identity`() {
        savePartitions(broadSecondPartition = false)
        for (partition in listOf(7L, 99L, QueryPartitionMode.ALL)) {
            val expectedPartitions = when (partition) {
                7L -> listOf(7L to 4L)
                99L -> emptyList()
                else -> listOf(7L to 4L, 8L to 4L)
            }
            val descriptor = context.getDescriptorForEntity(SecondaryAndPartitionEntity::class.java,
                if (partition === QueryPartitionMode.ALL) "" else partition)
            val indexed = DefaultQueryInteractor(descriptor, manager, context)
            context.resetWork()
            val query = partitionQuery(partition)
            assertEquals(expectedPartitions, executePartition(indexed, query))
            assertEquals(expectedPartitions.size, query.resultsCount)
            assertSelectiveWork(maxVisits = 132)
            val forced = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, context)
            assertEquals(expectedPartitions, executePartition(forced, partitionQuery(partition)))
        }
    }

    @Test
    fun `a broad later partition discards the partial seed and probes candidates across all partitions`() {
        savePartitions(broadSecondPartition = true)
        val descriptor = context.getDescriptorForEntity(SecondaryAndPartitionEntity::class.java, "")
        val indexed = DefaultQueryInteractor(descriptor, manager, context)
        context.resetWork()
        val query = partitionQuery(QueryPartitionMode.ALL)
        val expected = listOf(7L to 4L) + (1L..100L).map { 8L to it }
        assertEquals(expected, executePartition(indexed, query))
        assertEquals(expected.size, query.resultsCount)
        assertEquals(2, context.indexLookups)
        assertEquals(0, context.fullTableScans)
    }

    @Test
    fun `cardinality applies across partitions after their intersections`() {
        savePartitions(broadSecondPartition = false)
        val descriptor = context.getDescriptorForEntity(SecondaryAndPartitionEntity::class.java, "")
        val indexed = DefaultQueryInteractor(descriptor, manager, context)
        context.maxCardinality = 2
        assertEquals(listOf(7L to 4L, 8L to 4L), executePartition(indexed, partitionQuery(QueryPartitionMode.ALL)))
        context.maxCardinality = 1
        assertFailsWith<MaxCardinalityExceededException> {
            executePartition(indexed, partitionQuery(QueryPartitionMode.ALL))
        }
    }

    private fun savePartitions(broadSecondPartition: Boolean) {
        for (partition in listOf(7L, 8L)) {
            for (identifier in 1L..100L) {
                manager.saveEntity<IManagedEntity>(SecondaryAndPartitionEntity().apply {
                    id = identifier
                    partitionId = partition
                    status = 1
                    bucket = if (identifier == 4L || partition == 8L && broadSecondPartition) 7 else 0
                })
            }
        }
    }

    private fun partitionQuery(partition: Any) = Query(SecondaryAndPartitionEntity::class.java,
        conjunction(), QueryOrder("partitionId", true), QueryOrder("id", true)).apply { this.partition = partition }

    private fun executePartition(interactor: DefaultQueryInteractor, query: Query): List<Pair<Long, Long>> =
        interactor.getReferencesForQuery<SecondaryAndPartitionEntity>(query).results.map {
            val entity = it as SecondaryAndPartitionEntity
            entity.partitionId to entity.id
        }

    private fun assertSelectiveWork(maxVisits: Long = 73) {
        assertEquals(0, context.indexLookups, "Selective plans must not materialize broad postings")
        assertEquals(0L, context.materializedPostings)
        assertEquals(0, context.fullTableScans)
        assertEquals(0, context.referenceFilterScans, "Native index equality must not be replaced by entity comparison")
        assertTrue(context.postingVisits <= maxVisits, "Too much posting traversal: ${context.postingVisits}")
    }

    private fun query(criteria: QueryCriteria) = Query(SecondaryAndEntity::class.java, criteria, QueryOrder("id", true))
    private fun execute(interactor: DefaultQueryInteractor, query: Query): List<Long> =
        interactor.getReferencesForQuery<SecondaryAndEntity>(query).results.map { (it as SecondaryAndEntity).id }
    private fun eq(attribute: String, value: Any?) = QueryCriteria(attribute, QueryCriteriaOperator.EQUAL, value)
    private fun conjunction(broadFirst: Boolean = true): QueryCriteria =
        if (broadFirst) eq("status", 1).and(eq("bucket", 7)) else eq("bucket", 7).and(eq("status", 1))
    private fun shape(criteria: QueryCriteria): List<Any?> = listOf(criteria.attribute, criteria.operator,
        criteria.value, criteria.isAnd, criteria.isOr, criteria.isNot, criteria.flip, criteria.subCriteria.map(::shape))
}

@Entity
class SecondaryAndEntity : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Index @Attribute var status: Int = 0
    @Index @Attribute var bucket: Int = 0
    @Index @Attribute var zone: Int = 0
    @Attribute var residual: String = "present"
}

@Entity
class SecondaryAndValueEntity : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Index @Attribute var marker: String = ""
    @Index @Attribute var recordedAt: Date? = null

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        recordedAt = recordedAt?.let { Timestamp(it.time) }
    }
}

@Entity
class SecondaryAndPartitionEntity : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Partition @Attribute var partitionId: Long = 0L
    @Index @Attribute var status: Int = 0
    @Index @Attribute var bucket: Int = 0
}
