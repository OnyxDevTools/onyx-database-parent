package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.approximateCandidates
import entities.partition.IndexPartitionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApproximateIndexCandidateIntegrationTest {

    private lateinit var databaseDirectory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-approximate-candidates-")
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = databaseDirectory.toString(),
            addShutdownHook = false
        ).apply {
            storeType = StoreType.IN_MEMORY
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager

        repeat(40) { save(partition = 7L, value = 5L) }
        repeat(3) { save(partition = 7L, value = 6L) }
        repeat(11) { save(partition = 8L, value = 5L) }
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
    fun `partitioned bounded IN admits only its budget before response limit`() {
        val query = Query(
            IndexPartitionEntity::class.java,
            approximateCandidates("indexVal", listOf(5L, 6L), maxCandidates = 17)
        ).apply {
            partition = 7L
            maxResults = 5
        }

        val results = manager.executeQuery<IndexPartitionEntity>(query)

        assertEquals(5, results.size)
        assertEquals(17, query.resultsCount)
        assertTrue(results.all { it.partitionId == 7L && it.indexVal in setOf(5L, 6L) })
    }

    @Test
    fun `bounded IN fairly admits from each nonempty route before reusing capacity`() {
        val query = Query(
            IndexPartitionEntity::class.java,
            approximateCandidates("indexVal", listOf(5L, 6L), maxCandidates = 10)
        ).apply { partition = 7L }

        val results = manager.executeQuery<IndexPartitionEntity>(query)

        assertEquals(10, results.size)
        assertEquals(10, query.resultsCount)
        assertEquals(7, results.count { it.indexVal == 5L })
        assertEquals(3, results.count { it.indexVal == 6L })
    }

    @Test
    fun `exact IN retains exhaustive totals while approximate EQ is bounded`() {
        val exact = Query(
            IndexPartitionEntity::class.java,
            QueryCriteria("indexVal", QueryCriteriaOperator.IN, listOf(5L, 6L))
        ).apply {
            partition = 7L
            maxResults = 5
        }
        val approximate = Query(
            IndexPartitionEntity::class.java,
            approximateCandidates("indexVal", 5L, maxCandidates = 9)
        ).apply {
            partition = 7L
            maxResults = 5
        }

        assertEquals(5, manager.executeQuery<IndexPartitionEntity>(exact).size)
        assertEquals(43, exact.resultsCount)
        assertEquals(5, manager.executeQuery<IndexPartitionEntity>(approximate).size)
        assertEquals(9, approximate.resultsCount)
    }

    @Test
    fun `partitioned candidate route requires one partition and sole root`() {
        val allPartitions = Query(
            IndexPartitionEntity::class.java,
            approximateCandidates("indexVal", 5L, maxCandidates = 3)
        )
        val composed = Query(
            IndexPartitionEntity::class.java,
            approximateCandidates("indexVal", 5L, maxCandidates = 3)
                .and("id", QueryCriteriaOperator.GREATER_THAN, 0L)
        ).apply { partition = 7L }

        assertFailsWith<IllegalArgumentException> {
            manager.executeQuery<IndexPartitionEntity>(allPartitions)
        }
        assertFailsWith<IllegalArgumentException> {
            manager.executeQuery<IndexPartitionEntity>(composed)
        }
    }

    private fun save(partition: Long, value: Long) {
        manager.saveEntity<IManagedEntity>(IndexPartitionEntity().apply {
            partitionId = partition
            indexVal = value
        })
    }
}
