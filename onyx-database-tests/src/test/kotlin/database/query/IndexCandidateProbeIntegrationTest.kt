package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.interactors.query.impl.DefaultQueryInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator.*
import com.onyx.persistence.query.QueryOrder
import com.onyx.persistence.query.QueryPartitionMode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import kotlin.test.assertEquals

/** Reproduces the ingestion's session timestamp range followed by interval = MINUTE. */
class IndexCandidateProbeIntegrationTest {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var context: IndexedAndTrackingSchemaContext
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-index-candidate-probe-")
        val location = directory.toString()
        context = IndexedAndTrackingSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(location, location, context, false).apply {
            storeType = StoreType.MEMORY_MAPPED_FILE
            initialize()
        }
        manager = factory.persistenceManager
        for (symbol in listOf("A", "B")) {
            for (identifier in 1L..1024L) {
                manager.saveEntity<IManagedEntity>(IndexCandidateBar().apply {
                    id = identifier
                    underlying = symbol
                    timestamp = Date(identifier * 60_000L)
                    interval = if (identifier % 10L == 0L) "HOUR" else "MINUTE"
                })
            }
        }
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
    fun `session endpoint probes only timestamp candidates and preserves count despite limit one`() {
        val query = query().apply { maxResults = 1 }
        context.resetWork()
        assertEquals(listOf("A" to 119L), execute(query))
        assertEquals(18, query.resultsCount)
        assertEquals(20, context.pointProbes)
        assertEquals(1, context.indexLookups)
        assertEquals(20L, context.materializedPostings)
        assertEquals(0, context.fullTableScans)
        assertEquals(0, context.referenceFilterScans)
        assertEquals(listOf("A" to 119L), execute(query().apply { maxResults = 1 }, fullTable = true))
    }

    @Test
    fun `full regular session with nine persisted intervals fits the probe budget`() {
        for (minute in 100L until 490L) {
            repeat(9) { intervalNumber ->
                manager.saveEntity<IManagedEntity>(IndexCandidateBar().apply {
                    id = minute * 10L + intervalNumber
                    underlying = "C"
                    timestamp = Date(minute * 60_000L)
                    interval = if (intervalNumber == 0) "MINUTE" else "ROLLUP_$intervalNumber"
                })
            }
        }
        context.resetWork()
        val query = query(partition = "C", to = 490L).apply { maxResults = 1 }
        assertEquals(listOf("C" to 4890L), execute(query))
        assertEquals(390, query.resultsCount)
        assertEquals(3510, context.pointProbes)
        assertEquals(1, context.indexLookups)
        assertEquals(3510L, context.materializedPostings)
    }

    @Test
    fun `all partitions probe each reference in its own posting tree`() {
        // Equal record offsets in different partitions must not share a posting membership result.
        manager.saveEntity<IManagedEntity>(IndexCandidateBar().apply {
            id = 119L
            underlying = "B"
            timestamp = Date(id * 60_000L)
            interval = "HOUR"
        })
        val query = query(QueryPartitionMode.ALL).apply { maxResults = 3 }
        context.resetWork()
        val actual = execute(query)
        assertEquals(35, query.resultsCount)
        assertEquals(40, context.pointProbes)
        assertEquals(2, context.indexLookups)
        assertEquals(40L, context.materializedPostings)
        assertEquals(execute(query(QueryPartitionMode.ALL).apply { maxResults = 3 }, fullTable = true), actual)
    }

    @Test
    fun `IN duplicate values and nulls preserve union membership without duplicate collection`() {
        for (operator in listOf(EQUAL, IN)) {
            val query = query(value = listOf("MINUTE", "MINUTE", "HOUR", null), operator = operator)
            context.resetWork()
            assertEquals((119L downTo 100L).map { "A" to it }, execute(query))
            assertEquals(20, query.resultsCount)
            assertEquals(22, context.pointProbes)
            assertEquals(1, context.indexLookups)
        }
    }

    @Test
    fun `custom indexes retry materialization before any collection occurs`() {
        context.pointProbesSupported = false
        val query = query().apply { maxResults = 1 }
        context.resetWork()
        assertEquals(listOf("A" to 119L), execute(query))
        assertEquals(18, query.resultsCount)
        assertEquals(2, context.indexLookups)
        assertEquals(942L, context.materializedPostings)
    }

    @Test
    fun `custom indexes can fall back after successful probes without losing or duplicating matches`() {
        context.unsupportedAfterPointProbes = 3
        context.resetWork()
        val query = query()
        assertEquals((119L downTo 100L).filter { it % 10L != 0L }.map { "A" to it }, execute(query))
        assertEquals(18, query.resultsCount)
        assertEquals(4, context.pointProbes)
        assertEquals(2, context.indexLookups)
    }

    @Test
    fun `custom null equality retains its materializing lookup semantics`() {
        context.nullIndexValueAlias = "MINUTE"
        context.resetWork()
        val query = query(value = null).apply { maxResults = 1 }
        assertEquals(listOf("A" to 119L), execute(query))
        assertEquals(18, query.resultsCount)
        assertEquals(1, context.pointProbes)
        assertEquals(2, context.indexLookups)
    }

    @Test
    fun `probe budget includes all IN routes and partitions before falling back`() {
        context.resetWork()
        val query = query(QueryPartitionMode.ALL, value = listOf("MINUTE", "HOUR", "DAY"),
            operator = IN, from = 1L, to = 1025L).apply { maxResults = 1 }
        assertEquals(listOf("A" to 1024L), execute(query))
        assertEquals(2048, query.resultsCount)
        assertEquals(0, context.pointProbes)
        assertEquals(8, context.indexLookups)
        assertEquals(4096L, context.materializedPostings)
    }

    @Test
    fun `empty range and null equality skip broad postings`() {
        for ((query, probes) in listOf(query(from = 2000L, to = 2010L) to 0, query(value = null) to 20)) {
            context.resetWork()
            assertEquals(emptyList(), execute(query))
            assertEquals(0, query.resultsCount)
            assertEquals(probes, context.pointProbes)
            assertEquals(1, context.indexLookups)
        }
    }

    @Test
    fun `eager lazy count and cached pages preserve results`() {
        for (cached in listOf(false, true)) {
            repeat(2) {
                val query = query().apply { cache = cached; firstRow = 1; maxResults = 2 }
                assertEquals(listOf(118L, 117L), manager.executeQuery<IndexCandidateBar>(query).map { it.id })
                assertEquals(18, query.resultsCount)
            }
        }
        val lazy = query().apply { firstRow = 1; maxResults = 2 }
        assertEquals(listOf(118L, 117L), manager.executeLazyQuery<IndexCandidateBar>(lazy).map { it.id })
        assertEquals(18, lazy.resultsCount)
        assertEquals(18L, manager.countForQuery(query()))
    }

    private fun query(
        partition: Any = "A",
        value: Any? = "MINUTE",
        operator: com.onyx.persistence.query.QueryCriteriaOperator = EQUAL,
        from: Long = 100L,
        to: Long = 120L
    ) = Query(IndexCandidateBar::class.java,
        QueryCriteria("timestamp", GREATER_THAN_EQUAL, Date(from * 60_000L))
            .and(QueryCriteria("timestamp", LESS_THAN, Date(to * 60_000L)))
            .and(QueryCriteria("interval", operator, value)),
        QueryOrder("timestamp", false), QueryOrder("underlying", true)
    ).apply { this.partition = partition }

    private fun execute(query: Query, fullTable: Boolean = false): List<Pair<String, Long>> {
        val descriptor = context.getDescriptorForEntity(IndexCandidateBar::class.java,
            if (query.partition === QueryPartitionMode.ALL) "" else query.partition)
        val interactor = if (fullTable) DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, context)
            else DefaultQueryInteractor(descriptor, manager, context)
        return interactor.getReferencesForQuery<IndexCandidateBar>(query).results.map {
            val bar = it as IndexCandidateBar
            bar.underlying to bar.id
        }
    }
}

@Entity
class IndexCandidateBar : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Partition @Attribute var underlying: String = ""
    @Index @Attribute var timestamp: Date = Date(0L)
    @Index @Attribute var interval: String = ""
}
