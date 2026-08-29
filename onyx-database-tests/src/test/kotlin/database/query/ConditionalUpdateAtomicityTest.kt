package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.descriptor.EntityDescriptor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.manager.createEntityIfAbsent
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.ConditionalUpdateEntity
import entities.identifiers.MutableSequenceIdentifierEntity
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ConditionalUpdateAtomicityTest {
    @Test
    fun ordinarySaveCannotPublishItsRowBeforeItsIndexesAcrossConditionalUpdate() {
        val directory = Files.createTempDirectory("onyx-save-cas-boundary-")
        val location = directory.toString()
        val context = DefaultSchemaContext(location, location)
        val factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = context,
            addShutdownHook = false,
        ).apply {
            storeType = StoreType.MEMORY_MAPPED_FILE
            setCredentials("admin", "admin")
            initialize()
        }
        val executor = Executors.newFixedThreadPool(2)
        val resumeSave = CountDownLatch(1)
        try {
            val ordinaryManager = PausingPersistenceManager(context, resumeSave)
            val conditionalManager: PersistenceManager = EmbeddedPersistenceManager(context)
            ordinaryManager.saveEntity<IManagedEntity>(ConditionalUpdateEntity().apply {
                id = "row-index-race"
                region = "central"
                owner = "base"
                generation = 0L
            })
            val ordinary = ordinaryManager.findByIdInPartition<ConditionalUpdateEntity>(
                ConditionalUpdateEntity::class.java,
                "row-index-race",
                "central",
            )!!.apply { owner = "ordinary" }
            ordinaryManager.pauseId = ordinary.id

            val saveFuture = executor.submit { ordinaryManager.saveEntity(ordinary) }
            assertTrue(ordinaryManager.rowStored.await(10, TimeUnit.SECONDS))

            val conditionalThread = AtomicReference<Thread>()
            val conditionalStarted = CountDownLatch(1)
            val conditionalFuture = executor.submit<Int> {
                conditionalThread.set(Thread.currentThread())
                conditionalStarted.countDown()
                conditionalOwnerUpdate(
                    conditionalManager,
                    id = "row-index-race",
                    region = "central",
                    expectedOwner = "ordinary",
                    newOwner = "conditional",
                )
            }
            assertTrue(conditionalStarted.await(10, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (conditionalThread.get().state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertEquals(
                Thread.State.BLOCKED,
                conditionalThread.get().state,
                "conditional update did not wait for the ordinary row+index mutation boundary",
            )

            resumeSave.countDown()
            saveFuture.get(20, TimeUnit.SECONDS)
            assertEquals(1, conditionalFuture.get(20, TimeUnit.SECONDS))

            val stored = ordinaryManager.findByIdInPartition<ConditionalUpdateEntity>(
                ConditionalUpdateEntity::class.java,
                "row-index-race",
                "central",
            )!!
            assertEquals("conditional", stored.owner)
            assertEquals(1, ownerMatches(ordinaryManager, "central", "conditional"))
            assertEquals(0, ownerMatches(ordinaryManager, "central", "ordinary"))
        } finally {
            resumeSave.countDown()
            executor.shutdownNow()
            factory.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun twoManagersCanHaveOnlyOneConditionalUpdateWinner() {
        val directory = Files.createTempDirectory("onyx-update-cas-")
        val location = directory.toString()
        val context = DefaultSchemaContext(location, location)
        val factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = context,
            addShutdownHook = false,
        ).apply {
            storeType = StoreType.MEMORY_MAPPED_FILE
            setCredentials("admin", "admin")
            initialize()
        }
        try {
            val firstManager = factory.persistenceManager
            val secondManager: PersistenceManager = EmbeddedPersistenceManager(context)
            firstManager.saveEntity<IManagedEntity>(ConditionalUpdateEntity().apply {
                id = "lease"
                region = "north"
                owner = "unowned"
                generation = 0L
            })
            firstManager.saveEntity<IManagedEntity>(ConditionalUpdateEntity().apply {
                id = "lease"
                region = "south"
                owner = "unowned"
                generation = 0L
            })
            val northDescriptorFromFirst =
                firstManager.context.getDescriptorForEntity(ConditionalUpdateEntity::class.java, "north")
            val northDescriptorFromSecond =
                secondManager.context.getDescriptorForEntity(ConditionalUpdateEntity::class.java, "north")
            assertSame(
                firstManager.context.getRecordInteractor(northDescriptorFromFirst),
                secondManager.context.getRecordInteractor(northDescriptorFromSecond),
            )

            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val contenders = listOf(firstManager to "worker-a", secondManager to "worker-b")
                val futures = contenders.map { (manager, owner) ->
                    executor.submit<Int> {
                        ready.countDown()
                        check(start.await(10, TimeUnit.SECONDS))
                        acquire(manager, "north", owner)
                    }
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS))
                start.countDown()
                assertEquals(1, futures.sumOf { it.get(20, TimeUnit.SECONDS) })
            } finally {
                executor.shutdownNow()
            }

            val north = firstManager.findByIdInPartition<ConditionalUpdateEntity>(
                ConditionalUpdateEntity::class.java,
                "lease",
                "north",
            )!!
            assertEquals(1L, north.generation)
            assertTrue(north.owner == "worker-a" || north.owner == "worker-b")

            val untouchedSouth = firstManager.findByIdInPartition<ConditionalUpdateEntity>(
                ConditionalUpdateEntity::class.java,
                "lease",
                "south",
            )!!
            assertEquals("unowned", untouchedSouth.owner)
            assertEquals(0L, untouchedSouth.generation)
            assertEquals(1, acquire(secondManager, "south", "worker-c"))
            assertEquals(
                "worker-c",
                firstManager.findByIdInPartition<ConditionalUpdateEntity>(
                    ConditionalUpdateEntity::class.java,
                    "lease",
                    "south",
                )!!.owner,
            )
        } finally {
            factory.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun twoManagersCanHaveOnlyOnePartitionedCreateIfAbsentWinner() {
        val directory = Files.createTempDirectory("onyx-create-once-")
        val location = directory.toString()
        val context = DefaultSchemaContext(location, location)
        val factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = context,
            addShutdownHook = false,
        ).apply {
            storeType = StoreType.MEMORY_MAPPED_FILE
            setCredentials("admin", "admin")
            initialize()
        }
        try {
            val firstManager = factory.persistenceManager
            val secondManager: PersistenceManager = EmbeddedPersistenceManager(context)
            val generated = MutableSequenceIdentifierEntity()
            assertFailsWith<IllegalArgumentException> {
                firstManager.createEntityIfAbsent(generated)
            }
            assertEquals(null, generated.identifier)
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val futures = listOf(firstManager to "creator-a", secondManager to "creator-b")
                    .map { (manager, owner) ->
                        executor.submit<ConditionalUpdateEntity?> {
                            ready.countDown()
                            check(start.await(10, TimeUnit.SECONDS))
                            manager.createEntityIfAbsent(ConditionalUpdateEntity().apply {
                                id = "create-once"
                                region = "west"
                                this.owner = owner
                                generation = 0L
                            })
                        }
                    }
                assertTrue(ready.await(10, TimeUnit.SECONDS))
                start.countDown()
                val winners = futures.map { it.get(20, TimeUnit.SECONDS) }.filterNotNull()
                assertEquals(1, winners.size)
                val stored = firstManager.findByIdInPartition<ConditionalUpdateEntity>(
                    ConditionalUpdateEntity::class.java,
                    "create-once",
                    "west",
                )!!
                assertEquals(winners.single().owner, stored.owner)
            } finally {
                executor.shutdownNow()
            }
        } finally {
            factory.close()
            directory.toFile().deleteRecursively()
        }
    }

    private fun acquire(manager: PersistenceManager, region: String, owner: String): Int {
        val query = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("id", QueryCriteriaOperator.EQUAL, "lease")
                .and("owner", QueryCriteriaOperator.EQUAL, "unowned")
                .and("generation", QueryCriteriaOperator.EQUAL, 0L),
            AttributeUpdate("owner", owner),
            AttributeUpdate("generation", 1L),
        )
        query.partition = region
        return manager.executeUpdate(query)
    }

    private fun conditionalOwnerUpdate(
        manager: PersistenceManager,
        id: String,
        region: String,
        expectedOwner: String,
        newOwner: String,
    ): Int {
        val query = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("id", QueryCriteriaOperator.EQUAL, id)
                .and("owner", QueryCriteriaOperator.EQUAL, expectedOwner),
            AttributeUpdate("owner", newOwner),
        )
        query.partition = region
        return manager.executeUpdate(query)
    }

    private fun ownerMatches(manager: PersistenceManager, region: String, owner: String): Int {
        val query = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("owner", QueryCriteriaOperator.EQUAL, owner),
        )
        query.partition = region
        return manager.executeQuery<ConditionalUpdateEntity>(query).size
    }

    private class PausingPersistenceManager(
        context: DefaultSchemaContext,
        private val resumeSave: CountDownLatch,
    ) : EmbeddedPersistenceManager(context) {
        val rowStored = CountDownLatch(1)

        @Volatile
        var pauseId: String? = null

        override fun onRecordSavedBeforeIndexes(
            entity: IManagedEntity,
            descriptor: EntityDescriptor,
            recordId: Long,
            isInsert: Boolean,
        ) {
            if (entity is ConditionalUpdateEntity && entity.id == pauseId) {
                rowStored.countDown()
                check(resumeSave.await(10, TimeUnit.SECONDS))
            }
        }
    }
}
