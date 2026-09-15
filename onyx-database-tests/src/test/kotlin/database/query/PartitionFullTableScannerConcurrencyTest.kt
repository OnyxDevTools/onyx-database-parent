package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.extension.validate
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.search
import entities.VectorPartitionedEntity
import entities.partition.FullTablePartitionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

class PartitionFullTableScannerConcurrencyTest {

    private lateinit var databaseDirectory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-partition-full-scan-concurrency-")
        val location = databaseDirectory.toString()
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            addShutdownHook = false,
        ).apply {
            storeType = StoreType.IN_MEMORY
            maxCardinality = 1_000
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager
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
    fun parallelPartitionPredicatesDoNotUseTheQueryMonitorOrCriteriaScratchState() {
        saveScalar(partition = 1L, value = 11L)
        saveScalar(partition = 2L, value = 22L)

        val child = QueryCriteria("partitionId", QueryCriteriaOperator.NOT_NULL)
        val criteria = QueryCriteria("indexVal", QueryCriteriaOperator.GREATER_THAN, 0L).and(child)
        val query = Query(
            FullTablePartitionEntity::class.java,
            criteria,
            QueryOrder("id", true),
        ).apply {
            partition = QueryPartitionMode.ALL
        }

        val results = executeWhileHoldingQueryMonitor(query) {
            manager.executeQuery<FullTablePartitionEntity>(query)
        }

        assertEquals(setOf(11L, 22L), results.map(FullTablePartitionEntity::indexVal).toSet())
        assertFalse(criteria.meetsCriteria, "Predicate evaluation leaked into the root criterion")
        assertFalse(child.meetsCriteria, "Predicate evaluation leaked into a child criterion")
    }

    @Test
    fun parallelPartitionScoresAreVisibleToConcurrentCollectors() {
        saveVector(region = "north", tag = "both", body = "amber comet")
        saveVector(region = "south", tag = "one", body = "amber only")
        saveVector(region = "west", tag = "none", body = "unrelated payload")

        val query = Query(
            VectorPartitionedEntity::class.java,
            search(VectorSearchQuery(text = "amber comet", requireAllTerms = false)),
            QueryOrder("tag", true),
        ).apply {
            partition = QueryPartitionMode.ALL
            selections = listOf("tag", Query.SCORE_SELECTION)
        }
        val descriptor = manager.context.getDescriptorForEntity(VectorPartitionedEntity::class.java, "")
        query.validate(manager.context, descriptor)
        val interactor = DefaultQueryInteractorTestBridge.forceFullTable(
            descriptor,
            manager,
            manager.context,
        )

        val collector = executeWhileHoldingQueryMonitor(query) {
            interactor.getReferencesForQuery<Map<String, Any?>>(query)
        }
        val scoresByTag = collector.results.associate { row ->
            row.getValue("tag") as String to row[Query.SCORE_SELECTION] as Float
        }

        assertEquals(mapOf("both" to 1.0f, "one" to 0.5f), scoresByTag)
        assertEquals(2, query.fullTextScores?.size)
    }

    private fun saveScalar(partition: Long, value: Long) {
        manager.saveEntity<IManagedEntity>(FullTablePartitionEntity().apply {
            partitionId = partition
            indexVal = value
        })
    }

    private fun saveVector(region: String, tag: String, body: String) {
        manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            this.region = region
            this.tag = tag
            this.body = body
        })
    }

    private fun <T> executeWhileHoldingQueryMonitor(query: Query, action: () -> T): T {
        val executor = Executors.newSingleThreadExecutor()
        var future: Future<T>? = null
        return try {
            synchronized(query) {
                val submitted = executor.submit<T> { action() }
                future = submitted
                try {
                    submitted.get(5, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    fail("Partition predicate evaluation blocked on the query monitor")
                }
            }
        } finally {
            future?.cancel(true)
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
