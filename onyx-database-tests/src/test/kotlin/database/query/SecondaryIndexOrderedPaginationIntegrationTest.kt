package database.query

import com.onyx.buffer.BufferStream
import com.onyx.diskmap.store.StoreType
import com.onyx.exception.AttributeMissingException
import com.onyx.exception.MaxCardinalityExceededException
import com.onyx.extension.validate
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.RelationshipType
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryOrder
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.QueryCriteriaOperator.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

@RunWith(Parameterized::class)
class SecondaryIndexOrderedPaginationIntegrationTest(private val storeType: StoreType) {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var context: OrderedIndexPageSchemaContext
    private lateinit var manager: PersistenceManager
    private val idsByRank = (1L..ROWS.toLong()).associateBy { it * 683L % ROWS }

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-secondary-ordered-page-")
        openDatabase()
        val payload = "x".repeat(4096)
        idsByRank.forEach { (rank, id) ->
            manager.saveEntity(OrderedIndexPageRow().apply {
                this.id = id
                this.rank = rank
                this.payload = payload
            })
        }
        resetWork()
    }

    private fun openDatabase() {
        val location = directory.toString()
        context = OrderedIndexPageSchemaContext(location)
        factory = EmbeddedPersistenceManagerFactory(location, location, context, false).apply {
            storeType = this@SecondaryIndexOrderedPaginationIntegrationTest.storeType
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
    fun `ordered range pages decode only returned rows and preserve full counts`() {
        val expected = (LOWER until ROWS.toLong()).map(idsByRank::getValue)
        for ((offset, limit) in listOf(
            -1 to 20, 0 to 20, 1 to 20, 700 to 20, expected.size - 7 to 20,
            expected.size to 20, expected.size + 1 to 20, Int.MAX_VALUE to 20, 100 to Int.MAX_VALUE,
        )) {
            val query = query().apply { firstRow = offset; maxResults = limit }
            resetWork()
            val rows = manager.executeQuery<OrderedIndexPageRow>(query)
            assertEquals(expected.drop(offset.coerceAtLeast(0)).take(limit), rows.map { it.id })
            assertEquals(expected.size, query.resultsCount)
            assertEquals(rows.size, OrderedIndexPageReads.rows.get())
            assertTrue(rows.all { it.payload.length == 4096 })
            assertEquals(1, context.orderedLookups.get())
            assertEquals(expected.size, context.postingVisits.get())
        }
    }

    @Test
    fun `all range bounds and fused two-sided ranges match full scans`() {
        val ranges: List<() -> QueryCriteria> = listOf(
            { QueryCriteria("rank", GREATER_THAN, 500L) },
            { QueryCriteria("rank", GREATER_THAN_EQUAL, 500L) },
            { QueryCriteria("rank", LESS_THAN, 1500L) },
            { QueryCriteria("rank", LESS_THAN_EQUAL, 1500L) },
            { QueryCriteria("rank", GREATER_THAN_EQUAL, 500L).not() },
            { QueryCriteria("rank", BETWEEN, 500L to 1500L) },
            { QueryCriteria("rank", BETWEEN, 500L to 500L) },
            { QueryCriteria("rank", BETWEEN, 1500L to 500L) },
            { QueryCriteria("rank", GREATER_THAN, Long.MAX_VALUE) },
            { QueryCriteria("rank", LESS_THAN, Long.MIN_VALUE) },
        ) + listOf(GREATER_THAN, GREATER_THAN_EQUAL).flatMap { lower ->
            listOf(LESS_THAN, LESS_THAN_EQUAL).flatMap { upper ->
                listOf<() -> QueryCriteria>(
                    { QueryCriteria("rank", lower, 500L).and(QueryCriteria("rank", upper, 1500L)) },
                    { QueryCriteria("rank", upper, 1500L).and(QueryCriteria("rank", lower, 500L)) },
                )
            }
        }
        for (range in ranges) {
            assertMatchesOrdinary({ query(range()).apply { firstRow = 7 } }, fast = true, compareWithTable = true)
        }
    }

    @Test
    fun `lazy ordered pages retain only page references and defer decoding`() {
        val expected = (LOWER until ROWS.toLong()).map(idsByRank::getValue)
        for (offset in listOf(0, 700, expected.size - 7, expected.size, Int.MAX_VALUE)) {
            val query = query().apply { firstRow = offset }
            resetWork()
            val rows = manager.executeLazyQuery<OrderedIndexPageRow>(query)
            assertEquals(expected.drop(offset).take(20).size, rows.size)
            assertEquals(expected.size, query.resultsCount)
            assertEquals(0, OrderedIndexPageReads.rows.get())
            assertEquals(1, context.orderedLookups.get())
            assertEquals(expected.drop(offset).take(20), rows.map { it.id })
            assertEquals(rows.size, OrderedIndexPageReads.rows.get())
        }
    }

    @Test
    fun `ties inside the page or across either boundary retain the original stable ordering`() {
        val ranks = listOf(0L, 10L, 10L, 20L, 30L, 40L, 50L, 50L, 50L)
        ranks.forEachIndexed { i, rank ->
            manager.saveEntity(OrderedIndexPageTieRow().apply { id = (i + 1).toLong(); this.rank = rank })
        }
        for ((offset, limit, tied) in listOf(
            Triple(0, 5, true), Triple(2, 1, true), Triple(0, 2, true),
            Triple(3, 3, false), Triple(9, 2, false),
        )) {
            val makeQuery = {
                Query(OrderedIndexPageTieRow::class.java, QueryCriteria("rank", GREATER_THAN_EQUAL, 0L), QueryOrder("rank"))
                    .apply { firstRow = offset; maxResults = limit }
            }
            context.orderedRangesSupported = false
            val expectedQuery = makeQuery()
            val expected = manager.executeQuery<OrderedIndexPageTieRow>(expectedQuery).map { it.id }
            context.orderedRangesSupported = true
            resetWork()
            val query = makeQuery()
            assertEquals(expected, manager.executeQuery<OrderedIndexPageTieRow>(query).map { it.id })
            assertEquals(ranks.size, query.resultsCount)
            assertEquals(if (tied) ranks.size else expected.size, OrderedIndexPageReads.rows.get())
            assertEquals(1, context.orderedLookups.get())
        }
    }

    @Test
    fun `ties across posting pages are detected without changing page membership`() {
        for (id in 1L..1024L) {
            manager.saveEntity(OrderedIndexPageTieRow().apply { this.id = id; rank = if (id <= 900L) 0L else id })
        }
        for (offset in listOf(700, 901)) {
            val makeQuery = {
                Query(OrderedIndexPageTieRow::class.java, QueryCriteria("rank", GREATER_THAN_EQUAL, 0L), QueryOrder("rank"))
                    .apply { firstRow = offset; maxResults = 20 }
            }
            context.orderedRangesSupported = false
            val expected = manager.executeQuery<OrderedIndexPageTieRow>(makeQuery()).map { it.id }
            context.orderedRangesSupported = true
            resetWork()
            val query = makeQuery()
            assertEquals(expected, manager.executeQuery<OrderedIndexPageTieRow>(query).map { it.id })
            assertEquals(1024, query.resultsCount)
            assertEquals(if (offset == 700) 1024 else 20, OrderedIndexPageReads.rows.get())
        }
    }

    @Test
    fun `cardinality limits the full matching domain before any entity is decoded`() {
        val matches = ROWS - LOWER.toInt()
        context.maxCardinality = matches
        assertEquals(20, manager.executeQuery<OrderedIndexPageRow>(query()).size)
        for (cardinality in listOf(0, 7, matches - 1)) {
            context.maxCardinality = cardinality
            for (offset in listOf(0, matches + 1)) {
                for (lazy in listOf(false, true)) {
                    resetWork()
                    val query = query().apply { firstRow = offset }
                    assertFailsWith<MaxCardinalityExceededException> {
                        if (lazy) manager.executeLazyQuery<OrderedIndexPageRow>(query)
                        else manager.executeQuery<OrderedIndexPageRow>(query)
                    }
                    assertEquals(cardinality + 1, context.postingVisits.get())
                    assertEquals(0, OrderedIndexPageReads.rows.get())
                }
            }
        }
        context.maxCardinality = 0
        val empty = query(QueryCriteria("rank", GREATER_THAN, Long.MAX_VALUE))
        assertTrue(manager.executeQuery<OrderedIndexPageRow>(empty).isEmpty())
        assertEquals(0, empty.resultsCount)
    }

    @Test
    fun `termination before or during ordered traversal returns no partial page`() {
        val query = query().apply { isTerminated = true }
        assertTrue(manager.executeQuery<OrderedIndexPageRow>(query).isEmpty())
        assertEquals(0, OrderedIndexPageReads.rows.get())
        query.isTerminated = false
        context.onPosting = Runnable { if (context.postingVisits.get() == 7) query.isTerminated = true }
        assertTrue(manager.executeQuery<OrderedIndexPageRow>(query).isEmpty())
        assertEquals(0, query.resultsCount)
        assertEquals(7, context.postingVisits.get())
        assertEquals(0, OrderedIndexPageReads.rows.get())
    }

    @Test
    fun `unsupported custom index traversal discards its partial page and retries`() {
        for (partial in listOf(false, true)) {
            context.orderedRangesSupported = partial
            context.unsupportedAfterVisits = if (partial) 7 else 0
            resetWork()
            val query = query()
            val rows = manager.executeQuery<OrderedIndexPageRow>(query)
            assertEquals((LOWER until LOWER + 20).map(idsByRank::getValue), rows.map { it.id })
            assertEquals(ROWS - LOWER.toInt(), query.resultsCount)
            assertEquals(ROWS - LOWER.toInt(), OrderedIndexPageReads.rows.get())
        }
    }

    @Test
    fun `fallback shapes keep their ordinary ordering counts and projections`() {
        val variants: List<Query.() -> Unit> = listOf(
            { queryOrders = listOf(QueryOrder("rank", false)) },
            { queryOrders = listOf(QueryOrder("id")) },
            { queryOrders = listOf(QueryOrder("rank"), QueryOrder("id")) },
            { queryOrders = null },
            { maxResults = 0 },
            { maxResults = -1 },
            { criteria = QueryCriteria("rank", GREATER_THAN_EQUAL, LOWER.toInt()) },
            { criteria = QueryCriteria("rank", EQUAL, LOWER) },
            { criteria = QueryCriteria("rank", GREATER_THAN_EQUAL, listOf(LOWER, LOWER + 10)) },
            { criteria = QueryCriteria("rank", GREATER_THAN_EQUAL, null) },
            { criteria = QueryCriteria("rank", GREATER_THAN_EQUAL, LOWER).apply { isNot = true } },
            { criteria = QueryCriteria("rank", GREATER_THAN_EQUAL, LOWER).and("id" eq 100L) },
            { criteria = QueryCriteria("rank", GREATER_THAN_EQUAL, ROWS - 256L).or("id" eq 100L) },
            { selections = listOf("id") },
            { selections = listOf("rank"); isDistinct = true },
            { selections = listOf("count(id)"); groupBy = listOf("rank") },
        )
        for (variant in variants) assertMatchesOrdinary({ query().apply { firstRow = 3 }.apply(variant) }, fast = false)
        assertFailsWith<AttributeMissingException> {
            manager.executeQuery<OrderedIndexPageRow>(query().apply { queryOrders = listOf(QueryOrder("missing")) })
        }
    }

    @Test
    fun `cached pages retain all matching references for later offsets and deletions`() {
        val query = query(QueryCriteria("rank", BETWEEN, 100L to 355L)).apply { cache = true }
        val expected = (100L..355L).map(idsByRank::getValue)
        assertEquals(expected.take(20), manager.executeQuery<OrderedIndexPageRow>(query).map { it.id })
        assertEquals(0, context.orderedLookups.get())
        val cached = assertNotNull(context.queryCacheInteractor.getCachedQueryResults(query))
        assertEquals(256, cached.references!!.size)
        val deleted = expected[100]
        manager.deleteEntity(OrderedIndexPageRow().apply { id = deleted })
        query.firstRow = 95
        assertEquals(expected.filter { it != deleted }.drop(95).take(20), manager.executeQuery<OrderedIndexPageRow>(query).map { it.id })
        assertEquals(255, query.resultsCount)
    }

    @Test
    fun `stored string date and floating point keys retain their native order and omit null postings`() {
        val labels = listOf("z", "2", "10", "A", "a", "é", "01", "aa")
        val numbers = listOf(Double.NEGATIVE_INFINITY, -2.5, -0.0, 0.0, 2.5, Double.POSITIVE_INFINITY, Double.NaN, 10.0)
        labels.forEachIndexed { i, label ->
            manager.saveEntity(OrderedIndexPageScalarRow().apply {
                id = i.toLong(); text = label; time = Date((i * 1000).toLong()); number = numbers[i]
            })
        }
        manager.saveEntity(OrderedIndexPageScalarRow().apply { id = 100L })
        for ((attribute, criteria) in listOf(
            "text" to QueryCriteria("text", GREATER_THAN_EQUAL, ""),
            "text" to QueryCriteria("text", LESS_THAN, "a"),
            "time" to QueryCriteria("time", BETWEEN, Date(1000L) to Date(6000L)),
            "number" to QueryCriteria("number", GREATER_THAN_EQUAL, Double.NEGATIVE_INFINITY),
            "number" to QueryCriteria("number", LESS_THAN_EQUAL, 0.0),
        )) {
            val makeQuery = {
                Query(OrderedIndexPageScalarRow::class.java, criteria, QueryOrder(attribute))
                    .apply { firstRow = 1; maxResults = 4 }
            }
            context.orderedRangesSupported = false
            val expectedQuery = makeQuery()
            val expected = manager.executeQuery<OrderedIndexPageScalarRow>(expectedQuery).map { it.id }
            context.orderedRangesSupported = true
            resetWork()
            val query = makeQuery()
            val actual = manager.executeQuery<OrderedIndexPageScalarRow>(query)
            assertEquals(expected, actual.map { it.id })
            assertEquals(expectedQuery.resultsCount, query.resultsCount)
            assertEquals(actual.size, OrderedIndexPageReads.rows.get())
            assertEquals(1, context.orderedLookups.get())
        }
    }

    @Test
    fun `custom sort getters use the existing comparator`() {
        for (id in 1L..64L) manager.saveEntity(OrderedIndexPageComputedRow().apply { this.id = id; rank = id })
        val makeQuery = {
            Query(OrderedIndexPageComputedRow::class.java, QueryCriteria("rank", GREATER_THAN_EQUAL, 0L), QueryOrder("rank"))
                .apply { firstRow = 1; maxResults = 3 }
        }
        context.orderedRangesSupported = false
        val expected = manager.executeQuery<OrderedIndexPageComputedRow>(makeQuery()).map { it.rank }
        context.orderedRangesSupported = true
        resetWork()
        assertEquals(expected, manager.executeQuery<OrderedIndexPageComputedRow>(makeQuery()).map { it.rank })
        assertEquals(0, context.orderedLookups.get())
        assertEquals(64, OrderedIndexPageReads.rows.get())
    }

    @Test
    fun `eager relationships are hydrated only for the requested parents`() {
        for (id in 1L..64L) manager.saveEntity(OrderedIndexPageParent().apply {
            this.id = id; rank = 65L - id
            child = OrderedIndexPageChild().apply { this.id = id; name = "child $id" }
        })
        resetWork()
        OrderedIndexPageReads.children.set(0)
        val query = Query(OrderedIndexPageParent::class.java, QueryCriteria("rank", GREATER_THAN, 0L), QueryOrder("rank"))
            .apply { firstRow = 10; maxResults = 5 }
        val rows = manager.executeQuery<OrderedIndexPageParent>(query)
        assertEquals((54L downTo 50L).toList(), rows.map { it.id })
        assertEquals((54L downTo 50L).map { "child $it" }, rows.map { it.child?.name })
        assertEquals(64, query.resultsCount)
        assertEquals(5, OrderedIndexPageReads.rows.get())
        assertEquals(5, OrderedIndexPageReads.children.get())
    }

    @Test
    fun `partitioned ranges keep their global ordering and total counts`() {
        for (tenant in listOf(10L, 20L)) for (id in 1L..32L) {
            manager.saveEntity(OrderedIndexPagePartitionRow().apply { this.id = id; this.tenant = tenant; rank = tenant + id * 100L })
        }
        for (partition in listOf(QueryPartitionMode.ALL, 10L, 20L, 30L)) {
            resetWork()
            val query = Query(OrderedIndexPagePartitionRow::class.java, QueryCriteria("rank", GREATER_THAN, 0L), QueryOrder("rank"))
                .apply { this.partition = partition; firstRow = 7; maxResults = 10 }
            val expected = listOf(10L, 20L).filter { partition == QueryPartitionMode.ALL || it == partition }
                .flatMap { tenant -> (1L..32L).map { tenant + it * 100L } }.sorted()
            assertEquals(expected.drop(7).take(10), manager.executeQuery<OrderedIndexPagePartitionRow>(query).map { it.rank })
            assertEquals(expected.size, query.resultsCount)
            assertEquals(0, context.orderedLookups.get())
        }
    }

    @Test
    fun `pages reflect index updates deletes payload relocation and reopening`() {
        val changed = manager.findById<OrderedIndexPageRow>(OrderedIndexPageRow::class.java, idsByRank.getValue(700L))!!
        changed.rank = 3000L
        changed.payload = "y".repeat(16384)
        manager.saveEntity(changed)
        for (rank in 900L..1200L) manager.deleteEntity(OrderedIndexPageRow().apply { id = idsByRank.getValue(rank) })
        manager.saveEntity(OrderedIndexPageRow().apply { id = 3000L; rank = Long.MAX_VALUE; payload = "new" })
        if (storeType == StoreType.MEMORY_MAPPED_FILE) { factory.close(); openDatabase() }
        assertMatchesOrdinary({ query().apply { firstRow = 600 } }, fast = true)
        assertMatchesOrdinary({ query(QueryCriteria("rank", GREATER_THAN_EQUAL, 3000L)) }, fast = true)
    }

    private fun assertMatchesOrdinary(makeQuery: () -> Query, fast: Boolean, compareWithTable: Boolean = false) {
        context.orderedRangesSupported = false
        val expectedQuery = makeQuery()
        val expected = if (compareWithTable) {
            val descriptor = context.getDescriptorForEntity(OrderedIndexPageRow::class.java, "")
            expectedQuery.validate(context, descriptor)
            DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, context)
                .getReferencesForQuery<Any>(expectedQuery).results.map(::comparableResult)
        } else manager.executeQuery<Any>(expectedQuery).map(::comparableResult)
        context.orderedRangesSupported = true
        resetWork()
        val query = makeQuery()
        val actual = manager.executeQuery<Any>(query)
        assertEquals(expected, actual.map(::comparableResult), query.toString())
        assertEquals(expectedQuery.resultsCount, query.resultsCount)
        if (fast) {
            assertEquals(1, context.orderedLookups.get())
            assertEquals(actual.size, OrderedIndexPageReads.rows.get())
        } else {
            assertEquals(0, context.orderedLookups.get(),
                "Unexpected ordered traversal for ${query.criteria?.operator} ${query.criteria?.value} " +
                    "(${query.criteria?.value?.javaClass}), orders=${query.queryOrders}, selections=${query.selections}, groups=${query.groupBy}")
        }
    }

    private fun comparableResult(value: Any): Any =
        if (value is OrderedIndexPageRow) Triple(value.id, value.rank, value.payload) else value

    private fun query(criteria: QueryCriteria = QueryCriteria("rank", GREATER_THAN_EQUAL, LOWER)) =
        Query(OrderedIndexPageRow::class.java, criteria, QueryOrder("rank")).apply { maxResults = 20 }

    private fun resetWork() { context.resetWork(); OrderedIndexPageReads.rows.set(0) }

    companion object {
        private const val ROWS = 2048
        private const val LOWER = 256L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stores(): List<Array<StoreType>> = listOf(arrayOf(StoreType.IN_MEMORY), arrayOf(StoreType.MEMORY_MAPPED_FILE))
    }
}

private object OrderedIndexPageReads {
    val rows = AtomicInteger()
    val children = AtomicInteger()
}

open class OrderedIndexPageReadRow : ManagedEntity() {
    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        OrderedIndexPageReads.rows.incrementAndGet()
    }
}

@Entity
class OrderedIndexPageRow : OrderedIndexPageReadRow() {
    @Identifier @Attribute var id: Long = 0L
    @Index @Attribute var rank: Long? = null
    @Attribute var payload: String = ""
}

@Entity
class OrderedIndexPageTieRow : OrderedIndexPageReadRow() {
    @Identifier @Attribute var id: Long = 0L
    @Index @Attribute var rank: Long = 0L
}

@Entity
class OrderedIndexPageScalarRow : OrderedIndexPageReadRow() {
    @Identifier @Attribute var id: Long = 0L
    @Index @Attribute var text: String? = null
    @Index @Attribute var time: Date? = null
    @Index @Attribute var number: Double? = null
}

@Entity
class OrderedIndexPageComputedRow : OrderedIndexPageReadRow() {
    @Identifier @Attribute var id: Long = 0L
    @Index @Attribute var rank: Long = 0L
        get() = -field
}

@Entity
class OrderedIndexPageParent : OrderedIndexPageReadRow() {
    @Identifier @Attribute var id: Long = 0L
    @Index @Attribute var rank: Long = 0L
    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = OrderedIndexPageChild::class,
        cascadePolicy = CascadePolicy.ALL, fetchPolicy = FetchPolicy.EAGER)
    var child: OrderedIndexPageChild? = null
}

@Entity
class OrderedIndexPageChild : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Attribute var name: String = ""

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        OrderedIndexPageReads.children.incrementAndGet()
    }
}

@Entity
class OrderedIndexPagePartitionRow : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Partition @Attribute var tenant: Long = 0L
    @Index @Attribute var rank: Long = 0L
}
