package database.query

import com.onyx.buffer.BufferStream
import com.onyx.diskmap.store.StoreType
import com.onyx.exception.AttributeMissingException
import com.onyx.exception.MaxCardinalityExceededException
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
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class IndexedCountIntegrationTest(private val storeType: StoreType) {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var context: IndexedCountSchemaContext
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-indexed-count-")
        openDatabase()
        for (id in 1L..256L) {
            manager.saveEntity(IndexedCountEntity().apply {
                this.id = id
                status = when (id % 4L) { 0L, 2L -> "open"; 1L -> "closed"; else -> null }
                bucket = (id % 8L).toInt()
                recordedAt = Date(1000L)
                payload = "x".repeat(1024)
            })
        }
    }

    private fun openDatabase() {
        val location = directory.toString()
        context = IndexedCountSchemaContext(location)
        factory = EmbeddedPersistenceManagerFactory(location, location, context, false).apply {
            storeType = this@IndexedCountIntegrationTest.storeType
            initialize()
        }
        manager = factory.persistenceManager
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
    fun `public equality counts stream duplicate index values and misses without entity reads`() {
        repeat(2) {
            for ((value, expected) in listOf("open" to 128L, "closed" to 64L, "missing" to 0L)) {
                assertIndexedCount(expected, query(value))
            }
            assertIndexedCount(32L, Query(IndexedCountEntity::class.java, "bucket" eq 2))
        }
        resetWork()
        assertEquals(128L, manager.from<IndexedCountEntity>().where("status" eq "open").count())
        assertIndexWork()
    }

    @Test
    fun `ordered paginated and lazy counts retain the full total and reset search scores`() {
        for (lazy in listOf(false, true)) {
            val query = query().apply {
                queryOrders = listOf(QueryOrder("id", false))
                firstRow = 10
                maxResults = 3
                isLazy = lazy
                fullTextScores = emptyMap()
            }
            assertIndexedCount(128L, query)
            assertNull(query.fullTextScores)
            assertEquals(10, query.firstRow)
            assertEquals(3, query.maxResults)
        }
    }

    @Test
    fun `counts observe inserts updates deletions and persisted postings after reopening`() {
        val changed = manager.from<IndexedCountEntity>().where("id" eq 1L).firstOrNull<IndexedCountEntity>()!!
        changed.status = "open"
        changed.payload = "y".repeat(8192)
        manager.saveEntity(changed)
        assertIndexedCount(129L, query())
        changed.status = "closed"
        manager.saveEntity(changed)
        assertIndexedCount(128L, query())
        val deleted = manager.from<IndexedCountEntity>().where("id" eq 2L).firstOrNull<IndexedCountEntity>()!!
        manager.deleteEntity(deleted)
        assertIndexedCount(127L, query())
        manager.saveEntity(IndexedCountEntity().apply { id = 300L; status = "open" })
        assertIndexedCount(128L, query())

        if (storeType == StoreType.MEMORY_MAPPED_FILE) {
            factory.close()
            openDatabase()
            assertIndexedCount(128L, query())
        }
    }

    @Test
    fun `partition counts preserve separate record domains and missing partitions`() {
        seedPartitions()
        for (partition in listOf("", QueryPartitionMode.ALL)) {
            resetWork()
            val query = partitionQuery(partition)
            assertEquals(8L, manager.countForQuery(query))
            assertEquals(8, query.resultsCount)
            assertIndexWork(context.getAllPartitions(IndexedCountPartitionEntity::class.java).size)
        }
        assertIndexedCount(4L, partitionQuery(10L))
        assertIndexedCount(4L, partitionQuery(20L))
        assertIndexedCount(0L, partitionQuery(30L))
    }

    @Test
    fun `cardinality is enforced at the same boundary even when the page is small`() {
        val query = query().apply { maxResults = 1 }
        context.maxCardinality = 128
        assertIndexedCount(128L, query)
        context.maxCardinality = 7
        resetWork()
        assertEquals(7, assertFailsWith<MaxCardinalityExceededException> {
            manager.countForQuery(query)
        }.maxCardinality)
        assertIndexWork()
        assertEquals(8, context.postingVisits.get(), "The count should stop when cardinality is exceeded")
        context.maxCardinality = 0
        assertIndexedCount(0L, query("missing"))
        assertFailsWith<MaxCardinalityExceededException> { manager.countForQuery(query()) }
    }

    @Test
    fun `cardinality limit applies across all partitions`() {
        seedPartitions()
        context.maxCardinality = 4
        assertIndexedCount(4L, partitionQuery(10L))
        resetWork()
        assertFailsWith<MaxCardinalityExceededException> { manager.countForQuery(partitionQuery()) }
        assertIndexWork(expectedLookups = 2)
        assertEquals(5, context.postingVisits.get())
    }

    @Test
    fun `compound null negated range and mixed type queries preserve collector counts`() {
        val criteria = listOf<() -> QueryCriteria>(
            { ("status" eq "open").and(QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 100L)) },
            { ("status" eq "open").or("status" eq "closed") },
            { ("status" eq "open").not() },
            { QueryCriteria("status", QueryCriteriaOperator.IS_NULL) },
            { QueryCriteria("status", QueryCriteriaOperator.EQUAL, null) },
            { QueryCriteria("status", QueryCriteriaOperator.IN, listOf("open", "open", "closed")) },
            { QueryCriteria("bucket", QueryCriteriaOperator.GREATER_THAN, 4) },
            { "bucket" eq "2" },
            { "recordedAt" eq Date(1000L) },
        )
        for (criterion in criteria) {
            assertCollectorCount { Query(IndexedCountEntity::class.java, criterion()) }
        }
    }

    @Test
    fun `selected distinct grouped and aggregate counts retain their collector semantics`() {
        assertCollectorCount { query().apply { selections = listOf("status"); isDistinct = true } }
        assertCollectorCount { query().apply { selections = listOf("status"); groupBy = listOf("status") } }
        assertCollectorCount { query().apply { selections = listOf("count(id)") } }
        assertCollectorCount { query().apply { selections = listOf("bucket", "count(id)"); groupBy = listOf("bucket") } }
    }

    @Test
    fun `counts preserve validation of invalid order attributes`() {
        resetWork()
        assertFailsWith<AttributeMissingException> {
            manager.countForQuery(query().apply { queryOrders = listOf(QueryOrder("missing", true)) })
        }
        assertEquals(0, context.streamingLookups.get())
    }

    @Test
    fun `unsupported streaming falls back to the original lookup`() {
        context.streamingSupported = false
        resetWork()
        val query = query()
        assertEquals(128L, manager.countForQuery(query))
        assertEquals(128, query.resultsCount)
        assertEquals(1, context.streamingLookups.get())
        assertEquals(1, context.materializedLookups.get())
        assertEquals(128, IndexedCountReads.entities.get())
    }

    @Test
    fun `terminated counts do no index work and diagnostic full scans still load entities`() {
        resetWork()
        assertEquals(0L, manager.countForQuery(query().apply { isTerminated = true }))
        assertIndexWork(expectedLookups = 0)

        val descriptor = context.getDescriptorForEntity(IndexedCountEntity::class.java, "")
        val fullTable = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, context)
        resetWork()
        assertEquals(128L, fullTable.getCountForQuery(query()))
        assertEquals(0, context.streamingLookups.get())
        assertTrue(IndexedCountReads.entities.get() >= 256)
    }

    @Test
    fun `cached results continue to supply counts and uncached counts can be followed by eager queries`() {
        val query = query().apply { cache = true; maxResults = 5 }
        assertIndexedCount(128L, query)
        assertEquals(5, manager.executeQuery<IndexedCountEntity>(query).size)
        resetWork()
        assertEquals(128L, manager.countForQuery(query))
        assertIndexWork(expectedLookups = 0)
    }

    private fun assertIndexedCount(expected: Long, query: Query, expectedLookups: Int = 1) {
        resetWork()
        assertEquals(expected, manager.countForQuery(query))
        assertEquals(expected.toInt(), query.resultsCount)
        assertIndexWork(expectedLookups)
    }

    private fun assertIndexWork(expectedLookups: Int = 1) {
        assertEquals(0, IndexedCountReads.entities.get(), "An indexed count deserialized entities")
        assertEquals(0, context.materializedLookups.get(), "An indexed count materialized postings")
        assertEquals(expectedLookups, context.streamingLookups.get())
    }

    private fun assertCollectorCount(create: () -> Query) {
        val expectedQuery = create()
        manager.executeQuery<Any>(expectedQuery)
        resetWork()
        assertEquals(expectedQuery.resultsCount.toLong(), manager.countForQuery(create()))
        assertEquals(0, context.streamingLookups.get())
    }

    private fun resetWork() {
        IndexedCountReads.entities.set(0)
        context.resetWork()
    }

    private fun query(value: String = "open") = Query(IndexedCountEntity::class.java, "status" eq value)

    private fun partitionQuery(partition: Any = "") =
        Query(IndexedCountPartitionEntity::class.java, "status" eq "open").apply { this.partition = partition }

    private fun seedPartitions() {
        for (partition in listOf(10L, 20L)) {
            for (id in 1L..8L) {
                manager.saveEntity(IndexedCountPartitionEntity().apply {
                    this.id = id
                    tenant = partition
                    status = if (id % 2L == 0L) "open" else "closed"
                })
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stores(): List<Array<StoreType>> =
            listOf(arrayOf(StoreType.IN_MEMORY), arrayOf(StoreType.MEMORY_MAPPED_FILE))
    }
}

private object IndexedCountReads {
    val entities = AtomicInteger()
}

@Entity
class IndexedCountEntity : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Index @Attribute var status: String? = null
    @Index @Attribute var bucket: Int = 0
    @Index @Attribute var recordedAt: Date = Date(0L)
    @Attribute var payload: String = ""

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        IndexedCountReads.entities.incrementAndGet()
    }
}

@Entity
class IndexedCountPartitionEntity : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Partition @Attribute var tenant: Long = 0L
    @Index @Attribute var status: String = ""

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        IndexedCountReads.entities.incrementAndGet()
    }
}
