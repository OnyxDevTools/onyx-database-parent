package database.query

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.store.StoreType
import com.onyx.extension.toManagedEntity
import com.onyx.interactors.query.QueryCollector
import com.onyx.interactors.query.QueryCollectorFactory
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
import kotlin.test.assertEquals

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
