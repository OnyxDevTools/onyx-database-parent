package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.interactors.query.QueryCollector
import com.onyx.interactors.query.impl.collectors.DefaultQueryCollector
import com.onyx.interactors.query.impl.collectors.UpdateQueryCollector
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryOrder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class ComputedSortConcurrencyTest(private val storeType: StoreType) {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-computed-sort-concurrency-")
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = directory.toString(),
            instance = directory.toString(),
            addShutdownHook = false,
        ).apply {
            storeType = this@ComputedSortConcurrencyTest.storeType
            initialize()
        }
    }

    @After
    fun cleanup() {
        ComputedSortRow.onScore = null
        try {
            if (::factory.isInitialized) factory.close()
        } finally {
            if (::directory.isInitialized) directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `computed sort scores overlap and are evaluated once per row for every entity collector`() {
        val rows = (1L..12L).map { id ->
            factory.persistenceManager.saveEntity(ComputedSortRow().apply { this.id = id })
        }
        val context = factory.schemaContext
        val descriptor = context.getDescriptorForEntity(ComputedSortRow::class.java, "")
        val records = context.getRecordInteractor(descriptor)
        val references = rows.associate { it.id to Reference(0L, records.getReferenceId(it.id)) }
        val idsByReference = references.entries.associate { it.value to it.key }

        for (mode in listOf("bounded", "unbounded", "lazy", "update", "updateOffset")) {
            val query = Query(ComputedSortRow::class.java, QueryOrder("computedScore", false)).apply {
                isLazy = mode == "lazy"
                firstRow = if (mode == "update") 0 else 2
                maxResults = if (mode == "unbounded") -1 else 3
            }
            val collector: QueryCollector<*> = if (mode.startsWith("update")) {
                UpdateQueryCollector(query, context, descriptor)
            } else {
                DefaultQueryCollector(query, context, descriptor)
            }
            val entered = CountDownLatch(4)
            val release = CountDownLatch(1)
            val calls = ConcurrentHashMap<Long, AtomicInteger>()
            ComputedSortRow.onScore = { id ->
                calls.computeIfAbsent(id) { AtomicInteger() }.incrementAndGet()
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Timed out releasing computed score" }
            }
            val executor = Executors.newFixedThreadPool(4)
            try {
                val futures = rows.chunked(3).map { chunk ->
                    executor.submit {
                        chunk.forEach { row -> collector.collect(references.getValue(row.id), row) }
                    }
                }
                try {
                    assertTrue(
                        entered.await(5, TimeUnit.SECONDS),
                        "$mode collection serialized computed scores before storing results",
                    )
                } finally {
                    release.countDown()
                }
                futures.forEach { it.get(15, TimeUnit.SECONDS) }
                if (mode == "bounded") assertTrue(collector.results.size <= 5)
                collector.finalizeResults()
                collector.finalizeResults()
                val expected = (12L downTo 1L).drop(query.firstRow).let {
                    if (query.maxResults > 0) it.take(query.maxResults) else it
                }
                val actual = if (mode == "lazy" || mode.startsWith("update")) {
                    collector.getLimitedReferences().map(idsByReference::getValue)
                } else {
                    collector.results.map { (it as ComputedSortRow).id }
                }
                assertEquals(expected, actual, "$mode order and pagination")
                assertEquals(12, collector.getNumberOfResults(), "$mode total count")
                assertEquals(rows.associate { it.id to 1 }, calls.mapValues { it.value.get() }, mode)
            } finally {
                release.countDown()
                executor.shutdownNow()
                executor.awaitTermination(5, TimeUnit.SECONDS)
                ComputedSortRow.onScore = null
            }
        }
    }

    @Test
    fun `async reference collection computes scores concurrently before retaining the top page`() {
        assumeTrue(Runtime.getRuntime().availableProcessors() > 1)
        val manager = factory.persistenceManager
        val rows = (1L..1025L).map { id ->
            manager.saveEntity(ComputedSortRow().apply { this.id = id })
        }
        val context = factory.schemaContext
        val descriptor = context.getDescriptorForEntity(ComputedSortRow::class.java, "")
        val records = context.getRecordInteractor(descriptor)
        val references = rows.mapTo(LinkedHashSet()) { Reference(0L, records.getReferenceId(it.id)) }
        val query = Query(ComputedSortRow::class.java, QueryOrder("computedScore", false)).apply {
            maxResults = 5
        }
        val collector = DefaultQueryCollector(query, context, descriptor)
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        ComputedSortRow.onScore = {
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        val caller = Executors.newSingleThreadExecutor()
        try {
            val collection = caller.submit { collector.setReferenceSet(references) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS), "Async collection serialized score getters")
            } finally {
                release.countDown()
            }
            collection.get(20, TimeUnit.SECONDS)
            assertEquals(5, collector.results.size)
            collector.finalizeResults()
            assertEquals((1025L downTo 1021L).toList(), collector.results.map { (it as ComputedSortRow).id })
            assertEquals(1025, calls.get())
            assertEquals(1025, collector.getNumberOfResults())
        } finally {
            release.countDown()
            caller.shutdownNow()
            caller.awaitTermination(5, TimeUnit.SECONDS)
            ComputedSortRow.onScore = null
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stores(): List<Array<StoreType>> = listOf(
            arrayOf(StoreType.IN_MEMORY),
            arrayOf(StoreType.FILE),
            arrayOf(StoreType.MEMORY_MAPPED_FILE),
        )
    }
}

@Entity
class ComputedSortRow : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L

    val computedScore: Double
        get() {
            onScore?.invoke(id)
            return id.toDouble()
        }

    companion object {
        @Volatile var onScore: ((Long) -> Unit)? = null
    }
}
