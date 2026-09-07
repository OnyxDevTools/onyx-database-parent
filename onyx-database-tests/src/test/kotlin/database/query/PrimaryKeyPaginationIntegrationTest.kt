package database.query

import com.onyx.buffer.BufferStream
import com.onyx.diskmap.store.StoreType
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.extension.validate
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.annotations.Relationship
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.RelationshipType
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class PrimaryKeyPaginationIntegrationTest(private val storeType: StoreType) {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var context: DefaultSchemaContext
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-primary-key-pagination-")
        openDatabase()
        for (id in ROWS downTo 1) {
            manager.saveEntity(PrimaryKeyPageRow().apply {
                this.id = id.toLong()
                bucket = id % 4
                payload = "x".repeat(4096)
            })
        }
    }

    private fun openDatabase() {
        val location = directory.toString()
        context = DefaultSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(location, location, context, false).apply {
            storeType = this@PrimaryKeyPaginationIntegrationTest.storeType
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
    fun `ordered pages skip offsets without decoding and retain the total count`() {
        val expected = (1L..ROWS.toLong()).toList()
        for ((offset, limit) in listOf(
            -1 to 20, 0 to 20, 1 to 20, 100 to 20, 700 to 20, ROWS - 7 to 20,
            ROWS to 20, ROWS + 1 to 20, Int.MAX_VALUE to 20, 100 to Int.MAX_VALUE,
        )) {
            val query = query().apply { firstRow = offset; maxResults = limit }
            PrimaryKeyPageReads.rows.set(0)
            val rows = manager.executeQuery<PrimaryKeyPageRow>(query)
            assertEquals(expected.drop(offset.coerceAtLeast(0)).take(limit), rows.map { it.id })
            assertEquals(ROWS, query.resultsCount)
            assertEquals(rows.size, PrimaryKeyPageReads.rows.get())
            assertTrue(rows.all { it.payload.length == 4096 })
        }
    }

    @Test
    fun `lazy pages defer all entity reads and apply the offset once`() {
        for (offset in listOf(0, 1, 700, ROWS - 7, ROWS, Int.MAX_VALUE)) {
            val query = query().apply { firstRow = offset }
            PrimaryKeyPageReads.rows.set(0)
            val rows = manager.executeLazyQuery<PrimaryKeyPageRow>(query)
            val expected = (1L..ROWS.toLong()).drop(offset).take(20)
            assertEquals(expected.size, rows.size)
            assertEquals(ROWS, query.resultsCount)
            assertEquals(0, PrimaryKeyPageReads.rows.get())
            assertEquals(expected, rows.map { it.id })
            assertEquals(expected.size, PrimaryKeyPageReads.rows.get())
        }
    }

    @Test
    fun `cardinality applies to the full table before any page is loaded`() {
        context.maxCardinality = ROWS
        assertEquals(20, manager.executeQuery<PrimaryKeyPageRow>(query()).size)
        context.maxCardinality = ROWS - 1
        for (offset in listOf(0, ROWS + 1)) {
            for (lazy in listOf(false, true)) {
                PrimaryKeyPageReads.rows.set(0)
                val query = query().apply { firstRow = offset }
                assertFailsWith<MaxCardinalityExceededException> {
                    if (lazy) manager.executeLazyQuery<PrimaryKeyPageRow>(query)
                    else manager.executeQuery<PrimaryKeyPageRow>(query)
                }
                assertEquals(0, PrimaryKeyPageReads.rows.get())
            }
        }
    }

    @Test
    fun `empty and terminated queries return empty pages`() {
        PrimaryKeyPageReads.rows.set(0)
        val terminated = query().apply { isTerminated = true }
        assertTrue(manager.executeQuery<PrimaryKeyPageRow>(terminated).isEmpty())
        assertEquals(0, PrimaryKeyPageReads.rows.get())
        manager.executeDelete(Query(PrimaryKeyPageRow::class.java))
        context.maxCardinality = 0
        PrimaryKeyPageReads.rows.set(0)
        val empty = query()
        assertTrue(manager.executeQuery<PrimaryKeyPageRow>(empty).isEmpty())
        assertEquals(0, empty.resultsCount)
        assertEquals(0, PrimaryKeyPageReads.rows.get())
    }

    @Test
    fun `page results agree with forced scans for default and fallback query shapes`() {
        val variants: List<Query.() -> Unit> = listOf(
            {},
            { criteria = QueryCriteria("id", QueryCriteriaOperator.NOT_NULL) },
            { queryOrders = listOf(QueryOrder("id", false)) },
            { queryOrders = listOf(QueryOrder("bucket"), QueryOrder("id")) },
            { criteria = "bucket" eq 2 },
            { criteria = QueryCriteria("id", QueryCriteriaOperator.NOT_NULL).not() },
            { criteria = QueryCriteria("id", QueryCriteriaOperator.NOT_EQUAL).apply { isNot = true } },
            { criteria = QueryCriteria("id", QueryCriteriaOperator.NOT_NULL).and("bucket" eq 2) },
            { maxResults = 0 },
            { maxResults = -1 },
        )
        val descriptor = context.getDescriptorForEntity(PrimaryKeyPageRow::class.java, "")
        val fullTable = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, context)
        for (configure in variants) {
            val ordinary = query().apply { firstRow = 7 }.apply(configure)
            val forced = query().apply { firstRow = 7 }.apply(configure)
            forced.validate(context, descriptor)
            val expected = fullTable.getReferencesForQuery<PrimaryKeyPageRow>(forced)
            val actual = manager.executeQuery<PrimaryKeyPageRow>(ordinary)
            assertEquals(expected.results.map { it.id }, actual.map { it.id })
            assertEquals(forced.resultsCount, ordinary.resultsCount)
        }
    }

    @Test
    fun `cached queries retain the complete reference domain for later pages`() {
        manager.executeDelete(Query(PrimaryKeyPageRow::class.java,
            QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 512L)))
        val query = query().apply { cache = true }
        assertEquals((1L..20L).toList(), manager.executeQuery<PrimaryKeyPageRow>(query).map { it.id })
        val cached = assertNotNull(context.queryCacheInteractor.getCachedQueryResults(query))
        assertEquals(512, cached.references!!.size)
        manager.deleteEntity(PrimaryKeyPageRow().apply { id = 256L })
        query.firstRow = 250
        assertEquals(
            (1L..512L).filter { it != 256L }.drop(250).take(20),
            manager.executeQuery<PrimaryKeyPageRow>(query).map { it.id },
        )
        assertEquals(511, query.resultsCount)
    }

    @Test
    fun `eager relationships are hydrated only for returned parents`() {
        for (id in 1L..64L) {
            manager.saveEntity(PrimaryKeyPageParent().apply {
                this.id = id
                child = PrimaryKeyPageChild().apply { this.id = id; name = "child $id" }
            })
        }
        PrimaryKeyPageReads.parents.set(0)
        PrimaryKeyPageReads.children.set(0)
        val query = Query(PrimaryKeyPageParent::class.java).apply {
            queryOrders = listOf(QueryOrder("id"))
            firstRow = 10
            maxResults = 5
        }
        val rows = manager.executeQuery<PrimaryKeyPageParent>(query)
        assertEquals((11L..15L).toList(), rows.map { it.id })
        assertEquals((11L..15L).map { "child $it" }, rows.map { it.child?.name })
        assertEquals(64, query.resultsCount)
        assertEquals(5, PrimaryKeyPageReads.parents.get())
        assertEquals(5, PrimaryKeyPageReads.children.get())
    }

    @Test
    fun `partition queries retain their existing global ordering and counts`() {
        for (tenant in listOf(10L, 20L)) {
            for (id in 1L..32L) {
                manager.saveEntity(PrimaryKeyPagePartitionRow().apply {
                    this.id = tenant * 100L + id
                    this.tenant = tenant
                })
            }
        }
        for (partition in listOf(QueryPartitionMode.ALL, 10L, 20L, 30L)) {
            val query = Query(PrimaryKeyPagePartitionRow::class.java).apply {
                this.partition = partition
                queryOrders = listOf(QueryOrder("id"))
                firstRow = 7
                maxResults = 10
            }
            val expected = listOf(10L, 20L).filter { partition == QueryPartitionMode.ALL || it == partition }
                .flatMap { tenant -> (1L..32L).map { tenant * 100L + it } }
            assertEquals(expected.drop(7).take(10), manager.executeQuery<PrimaryKeyPagePartitionRow>(query).map { it.id })
            assertEquals(expected.size, query.resultsCount)
        }
    }

    @Test
    fun `pages survive tree merges new extreme keys and reopening`() {
        for (id in 500L..1500L) manager.deleteEntity(PrimaryKeyPageRow().apply { this.id = id })
        for (id in listOf(Long.MIN_VALUE, -1L, Long.MAX_VALUE)) {
            manager.saveEntity(PrimaryKeyPageRow().apply { this.id = id; payload = "added" })
        }
        if (storeType == StoreType.MEMORY_MAPPED_FILE) {
            factory.close()
            openDatabase()
        }
        val expected = (listOf(Long.MIN_VALUE, -1L) + (1L..ROWS.toLong()).filter { it !in 500L..1500L } + Long.MAX_VALUE)
        for (offset in listOf(0, 490, expected.size - 5)) {
            val query = query().apply { firstRow = offset }
            PrimaryKeyPageReads.rows.set(0)
            val rows = manager.executeQuery<PrimaryKeyPageRow>(query)
            assertEquals(expected.drop(offset).take(20), rows.map { it.id })
            assertEquals(expected.size, query.resultsCount)
            assertEquals(rows.size, PrimaryKeyPageReads.rows.get())
        }
    }

    @Test
    fun `string primary keys use lexical order`() {
        val keys = listOf("z", "2", "10", "A", "a", "é", "01", "aa")
        keys.forEach { manager.saveEntity(PrimaryKeyPageStringRow().apply { id = it }) }
        val query = Query(PrimaryKeyPageStringRow::class.java).apply {
            queryOrders = listOf(QueryOrder("id"))
            firstRow = 1
            maxResults = 4
        }
        assertEquals(keys.sorted().drop(1).take(4), manager.executeQuery<PrimaryKeyPageStringRow>(query).map { it.id })
        assertEquals(keys.size, query.resultsCount)
    }

    @Test
    fun `custom identifier getters retain their computed sort order`() {
        (1L..64L).forEach { manager.saveEntity(PrimaryKeyPageComputedRow().apply { id = it }) }
        val query = Query(PrimaryKeyPageComputedRow::class.java).apply {
            queryOrders = listOf(QueryOrder("id"))
            firstRow = 1
            maxResults = 3
        }
        assertEquals(listOf(-63L, -62L, -61L), manager.executeQuery<PrimaryKeyPageComputedRow>(query).map { it.id })
        assertEquals(64, query.resultsCount)
    }

    private fun query() = Query(PrimaryKeyPageRow::class.java).apply {
        queryOrders = listOf(QueryOrder("id"))
        maxResults = 20
    }

    companion object {
        private const val ROWS = 2048

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stores(): List<Array<StoreType>> =
            listOf(arrayOf(StoreType.IN_MEMORY), arrayOf(StoreType.MEMORY_MAPPED_FILE))
    }
}

private object PrimaryKeyPageReads {
    val rows = AtomicInteger()
    val parents = AtomicInteger()
    val children = AtomicInteger()
}

@Entity
class PrimaryKeyPageRow : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Attribute var bucket: Int = 0
    @Attribute var payload: String = ""

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        PrimaryKeyPageReads.rows.incrementAndGet()
    }
}

@Entity
class PrimaryKeyPageParent : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = PrimaryKeyPageChild::class,
        cascadePolicy = CascadePolicy.ALL, fetchPolicy = FetchPolicy.EAGER)
    var child: PrimaryKeyPageChild? = null

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        PrimaryKeyPageReads.parents.incrementAndGet()
    }
}

@Entity
class PrimaryKeyPageChild : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Attribute var name: String = ""

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        PrimaryKeyPageReads.children.incrementAndGet()
    }
}

@Entity
class PrimaryKeyPagePartitionRow : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Partition @Attribute var tenant: Long = 0L
}

@Entity
class PrimaryKeyPageStringRow : ManagedEntity() {
    @Identifier @Attribute var id: String = ""
}

@Entity
class PrimaryKeyPageComputedRow : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
        get() = -field
}
