package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryListener
import com.onyx.persistence.query.QueryOrder
import entities.SelectIdentifierTestEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class QueryCacheReferenceIntegrationTest {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-query-cache-references-")
        val location = directory.toString()
        factory = EmbeddedPersistenceManagerFactory(
            location, location, DefaultSchemaContext(location, location), false,
        ).apply {
            storeType = StoreType.MEMORY_MAPPED_FILE
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager
        for (id in 1L..4096L) {
            manager.saveEntity(SelectIdentifierTestEntity().apply {
                this.id = id
                index = 1
                attribute = "row $id"
            })
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
    fun `cached and lazy pages retain the full matching domain and total count`() {
        val query = pageQuery()
        repeat(2) {
            assertEquals((11L..30L).toList(), manager.executeQuery<SelectIdentifierTestEntity>(query).map { it.id })
            assertEquals(4096, query.resultsCount)
        }
        val cached = assertNotNull(manager.context.queryCacheInteractor.getCachedQueryResults(query))
        assertEquals(4096, cached.references!!.size)
        assertEquals(4096L, manager.countForQuery(query))
        assertEquals((11L..30L).toList(), manager.executeLazyQuery<SelectIdentifierTestEntity>(query).map { it.id })
        assertEquals(4096, query.resultsCount)
    }

    @Test
    fun `subscription populated later keeps snapshots and receives membership changes`() {
        val events = LinkedBlockingQueue<String>()
        val query = pageQuery().apply {
            changeListener = object : QueryListener<SelectIdentifierTestEntity> {
                override fun onItemAdded(item: SelectIdentifierTestEntity) { events.add("add:${item.id}") }
                override fun onItemUpdated(item: SelectIdentifierTestEntity) { events.add("update:${item.id}") }
                override fun onItemRemoved(item: SelectIdentifierTestEntity) { events.add("remove:${item.id}") }
            }
        }
        manager.listen(query)
        val cached = assertNotNull(manager.context.queryCacheInteractor.getCachedQueryResults(query))
        assertNull(cached.references)
        assertEquals((11L..30L).toList(), manager.executeQuery<SelectIdentifierTestEntity>(query).map { it.id })
        assertSame(cached, manager.context.queryCacheInteractor.getCachedQueryResults(query))
        assertEquals(4096, cached.references!!.size)
        val initial = cached.references!!.toList()
        val snapshot = cached.references!!.iterator()

        val added = SelectIdentifierTestEntity().apply {
            id = 5000L
            index = 1
            attribute = "before"
        }
        manager.saveEntity(added)
        assertEquals("add:5000", events.poll(5, TimeUnit.SECONDS))
        assertEquals(4097L, manager.countForQuery(query))

        added.attribute = "after!"
        manager.saveEntity(added)
        assertEquals("update:5000", events.poll(5, TimeUnit.SECONDS))
        assertEquals(4097L, manager.countForQuery(query))

        added.index = 2
        manager.saveEntity(added)
        assertEquals("remove:5000", events.poll(5, TimeUnit.SECONDS))
        assertEquals(4096L, manager.countForQuery(query))

        added.index = 1
        manager.saveEntity(added)
        assertEquals("update:5000", events.poll(5, TimeUnit.SECONDS))
        manager.deleteEntity(added)
        assertEquals("remove:5000", events.poll(5, TimeUnit.SECONDS))
        assertEquals(4096L, manager.countForQuery(query))

        assertEquals(initial, snapshot.asSequence().toList())
        assertEquals((11L..30L).toList(), manager.executeQuery<SelectIdentifierTestEntity>(query).map { it.id })
        assertEquals(4096, query.resultsCount)
        assertEquals(1, cached.listeners.size)
    }

    private fun pageQuery() = Query(
        SelectIdentifierTestEntity::class.java,
        QueryCriteria("index", QueryCriteriaOperator.EQUAL, 1),
    ).apply {
        cache = true
        queryOrders = listOf(QueryOrder("id", true))
        firstRow = 10
        maxResults = 20
    }
}
