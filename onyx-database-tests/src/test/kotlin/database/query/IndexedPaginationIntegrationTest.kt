package database.query

import com.onyx.buffer.BufferStream
import com.onyx.diskmap.store.StoreType
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.annotations.Relationship
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.RelationshipType
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryOrder
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.query.eq
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class IndexedPaginationIntegrationTest(private val storeType: StoreType) {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var context: DefaultSchemaContext
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-indexed-pagination-")
        openDatabase()
        val payload = "x".repeat(4096)
        for (id in 1L..MATCHES + 32L) {
            manager.saveEntity(IndexedPageEntity().apply {
                this.id = id
                status = if (id <= MATCHES) "open" else "closed"
                bucket = (id % 4L).toInt()
                this.payload = payload
            })
        }
    }

    private fun openDatabase() {
        val location = directory.toString()
        context = DefaultSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(location, location, context, false).apply {
            storeType = this@IndexedPaginationIntegrationTest.storeType
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
    fun `eager pages load only returned entities and preserve index arrival order and total count`() {
        val all = manager.executeQuery<IndexedPageEntity>(query().apply { maxResults = -1 }).map { it.id }
        for ((offset, limit) in listOf(
            0 to 20, 1 to 20, 100 to 20, MATCHES - 7 to 20,
            MATCHES to 20, MATCHES + 1 to 20, Int.MAX_VALUE to 20, 100 to Int.MAX_VALUE,
        )) {
            val query = query().apply { firstRow = offset; maxResults = limit }
            IndexedPageReads.entities.set(0)
            val rows = manager.executeQuery<IndexedPageEntity>(query)
            assertEquals(all.drop(offset).take(limit), rows.map { it.id })
            assertEquals(MATCHES, query.resultsCount)
            assertEquals(rows.size, IndexedPageReads.entities.get())
            assertTrue(rows.all { it.payload.length == 4096 })
        }
    }

    @Test
    fun `lazy pages and unbounded lazy lists do not load entities before access`() {
        val all = manager.executeQuery<IndexedPageEntity>(query().apply { maxResults = -1 }).map { it.id }
        for (limit in listOf(20, -1, 0, Int.MAX_VALUE)) {
            val query = query().apply { firstRow = 10; maxResults = limit }
            IndexedPageReads.entities.set(0)
            val rows = manager.executeLazyQuery<IndexedPageEntity>(query)
            val expected = all.drop(10).let { if (limit > 0) it.take(limit) else it }
            assertEquals(expected.size, rows.size)
            assertEquals(MATCHES, query.resultsCount)
            assertEquals(0, IndexedPageReads.entities.get())
            assertEquals(expected.first(), rows[0].id)
            assertEquals(1, IndexedPageReads.entities.get())
            assertEquals(expected, rows.map { it.id })
        }
    }

    @Test
    fun `cached pages retain all references and only load the newly requested page`() {
        val all = manager.executeQuery<IndexedPageEntity>(query().apply { maxResults = -1 }).map { it.id }
        val query = query().apply { cache = true }
        IndexedPageReads.entities.set(0)
        assertEquals(all.take(20), manager.executeQuery<IndexedPageEntity>(query).map { it.id })
        assertEquals(20, IndexedPageReads.entities.get())
        val cached = assertNotNull(context.queryCacheInteractor.getCachedQueryResults(query))
        assertEquals(MATCHES, cached.references!!.size)

        for (offset in listOf(1, 100, MATCHES - 7, MATCHES + 1)) {
            query.firstRow = offset
            IndexedPageReads.entities.set(0)
            val rows = manager.executeQuery<IndexedPageEntity>(query)
            assertEquals(all.drop(offset).take(20), rows.map { it.id })
            assertEquals(rows.size, IndexedPageReads.entities.get())
            assertEquals(MATCHES, query.resultsCount)
        }

        query.firstRow = 100
        IndexedPageReads.entities.set(0)
        val lazy = manager.executeLazyQuery<IndexedPageEntity>(query)
        assertEquals(20, lazy.size)
        assertEquals(0, IndexedPageReads.entities.get())
        assertEquals(all.drop(100).take(20), lazy.map { it.id })
        assertEquals(20, IndexedPageReads.entities.get())
        assertEquals(MATCHES, query.resultsCount)
    }

    @Test
    fun `cached references observe changes outside the original page`() {
        val query = query().apply { cache = true }
        val page = manager.executeQuery<IndexedPageEntity>(query)
        val cached = assertNotNull(context.queryCacheInteractor.getCachedQueryResults(query))
        val outsidePage = (1L..MATCHES).first { id -> page.none { it.id == id } }
        val row = manager.executeQuery<IndexedPageEntity>(Query(IndexedPageEntity::class.java, "id" eq outsidePage)).single()
        row.status = "closed"
        manager.saveEntity(row)
        assertEquals(MATCHES - 1, cached.references!!.size)
        row.status = "open"
        row.payload = "changed"
        manager.saveEntity(row)
        assertEquals(MATCHES, cached.references!!.size)
        manager.deleteEntity(row)
        assertEquals(MATCHES - 1, cached.references!!.size)

        manager.saveEntity(IndexedPageEntity().apply { id = 10000L; status = "open"; payload = "new" })
        assertEquals(MATCHES, cached.references!!.size)
        query.firstRow = MATCHES - 1
        query.maxResults = 1
        IndexedPageReads.entities.set(0)
        val added = manager.executeQuery<IndexedPageEntity>(query).single()
        assertEquals(10000L, added.id)
        assertEquals("new", added.payload)
        assertEquals(1, IndexedPageReads.entities.get())
        assertEquals(MATCHES, query.resultsCount)
    }

    @Test
    fun `cardinality limits the full domain on cold and cached pages before loading`() {
        val query = query().apply { cache = true }
        context.maxCardinality = MATCHES
        assertEquals(20, manager.executeQuery<IndexedPageEntity>(query).size)
        val cached = assertNotNull(context.queryCacheInteractor.getCachedQueryResults(query))
        assertEquals(MATCHES, cached.references!!.size)
        context.maxCardinality = MATCHES - 1
        for (cacheEnabled in listOf(false, true)) {
            for (lazy in listOf(false, true)) {
                val limited = query().apply { cache = cacheEnabled }
                IndexedPageReads.entities.set(0)
                assertFailsWith<MaxCardinalityExceededException> {
                    if (lazy) manager.executeLazyQuery<IndexedPageEntity>(limited)
                    else manager.executeQuery<IndexedPageEntity>(limited)
                }
                assertEquals(0, IndexedPageReads.entities.get())
            }
        }
        context.maxCardinality = 0
        IndexedPageReads.entities.set(0)
        val missing = query("missing")
        assertTrue(manager.executeQuery<IndexedPageEntity>(missing).isEmpty())
        assertEquals(0, missing.resultsCount)
        assertEquals(0, IndexedPageReads.entities.get())
    }

    @Test
    fun `concrete and all partition queries load only one global page`() {
        seedPartitions()
        for (partition in listOf(10L, 20L, QueryPartitionMode.ALL, "", 30L)) {
            val expectedCount = when (partition) { 10L, 20L -> 64; 30L -> 0; else -> 128 }
            val query = partitionQuery(partition).apply { firstRow = 3; cache = true }
            IndexedPageReads.entities.set(0)
            val rows = manager.executeQuery<IndexedPagePartitionEntity>(query)
            assertEquals(if (expectedCount == 0) 0 else 20, rows.size)
            assertEquals(expectedCount, query.resultsCount)
            assertEquals(rows.size, IndexedPageReads.entities.get())
            assertEquals(rows.size, rows.map { it.tenant to it.id }.distinct().size)
            assertTrue(rows.all { it.status == "open" && (partition !is Long || it.tenant == partition) })
            val cached = assertNotNull(context.queryCacheInteractor.getCachedQueryResults(query))
            assertEquals(expectedCount, cached.references!!.size)
            IndexedPageReads.entities.set(0)
            val lazy = manager.executeLazyQuery<IndexedPagePartitionEntity>(query)
            assertEquals(0, IndexedPageReads.entities.get())
            assertEquals(rows.map { it.tenant to it.id }, lazy.map { it.tenant to it.id })
            assertEquals(rows.size, IndexedPageReads.entities.get())
        }
    }

    @Test
    fun `concurrent partition admission enforces global cardinality`() {
        seedPartitions()
        context.maxCardinality = 128
        assertEquals(20, manager.executeQuery<IndexedPagePartitionEntity>(partitionQuery()).size)
        context.maxCardinality = 64
        assertEquals(20, manager.executeQuery<IndexedPagePartitionEntity>(partitionQuery(10L)).size)
        IndexedPageReads.entities.set(0)
        val failure = assertFails {
            manager.executeQuery<IndexedPagePartitionEntity>(partitionQuery())
        }
        assertTrue(generateSequence(failure) { it.cause }.any { it is MaxCardinalityExceededException })
        assertEquals(0, IndexedPageReads.entities.get())
    }

    @Test
    fun `ordered projections aggregations and residual filters keep their entity evaluation`() {
        val queries = listOf<() -> Query>(
            { query().apply { queryOrders = listOf(QueryOrder("id", false)) } },
            { query().apply { selections = listOf("id") } },
            { query().apply { selections = listOf("bucket"); isDistinct = true } },
            { query().apply { selections = listOf("bucket", "count(id)"); groupBy = listOf("bucket") } },
            { query().apply { selections = listOf("count(id)") } },
            { query().apply { criteria = ("payload" eq "x".repeat(4096)).and("status" eq "open") } },
        )
        for (create in queries) {
            val query = create()
            IndexedPageReads.entities.set(0)
            val rows = manager.executeQuery<Any>(query)
            assertTrue(rows.isNotEmpty())
            assertTrue(IndexedPageReads.entities.get() >= MATCHES)
        }
        val ordered = query().apply { queryOrders = listOf(QueryOrder("id", false)) }
        assertEquals((MATCHES.toLong() downTo MATCHES - 19L).toList(), manager.executeQuery<IndexedPageEntity>(ordered).map { it.id })
    }

    @Test
    fun `update and delete queries still mutate only their requested number of records`() {
        val update = query().apply { updates = listOf(AttributeUpdate("status", "updated")) }
        assertEquals(20, manager.executeUpdate(update))
        assertEquals(20L, manager.countForQuery(query("updated")))
        assertEquals(20, manager.executeDelete(query("updated")))
        assertEquals(0L, manager.countForQuery(query("updated")))
        assertEquals(MATCHES - 20L, manager.countForQuery(query()))
    }

    @Test
    fun `page entities retain eager relationships`() {
        for (id in 1L..64L) {
            manager.saveEntity(IndexedPageParent().apply {
                this.id = id
                status = "open"
                child = IndexedPageChild().apply { this.id = id; name = "child $id" }
            })
        }
        val query = Query(IndexedPageParent::class.java, "status" eq "open").apply { maxResults = 3 }
        IndexedPageReads.entities.set(0)
        val parents = manager.executeQuery<IndexedPageParent>(query)
        assertEquals(3, parents.size)
        assertEquals(3, IndexedPageReads.entities.get())
        assertEquals(64, query.resultsCount)
        parents.forEach { assertEquals("child ${it.id}", assertNotNull(it.child).name) }
    }

    @Test
    fun `reopened indexes retain page order and deferred reads`() {
        if (storeType != StoreType.MEMORY_MAPPED_FILE) return
        val expected = manager.executeQuery<IndexedPageEntity>(query()).map { it.id }
        factory.close()
        openDatabase()
        IndexedPageReads.entities.set(0)
        val query = query()
        assertEquals(expected, manager.executeQuery<IndexedPageEntity>(query).map { it.id })
        assertEquals(20, IndexedPageReads.entities.get())
        assertEquals(MATCHES, query.resultsCount)
    }

    private fun query(status: String = "open") = Query(IndexedPageEntity::class.java, "status" eq status).apply {
        maxResults = 20
    }

    private fun partitionQuery(partition: Any = QueryPartitionMode.ALL) =
        Query(IndexedPagePartitionEntity::class.java, "status" eq "open").apply {
            this.partition = partition
            maxResults = 20
        }

    private fun seedPartitions() {
        for (tenant in listOf(10L, 20L)) {
            for (id in 1L..64L) {
                manager.saveEntity(IndexedPagePartitionEntity().apply {
                    this.id = id
                    this.tenant = tenant
                    status = "open"
                })
            }
        }
    }

    companion object {
        private const val MATCHES = 2048

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stores(): List<Array<StoreType>> =
            listOf(arrayOf(StoreType.IN_MEMORY), arrayOf(StoreType.MEMORY_MAPPED_FILE))
    }
}

private object IndexedPageReads {
    val entities = AtomicInteger()
}

@Entity
class IndexedPageEntity : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Index @Attribute var status: String = ""
    @Index @Attribute var bucket: Int = 0
    @Attribute var payload: String = ""

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        IndexedPageReads.entities.incrementAndGet()
    }
}

@Entity
class IndexedPagePartitionEntity : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Partition @Attribute var tenant: Long = 0L
    @Index @Attribute var status: String = ""

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        IndexedPageReads.entities.incrementAndGet()
    }
}

@Entity
class IndexedPageParent : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Index @Attribute var status: String = ""
    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = IndexedPageChild::class,
        cascadePolicy = CascadePolicy.ALL, fetchPolicy = FetchPolicy.EAGER)
    var child: IndexedPageChild? = null

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        IndexedPageReads.entities.incrementAndGet()
    }
}

@Entity
class IndexedPageChild : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Attribute var name: String = ""
}
