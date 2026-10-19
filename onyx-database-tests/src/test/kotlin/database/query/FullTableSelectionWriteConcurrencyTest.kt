package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.manager.findById
import com.onyx.persistence.manager.findByIdInPartition
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(Parameterized::class)
class FullTableSelectionWriteConcurrencyTest(private val storeType: StoreType) {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-select-write-concurrency-")
        val location = directory.toString()
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            addShutdownHook = false,
        ).apply {
            storeType = this@FullTableSelectionWriteConcurrencyTest.storeType
            initialize()
        }
        manager = factory.persistenceManager
    }

    @After
    fun cleanup() {
        BlockingSelectionGetter.gate?.release?.countDown()
        BlockingSelectionGetter.gate = null
        try {
            if (::factory.isInitialized) factory.close()
        } finally {
            if (::directory.isInitialized) directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `unpartitioned select allows update and insert while computed getter is blocked`() {
        saveRow(1L, "first")
        saveRow(2L, "second")

        assertSaveCompletesDuringSelection(selection(BlockingSelectionRow::class.java)) {
            saveRow(2L, "updated")
            saveRow(3L, "inserted")
            assertEquals("updated", manager.findById<BlockingSelectionRow>(2L)?.value)
            assertEquals("inserted", manager.findById<BlockingSelectionRow>(3L)?.value)
        }
    }

    @Test
    fun `single partition select allows update and insert in that partition while getter is blocked`() {
        assertPartitionSaveCompletesDuringSelection(7L)
    }

    @Test
    fun `all partitions select allows update and insert in scanned partition while getter is blocked`() {
        assertPartitionSaveCompletesDuringSelection(QueryPartitionMode.ALL)
    }

    private fun assertPartitionSaveCompletesDuringSelection(partition: Any) {
        savePartitionedRow(1L, 7L, "first")
        savePartitionedRow(2L, 7L, "second")
        savePartitionedRow(4L, 8L, "other partition")
        val query = selection(PartitionedBlockingSelectionRow::class.java).apply {
            this.partition = partition
        }

        assertSaveCompletesDuringSelection(query) {
            savePartitionedRow(2L, 7L, "updated")
            savePartitionedRow(3L, 7L, "inserted")
            assertEquals("updated", manager.findByIdInPartition<PartitionedBlockingSelectionRow>(2L, 7L)?.value)
            assertEquals("inserted", manager.findByIdInPartition<PartitionedBlockingSelectionRow>(3L, 7L)?.value)
        }
    }

    private fun selection(entityClass: Class<*>) = Query(
        entityClass,
        listOf("id", "computed"),
        // An ordinary, unindexed predicate exercises the normal full-table SELECT path.
        QueryCriteria("value", QueryCriteriaOperator.NOT_NULL),
    )

    private fun assertSaveCompletesDuringSelection(query: Query, save: () -> Unit) {
        val gate = SelectionGetterGate()
        val executor = Executors.newFixedThreadPool(2)
        BlockingSelectionGetter.gate = gate
        try {
            val selecting = executor.submit<List<Map<String, Any?>>> { manager.executeQuery(query) }
            assertTrue(gate.entered.await(5, TimeUnit.SECONDS), "SELECT did not reach its computed getter")

            val saving = executor.submit { save() }
            try {
                saving.get(5, TimeUnit.SECONDS)
            } catch (_: TimeoutException) {
                fail("A SELECT's computed getter held the table lock and blocked PersistenceManager.save")
            }
            assertEquals(1L, gate.release.count, "The save must finish before the getter is released")
            assertFalse(selecting.isDone, "SELECT should still be waiting in its computed getter")

            gate.release.countDown()
            val rows = selecting.get(5, TimeUnit.SECONDS)
            assertEquals("first", rows.single { it["id"] == 1L }["computed"])
        } finally {
            // Release the read callback before joining a writer blocked by the old implementation.
            gate.release.countDown()
            executor.shutdown()
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "SELECT/save workers did not stop")
            }
            BlockingSelectionGetter.gate = null
        }
    }

    private fun saveRow(id: Long, value: String) = manager.save(BlockingSelectionRow().apply {
        this.id = id
        this.value = value
    })

    private fun savePartitionedRow(id: Long, partition: Long, value: String) =
        manager.save(PartitionedBlockingSelectionRow().apply {
            this.id = id
            partitionId = partition
            this.value = value
        })

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun stores(): List<Array<StoreType>> = StoreType.entries.map { arrayOf(it) }
    }
}

private class SelectionGetterGate {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
}

private object BlockingSelectionGetter {
    @Volatile
    var gate: SelectionGetterGate? = null

    fun compute(id: Long, value: String): String {
        if (id == 1L) {
            gate?.let {
                it.entered.countDown()
                check(it.release.await(30, TimeUnit.SECONDS)) { "Computed getter was never released" }
            }
        }
        return value
    }
}

@Entity
class BlockingSelectionRow : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Attribute var value: String = ""

    val computed: String get() = BlockingSelectionGetter.compute(id, value)
}

@Entity
class PartitionedBlockingSelectionRow : ManagedEntity() {
    @Identifier @Attribute var id: Long = 0L
    @Partition @Attribute var partitionId: Long = 0L
    @Attribute var value: String = ""

    val computed: String get() = BlockingSelectionGetter.compute(id, value)
}
