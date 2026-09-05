package database.query

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.store.StoreType
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.query.QueryCollector
import com.onyx.interactors.query.QueryCollectorFactory
import com.onyx.interactors.query.data.QuerySortComparator
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import entities.SelectIdentifierTestEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderedQueryPaginationTest {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var context: DefaultSchemaContext
    private lateinit var descriptor: EntityDescriptor
    private lateinit var rows: Map<Long, SelectIdentifierTestEntity>

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-ordered-pagination-")
        val location = directory.toString()
        context = DefaultSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = context,
            addShutdownHook = false
        ).apply {
            storeType = StoreType.IN_MEMORY
            setCredentials("admin", "admin")
            initialize()
        }
        rows = (1L..12L).associateWith { id ->
            factory.persistenceManager.saveEntity<IManagedEntity>(SelectIdentifierTestEntity().apply {
                this.id = id
                index = when {
                    id <= 2 -> 0
                    id <= 5 -> 1
                    id <= 9 -> 2
                    else -> 3
                }
                attribute = "row $id"
            }) as SelectIdentifierTestEntity
        }
        descriptor = context.getDescriptorForEntity(SelectIdentifierTestEntity::class.java, "")
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
    fun `ordered pages are independent of record arrival order`() {
        for (ascending in listOf(true, false)) {
            val expected = if (ascending) listOf(3L, 4L, 5L) else listOf(10L, 9L, 8L)
            for (arrival in listOf(SHUFFLED_IDS, SHUFFLED_IDS.reversed(), rows.keys.toList())) {
                val collector = collect<IManagedEntity>(query(ascending), arrival)
                assertEquals(expected, collector.results.map { (it as SelectIdentifierTestEntity).id })
                assertEquals(12, collector.getNumberOfResults())
                collector.finalizeResults()
                assertEquals(expected, collector.results.map { (it as SelectIdentifierTestEntity).id })
            }
        }
    }

    @Test
    fun `ordered offset works without a limit and past the last result`() {
        val unbounded = collect<IManagedEntity>(query().apply { maxResults = -1 })
        assertEquals((3L..12L).toList(), unbounded.results.map { (it as SelectIdentifierTestEntity).id })
        assertEquals(12, unbounded.getNumberOfResults())

        val beyondEnd = collect<IManagedEntity>(query().apply { firstRow = 20 })
        assertEquals(emptyList(), beyondEnd.results.toList())
        assertEquals(12, beyondEnd.getNumberOfResults())

        val hugeLimit = collect<IManagedEntity>(query().apply { maxResults = Int.MAX_VALUE })
        assertEquals((3L..12L).toList(), hugeLimit.results.map { (it as SelectIdentifierTestEntity).id })
    }

    @Test
    fun `lazy ordered references apply the offset exactly once`() {
        val collector = collect<IManagedEntity>(query(false).apply { isLazy = true })
        assertEquals(emptyList(), collector.results.toList())
        assertEquals(12, collector.references.size)
        assertEquals(12, collector.getNumberOfResults())
        repeat(2) {
            assertEquals(listOf(10L, 9L, 8L), collector.getLimitedReferences().map {
                (it.toManagedEntity(context, descriptor) as SelectIdentifierTestEntity).id
            })
        }
    }

    @Test
    fun `projection pagination preserves unselected sort attributes until the page is selected`() {
        val collector = collect<Map<String, Any?>>(query(false).apply {
            selections = listOf("attribute")
        })
        assertEquals(
            listOf(mapOf("attribute" to "row 10"), mapOf("attribute" to "row 9"), mapOf("attribute" to "row 8")),
            collector.results.toList()
        )
        assertEquals(12, collector.getNumberOfResults())
    }

    @Test
    fun `grouped aggregate pagination applies the offset after sorting completed groups`() {
        for (maxRows in listOf(2, -1)) {
            val collector = collect<Map<String, Any?>>(query().apply {
                queryOrders = listOf(QueryOrder("index", true))
                selections = listOf("count(id)")
                groupBy = listOf("index")
                firstRow = 1
                maxResults = maxRows
            })
            val expectedCounts = if (maxRows == 2) listOf(3, 4) else listOf(3, 4, 3)
            assertEquals(expectedCounts.map { mapOf("count(id)" to it) }, collector.results.toList())
            assertEquals(4, collector.getNumberOfResults())
        }
    }

    @Test
    fun `equal sort keys preserve arrival order across bounded and unbounded pages`() {
        for (ascending in listOf(true, false)) {
            for (arrival in listOf(SHUFFLED_IDS, SHUFFLED_IDS.reversed())) {
                val ordered = if (ascending) arrival.sortedBy { rows.getValue(it).index }
                    else arrival.sortedByDescending { rows.getValue(it).index }
                for (maxRows in listOf(3, -1)) {
                    val query = query().apply {
                        queryOrders = listOf(QueryOrder("index", ascending))
                        maxResults = maxRows
                    }
                    val expected = ordered.drop(query.firstRow).let {
                        if (maxRows > 0) it.take(maxRows) else it
                    }
                    val entities = collect<IManagedEntity>(query, arrival)
                    assertEquals(expected, entities.results.map { (it as SelectIdentifierTestEntity).id })
                    assertEquals(12, entities.getNumberOfResults())

                    val projection = collect<Map<String, Any?>>(query.apply {
                        selections = listOf("id")
                    }, arrival)
                    assertEquals(expected.map { mapOf("id" to it) }, projection.results.toList())
                    assertEquals(12, projection.getNumberOfResults())
                }
            }
        }
    }

    @Test
    fun `large tied and nullable sorts match stable ordering and retain only the requested prefix`() {
        val input = (1L..4096L).map { id ->
            SelectIdentifierTestEntity().apply {
                this.id = id
                attribute = if (id % 5L == 0L) null else "key ${id % 7L}"
            }
        }.shuffled(Random(42))
        for (ascending in listOf(true, false)) {
            val ordered = if (ascending) input.sortedBy { it.attribute }
                else input.sortedByDescending { it.attribute }
            for (maxRows in listOf(20, -1, 0, Int.MAX_VALUE)) {
                val query = query().apply {
                    queryOrders = listOf(QueryOrder("attribute", ascending))
                    firstRow = 100
                    maxResults = maxRows
                }
                val collector = QueryCollectorFactory.create<IManagedEntity>(context, descriptor, query)
                input.forEach { row ->
                    collector.collect(Reference(0L, row.id), row)
                    if (maxRows == 20) assertTrue(collector.results.size <= 120)
                }
                collector.finalizeResults()
                val expected = ordered.drop(100).let { if (maxRows > 0) it.take(maxRows) else it }
                assertEquals(expected.map { it.id }, collector.results.map { (it as SelectIdentifierTestEntity).id })
                assertEquals(input.size, collector.getNumberOfResults())
                assertEquals(input.size, collector.references.size)
            }
        }
    }

    @Test
    fun `distinct projection counts do not depend on heap admission or equal sort keys`() {
        for (maxRows in listOf(2, -1)) {
            val query = query().apply {
                queryOrders = listOf(QueryOrder("index", true))
                selections = listOf("index")
                isDistinct = true
                firstRow = 1
                maxResults = maxRows
            }
            val collector = collect<Map<String, Any?>>(query, SHUFFLED_IDS + SHUFFLED_IDS.reversed())
            val expected = if (maxRows == 2) listOf(1, 2) else listOf(1, 2, 3)
            assertEquals(expected.map { mapOf("index" to it) }, collector.results.toList())
            assertEquals(4, collector.getNumberOfResults())

            // Different projected rows sharing an order key must survive DISTINCT.
            val differentRows = collect<Map<String, Any?>>(query.apply {
                selections = listOf("index", "attribute")
                firstRow = 0
                maxResults = -1
            })
            assertEquals(12, differentRows.results.size)
            assertEquals(12, differentRows.getNumberOfResults())
        }
    }

    @Test
    fun `grouped selections and completed aggregates retain their full group counts`() {
        val groups = collect<Map<String, Any?>>(query().apply {
            queryOrders = listOf(QueryOrder("index", false))
            selections = listOf("index")
            groupBy = listOf("index")
            firstRow = 1
            maxResults = 2
        })
        assertEquals(listOf(mapOf("index" to 2), mapOf("index" to 1)), groups.results.toList())
        assertEquals(4, groups.getNumberOfResults())

        val aggregates = collect<Map<String, Any?>>(query().apply {
            queryOrders = listOf(QueryOrder("count(id)", false), QueryOrder("index", true))
            selections = listOf("index", "count(id)")
            groupBy = listOf("index")
            firstRow = 1
            maxResults = 2
        })
        assertEquals(
            listOf(mapOf("index" to 1, "count(id)" to 3), mapOf("index" to 3, "count(id)" to 3)),
            aggregates.results.toList()
        )
        assertEquals(4, aggregates.getNumberOfResults())
    }

    @Test
    fun `lazy references sort once and support ties unbounded and overflowing page ends`() {
        val expected = SHUFFLED_IDS.sortedBy { rows.getValue(it).index }.drop(2)
        for (maxRows in listOf(3, -1, Int.MAX_VALUE)) {
            val collector = collect<IManagedEntity>(query().apply {
                queryOrders = listOf(QueryOrder("index", true))
                isLazy = true
                maxResults = maxRows
            })
            repeat(2) {
                collector.finalizeResults()
                val actual = collector.getLimitedReferences().map {
                    (it.toManagedEntity(context, descriptor) as SelectIdentifierTestEntity).id
                }
                assertEquals(if (maxRows > 0) expected.take(maxRows) else expected, actual)
                assertEquals(12, collector.references.size)
            }
        }
    }

    @Test
    fun `cached eager and lazy pages keep all matching references and total counts`() {
        for (lazy in listOf(false, true)) {
            val query = query(false).apply { cache = true }
            repeat(2) {
                val actual = if (lazy) {
                    factory.persistenceManager.executeLazyQuery<SelectIdentifierTestEntity>(query)
                } else {
                    factory.persistenceManager.executeQuery<SelectIdentifierTestEntity>(query)
                }
                assertEquals(listOf(10L, 9L, 8L), actual.map { it.id })
                assertEquals(12, query.resultsCount)
            }
        }
    }

    @Test
    fun `parallel collection keeps the best rows and counts rejected candidates`() {
        val query = query(false).apply { firstRow = 100; maxResults = 20 }
        val collector = QueryCollectorFactory.create<IManagedEntity>(context, descriptor, query)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val futures = (0..3).map { worker ->
                executor.submit {
                    for (id in (worker + 1L)..4096L step 4) {
                        val row = SelectIdentifierTestEntity().apply { this.id = id }
                        collector.collect(Reference(0L, id), row)
                    }
                }
            }
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals(120, collector.results.size)
            collector.finalizeResults()
            assertEquals((3996L downTo 3977L).toList(), collector.results.map { (it as SelectIdentifierTestEntity).id })
            assertEquals(4096, collector.getNumberOfResults())
            assertEquals(4096, collector.references.size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `query comparators return zero for equal keys in both directions`() {
        val query = query().apply { queryOrders = listOf(QueryOrder("index", true)) }
        val comparator = QuerySortComparator(query, query.queryOrders!!.toTypedArray(), descriptor, context)
        val first = rows.getValue(1L)
        val second = rows.getValue(2L)
        assertEquals(0, comparator.compare(first, second))
        assertEquals(0, comparator.compare(second, first))
        assertEquals(0, comparator.compare(mapOf("index" to 0, "id" to 1), mapOf("index" to 0, "id" to 2)))
        assertEquals(0, comparator.compare(mapOf("index" to 0, "id" to 2), mapOf("index" to 0, "id" to 1)))
        val records = context.getRecordInteractor(descriptor)
        val firstReference = Reference(0L, records.getReferenceId(first.id))
        val secondReference = Reference(0L, records.getReferenceId(second.id))
        assertEquals(0, comparator.compare(firstReference, secondReference))
        assertEquals(0, comparator.compare(secondReference, firstReference))
    }

    private fun query(ascending: Boolean = true) = Query(
        SelectIdentifierTestEntity::class.java,
        QueryCriteria("attribute", QueryCriteriaOperator.NOT_NULL),
        QueryOrder("id", ascending)
    ).apply {
        firstRow = 2
        maxResults = 3
    }

    private fun <T> collect(query: Query, arrival: List<Long> = SHUFFLED_IDS): QueryCollector<T> {
        val collector = QueryCollectorFactory.create<T>(context, descriptor, query)
        val recordInteractor = context.getRecordInteractor(descriptor)
        arrival.forEach { id ->
            collector.collect(Reference(0L, recordInteractor.getReferenceId(id)), rows.getValue(id))
        }
        collector.finalizeResults()
        return collector
    }

    companion object {
        private val SHUFFLED_IDS = listOf(12L, 2L, 10L, 4L, 8L, 6L, 11L, 1L, 9L, 3L, 7L, 5L)
    }
}
