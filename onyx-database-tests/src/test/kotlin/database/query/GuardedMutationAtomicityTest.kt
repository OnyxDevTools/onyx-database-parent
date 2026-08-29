package database.query

import com.onyx.descriptor.EntityDescriptor
import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.annotations.Partition
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.EntityMutationGuard
import com.onyx.persistence.manager.GuardedMutationResult
import com.onyx.persistence.manager.GuardedMutationStatus
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.manager.executeDeleteIfGuardMatches
import com.onyx.persistence.manager.executeUpdateIfGuardMatches
import com.onyx.persistence.manager.findById
import com.onyx.persistence.manager.findByIdInPartition
import com.onyx.persistence.manager.saveEntitiesIfGuardMatches
import com.onyx.persistence.manager.saveEntityIfGuardMatches
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.HnswSearchQuery
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode
import com.onyx.persistence.query.from
import com.onyx.persistence.query.hnswCandidates
import entities.ConditionalUpdateEntity
import entities.VectorSearchEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuardedMutationAtomicityTest {
    private lateinit var databaseDirectory: Path
    private lateinit var context: GuardedDeleteWorkTrackingSchemaContext
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-guarded-mutation-")
        val location = databaseDirectory.toString()
        context = GuardedDeleteWorkTrackingSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = context,
            addShutdownHook = false,
        ).apply {
            storeType = StoreType.MEMORY_MAPPED_FILE
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager
    }

    @After
    fun cleanup() {
        try {
            factory.close()
        } finally {
            databaseDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun pausedGuardedChildSaveCommitsBeforeLeaseTakeoverAndStaleWriterCannotMutate() {
        saveLease()
        val resumeSave = CountDownLatch(1)
        val guardedManager = PausingPersistenceManager(context, resumeSave).apply { pauseId = "child" }
        val takeoverManager: PersistenceManager = EmbeddedPersistenceManager(context)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val child = child("child", "children", "writer-a")
            val guardedFuture = executor.submit<GuardedMutationResult<ConditionalUpdateEntity>> {
                guardedManager.saveEntityIfGuardMatches(child, currentGuard())
            }
            assertTrue(guardedManager.rowStored.await(10, TimeUnit.SECONDS))

            val takeoverThread = AtomicReference<Thread>()
            val takeoverStarted = CountDownLatch(1)
            val takeoverFuture = executor.submit<Int> {
                takeoverThread.set(Thread.currentThread())
                takeoverStarted.countDown()
                takeLease(takeoverManager)
            }
            assertTrue(takeoverStarted.await(10, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (takeoverThread.get().state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertEquals(
                Thread.State.BLOCKED,
                takeoverThread.get().state,
                "lease takeover did not wait for the guarded row+index commit",
            )

            resumeSave.countDown()
            val applied = guardedFuture.get(20, TimeUnit.SECONDS)
            assertEquals(GuardedMutationStatus.APPLIED, applied.status)
            assertTrue(applied.guardMatched)
            assertEquals(1, applied.affected)
            assertEquals(1, takeoverFuture.get(20, TimeUnit.SECONDS))

            val stored = manager.findByIdInPartition<ConditionalUpdateEntity>("child", "children")!!
            assertEquals("writer-a", stored.owner)
            stored.owner = "stale-writer"
            val stale = manager.saveEntityIfGuardMatches(stored, currentGuard())
            assertEquals(GuardedMutationStatus.GUARD_MISMATCH, stale.status)
            assertFalse(stale.guardMatched)
            assertEquals(0, stale.affected)
            assertNull(stale.value)
            assertEquals(
                "writer-a",
                manager.findByIdInPartition<ConditionalUpdateEntity>("child", "children")!!.owner,
            )
        } finally {
            resumeSave.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun guardedHnswSaveAndDeleteKeepAuthoritativeRowAndGraphConsistent() {
        saveLease()
        val nearest = VectorSearchEntity().apply {
            title = "nearest"
            hnswVector(floatArrayOf(1f, 0f, 0f), CALIBRATION)
        }
        val anchor = VectorSearchEntity().apply {
            title = "anchor"
            hnswVector(floatArrayOf(0f, 1f, 0f), CALIBRATION)
        }

        val saved = manager.saveEntitiesIfGuardMatches(listOf(nearest, anchor), currentGuard())
        assertEquals(GuardedMutationStatus.APPLIED, saved.status)
        assertEquals("nearest", nearest(floatArrayOf(1f, 0f, 0f)).title)

        val oldRepresentation = nearest.vectorRepresentation()!!.hnswVector.copyOf()
        nearest.title = "moved-by-stale-writer"
        nearest.hnswVector(floatArrayOf(-1f, 0f, 0f), CALIBRATION)
        assertEquals(1, takeLease(manager))
        val stale = manager.saveEntityIfGuardMatches(nearest, currentGuard())
        assertEquals(GuardedMutationStatus.GUARD_MISMATCH, stale.status)
        val persisted = manager.findById<VectorSearchEntity>(nearest.id)!!
        assertEquals("nearest", persisted.title)
        assertTrue(persisted.vectorRepresentation()!!.hnswVector.contentEquals(oldRepresentation))
        assertEquals("anchor", nearest(floatArrayOf(-1f, 0f, 0f)).title)

        val delete = Query(
            VectorSearchEntity::class.java,
            QueryCriteria("id", QueryCriteriaOperator.EQUAL, nearest.id),
        )
        val deleted = manager.executeDeleteIfGuardMatches(delete, takeoverGuard())
        assertEquals(GuardedMutationStatus.APPLIED, deleted.status)
        assertEquals(1, deleted.value)
        assertNull(manager.findById<VectorSearchEntity>(nearest.id))
        assertEquals("anchor", nearest(floatArrayOf(1f, 0f, 0f)).title)

        val deletedAgain = manager.executeDeleteIfGuardMatches(delete, takeoverGuard())
        assertEquals(GuardedMutationStatus.APPLIED, deletedAgain.status)
        assertEquals(0, deletedAgain.value)
    }

    @Test
    fun guardedUpdateDistinguishesGuardMismatchFromAppliedOneAndAppliedZero() {
        saveLease()
        manager.saveEntity<IManagedEntity>(child("update-target", "update-targets", "before"))

        val appliedQuery = guardedUpdateQuery("update-target", "update-targets", "before", "after")
        val applied = manager.executeUpdateIfGuardMatches(appliedQuery, currentGuard())
        assertEquals(GuardedMutationStatus.APPLIED, applied.status)
        assertEquals(1, applied.value)
        assertEquals(1, applied.affected)
        assertEquals(1, appliedQuery.maxResults)
        assertEquals(
            "after",
            manager.findByIdInPartition<ConditionalUpdateEntity>("update-target", "update-targets")!!.owner,
        )

        val zero = manager.executeUpdateIfGuardMatches(
            guardedUpdateQuery("update-target", "update-targets", "before", "never"),
            currentGuard(),
        )
        assertEquals(GuardedMutationStatus.APPLIED, zero.status)
        assertEquals(0, zero.value)
        assertEquals(0, zero.affected)

        assertEquals(1, takeLease(manager))
        val stale = manager.executeUpdateIfGuardMatches(
            guardedUpdateQuery("update-target", "update-targets", "after", "stale"),
            currentGuard(),
        )
        assertEquals(GuardedMutationStatus.GUARD_MISMATCH, stale.status)
        assertNull(stale.value)
        assertEquals(0, stale.affected)
        assertEquals(
            "after",
            manager.findByIdInPartition<ConditionalUpdateEntity>("update-target", "update-targets")!!.owner,
        )
    }

    @Test
    fun guardedUpdateIsHardBoundedAndRejectsPartitionMovesAndWildcardPartitions() {
        saveLease()
        manager.saveEntities(
            listOf(
                child("bounded-update-1", "updates", "before"),
                child("bounded-update-2", "updates", "before"),
            ),
        )

        val bounded = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("owner", QueryCriteriaOperator.EQUAL, "before")
                .and("id", QueryCriteriaOperator.EQUAL, "bounded-update-1"),
            AttributeUpdate("owner", "after"),
        ).also { it.partition = "updates" }
        val result = manager.executeUpdateIfGuardMatches(bounded, currentGuard())
        assertEquals(GuardedMutationStatus.APPLIED, result.status)
        assertEquals(1, result.affected)
        assertEquals(1, bounded.resultsCount)
        assertEquals(
            1,
            listOf("bounded-update-1", "bounded-update-2").count { id ->
                manager.findByIdInPartition<ConditionalUpdateEntity>(id, "updates")!!.owner == "after"
            },
        )

        val overBound = guardedUpdateQuery("bounded-update-1", "updates", "before", "after").also {
            it.maxResults = 2
        }
        assertFailsWith<IllegalArgumentException> {
            manager.executeUpdateIfGuardMatches(overBound, currentGuard())
        }

        val identifierUpdate = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("id", QueryCriteriaOperator.EQUAL, "bounded-update-1"),
            AttributeUpdate("id", "replacement-id"),
        ).also { it.partition = "updates" }
        assertFailsWith<IllegalArgumentException> {
            manager.executeUpdateIfGuardMatches(identifierUpdate, currentGuard())
        }

        val partitionMove = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("id", QueryCriteriaOperator.EQUAL, "bounded-update-1"),
            AttributeUpdate("region", "other-partition"),
        ).also { it.partition = "updates" }
        assertFailsWith<IllegalArgumentException> {
            manager.executeUpdateIfGuardMatches(partitionMove, currentGuard())
        }


        val hotNonUnique = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("owner", QueryCriteriaOperator.EQUAL, "before"),
            AttributeUpdate("owner", "after"),
        ).also { it.partition = "updates" }
        assertFailsWith<IllegalArgumentException> {
            manager.executeUpdateIfGuardMatches(hotNonUnique, currentGuard())
        }

        val wildcardIdentifier = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("id", QueryCriteriaOperator.LIKE, "bounded-update-%"),
            AttributeUpdate("owner", "after"),
        ).also { it.partition = "updates" }
        assertFailsWith<IllegalArgumentException> {
            manager.executeUpdateIfGuardMatches(wildcardIdentifier, currentGuard())
        }

        val listIdentifier = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria(
                "id",
                QueryCriteriaOperator.EQUAL,
                listOf("bounded-update-1", "bounded-update-2"),
            ),
            AttributeUpdate("owner", "after"),
        ).also { it.partition = "updates" }
        assertFailsWith<IllegalArgumentException> {
            manager.executeUpdateIfGuardMatches(listIdentifier, currentGuard())
        }

        val subqueryIdentifier = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria(
                "id",
                QueryCriteriaOperator.EQUAL,
                Query(
                    ConditionalUpdateEntity::class.java,
                    QueryCriteria("owner", QueryCriteriaOperator.EQUAL, "before"),
                ).also { it.partition = "updates" },
            ),
            AttributeUpdate("owner", "after"),
        ).also { it.partition = "updates" }
        assertFailsWith<IllegalArgumentException> {
            manager.executeUpdateIfGuardMatches(subqueryIdentifier, currentGuard())
        }

        listOf("unknown", "owner.value").forEach { nonLocalField ->
            val nonLocal = Query(
                ConditionalUpdateEntity::class.java,
                QueryCriteria("id", QueryCriteriaOperator.EQUAL, "bounded-update-1")
                    .and(nonLocalField, QueryCriteriaOperator.EQUAL, "value"),
                AttributeUpdate("owner", "after"),
            ).also { it.partition = "updates" }
            assertFailsWith<IllegalArgumentException> {
                manager.executeUpdateIfGuardMatches(nonLocal, currentGuard())
            }
        }

        val disjunction = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("id", QueryCriteriaOperator.EQUAL, "bounded-update-1")
                .or("owner", QueryCriteriaOperator.EQUAL, "before"),
            AttributeUpdate("owner", "after"),
        ).also { it.partition = "updates" }
        assertFailsWith<IllegalArgumentException> {
            manager.executeUpdateIfGuardMatches(disjunction, currentGuard())
        }

        val negated = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("id", QueryCriteriaOperator.EQUAL, "bounded-update-1").apply {
                isNot = true
            },
            AttributeUpdate("owner", "after"),
        ).also { it.partition = "updates" }
        assertFailsWith<IllegalArgumentException> {
            manager.executeUpdateIfGuardMatches(negated, currentGuard())
        }

        listOf<Any>(QueryPartitionMode.ALL, "ALL", "all").forEach { wildcard ->
            val wildcardQuery = guardedUpdateQuery(
                "bounded-update-1",
                "updates",
                "before",
                "after",
            ).also { it.partition = wildcard }
            assertFailsWith<IllegalArgumentException> {
                manager.executeUpdateIfGuardMatches(wildcardQuery, currentGuard())
            }
        }

        assertFailsWith<IllegalArgumentException> {
            manager.saveEntityIfGuardMatches(child("reserved", "ALL", "writer-a"), currentGuard())
        }
        assertFailsWith<IllegalArgumentException> {
            manager.executeUpdateIfGuardMatches(
                guardedUpdateQuery("bounded-update-1", "updates", "before", "after"),
                currentGuard().copy(partition = "all"),
            )
        }
    }

    @Test
    fun guardedUpdateCompletesBeforeConcurrentGuardTakeover() {
        saveLease()
        manager.saveEntity<IManagedEntity>(child("race-update", "targets", "before"))
        val targetDescriptor = context.getDescriptorForEntity(
            ConditionalUpdateEntity::class.java,
            "targets",
        )
        val targetMonitor = context.getRecordInteractor(targetDescriptor)
        val executor = Executors.newFixedThreadPool(2)
        val guardedThread = AtomicReference<Thread>()
        val takeoverThread = AtomicReference<Thread>()
        val guardedStarted = CountDownLatch(1)
        val takeoverStarted = CountDownLatch(1)
        try {
            val guardedFuture: java.util.concurrent.Future<GuardedMutationResult<Int>>
            val takeoverFuture: java.util.concurrent.Future<Int>
            synchronized(targetMonitor) {
                guardedFuture = executor.submit<GuardedMutationResult<Int>> {
                    guardedThread.set(Thread.currentThread())
                    guardedStarted.countDown()
                    manager.executeUpdateIfGuardMatches(
                        guardedUpdateQuery("race-update", "targets", "before", "after"),
                        currentGuard(),
                    )
                }
                assertTrue(guardedStarted.await(10, TimeUnit.SECONDS))
                awaitBlocked(guardedThread, "guarded update did not wait for its target store")

                takeoverFuture = executor.submit<Int> {
                    takeoverThread.set(Thread.currentThread())
                    takeoverStarted.countDown()
                    takeLease(manager)
                }
                assertTrue(takeoverStarted.await(10, TimeUnit.SECONDS))
                awaitBlocked(takeoverThread, "lease takeover did not wait for the guarded update")
            }

            assertEquals(GuardedMutationStatus.APPLIED, guardedFuture.get(20, TimeUnit.SECONDS).status)
            assertEquals(1, takeoverFuture.get(20, TimeUnit.SECONDS))
            assertEquals(
                "after",
                manager.findByIdInPartition<ConditionalUpdateEntity>("race-update", "targets")!!.owner,
            )
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun guardedDeleteDistinguishesMismatchFromAnAppliedZeroAndRequiresConcreteScope() {
        saveLease()
        manager.saveEntity<IManagedEntity>(child("delete-1", "delete-targets", "remove"))
        manager.saveEntity<IManagedEntity>(child("delete-2", "delete-targets", "remove"))

        val delete = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("owner", QueryCriteriaOperator.EQUAL, "remove"),
        ).also { it.partition = "delete-targets" }
        val applied = manager.executeDeleteIfGuardMatches(delete, currentGuard())
        assertEquals(GuardedMutationStatus.APPLIED, applied.status)
        assertEquals(2, applied.value)
        assertEquals(2, applied.affected)

        val zero = manager.executeDeleteIfGuardMatches(delete, currentGuard())
        assertEquals(GuardedMutationStatus.APPLIED, zero.status)
        assertEquals(0, zero.value)
        assertEquals(0, zero.affected)

        manager.saveEntity<IManagedEntity>(child("delete-3", "delete-targets", "remove"))
        assertEquals(1, takeLease(manager))
        val mismatch = manager.executeDeleteIfGuardMatches(delete, currentGuard())
        assertEquals(GuardedMutationStatus.GUARD_MISMATCH, mismatch.status)
        assertEquals(0, mismatch.affected)
        assertNull(mismatch.value)
        assertTrue(manager.findByIdInPartition<ConditionalUpdateEntity>("delete-3", "delete-targets") != null)

        delete.partition = QueryPartitionMode.ALL
        assertFailsWith<IllegalArgumentException> {
            manager.executeDeleteIfGuardMatches(delete, takeoverGuard())
        }
    }

    @Test
    fun guardedBatchSupportsDeterministicallyLockedConcretePartitionsAndRejectsUnstableInputs() {
        saveLease()
        val east = child("east-child", "east", "writer-a")
        val west = child("west-child", "west", "writer-a")
        val applied = manager.saveEntitiesIfGuardMatches(listOf(east, west), currentGuard())
        assertEquals(GuardedMutationStatus.APPLIED, applied.status)
        assertEquals(2, applied.value!!.size)
        assertTrue(manager.findByIdInPartition<ConditionalUpdateEntity>(east.id, east.region) != null)
        assertTrue(manager.findByIdInPartition<ConditionalUpdateEntity>(west.id, west.region) != null)

        val wrongNumericType = manager.saveEntityIfGuardMatches(
            child("typed-mismatch", "east", "writer-a"),
            currentGuard().copy(expectedFields = mapOf("generation" to 7, "owner" to "writer-a")),
        )
        assertEquals(GuardedMutationStatus.GUARD_MISMATCH, wrongNumericType.status)
        assertNull(manager.findByIdInPartition<ConditionalUpdateEntity>("typed-mismatch", "east"))

        assertFailsWith<IllegalArgumentException> {
            manager.saveEntityIfGuardMatches(child("blank", "", "writer-a"), currentGuard())
        }
        assertFailsWith<IllegalArgumentException> {
            manager.saveEntityIfGuardMatches(
                child("bad-guard", "east", "writer-a"),
                currentGuard().copy(partition = QueryPartitionMode.ALL),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            manager.saveEntityIfGuardMatches(
                child("null-guard", "east", "writer-a"),
                currentGuard().copy(identifier = null),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            manager.saveEntitiesIfGuardMatches(
                List(501) { child("too-many-$it", "east", "writer-a") },
                currentGuard(),
            )
        }
    }

    @Test
    fun guardedDeletePhysicallyBatchesLargeConcretePartitionsAtFiveHundredRows() {
        saveLease()
        manager.saveEntities(
            List(1_201) { ordinal ->
                BoundedDeleteEntity().apply {
                    id = "bounded-$ordinal"
                    partition = "bounded-targets"
                    group = "remove"
                }
            }
        )
        context.resetGuardedDeleteWork()

        val firstQuery = boundedDeleteQuery()
        val first = manager.executeDeleteIfGuardMatches(firstQuery, currentGuard())
        assertEquals(GuardedMutationStatus.APPLIED, first.status)
        assertEquals(500, first.affected)
        assertEquals(500, firstQuery.resultsCount)
        assertEquals(701L, boundedDeleteCount())

        val second = manager.executeDeleteIfGuardMatches(boundedDeleteQuery(), currentGuard())
        assertEquals(500, second.affected)
        assertEquals(201L, boundedDeleteCount())

        val third = manager.executeDeleteIfGuardMatches(boundedDeleteQuery(), currentGuard())
        assertEquals(201, third.affected)
        assertEquals(0L, boundedDeleteCount())

        val fourth = manager.executeDeleteIfGuardMatches(boundedDeleteQuery(), currentGuard())
        assertEquals(GuardedMutationStatus.APPLIED, fourth.status)
        assertEquals(0, fourth.affected)

        val work = context.guardedDeleteWork
        assertEquals(listOf(500, 500, 201, 0), work.map { it.deletedCount })
        assertEquals(listOf(500, 500, 201, 0), work.map { it.postingVisits })
        assertEquals(listOf(500, 500, 201, 0), work.map { it.recordLookups })
        assertTrue(work.all { it.pageLimit == 500 })
        assertTrue(work.all { it.cardinalityProbePostingVisits == 0 })
        assertTrue(work.all { it.drivingAttribute == "group" })
        assertEquals(0, context.fullTableScans)

        manager.saveEntity<IManagedEntity>(BoundedDeleteEntity().apply {
            id = "keep-default-safe"
            partition = "bounded-targets"
            group = "remove"
        })
        val unfiltered = Query().apply {
            entityType = BoundedDeleteEntity::class.java
            partition = "bounded-targets"
        }
        assertFailsWith<IllegalArgumentException> {
            manager.executeDeleteIfGuardMatches(unfiltered, currentGuard())
        }
        assertEquals(1L, boundedDeleteCount())
    }

    @Test
    fun guardedDeleteUsesTheSmallerPositiveLimitForItsPhysicalIndexPage() {
        saveLease()
        manager.saveEntities(
            List(150) { ordinal ->
                BoundedDeleteEntity().apply {
                    id = "limited-$ordinal"
                    partition = "limited-targets"
                    group = "remove"
                }
            }
        )
        context.resetGuardedDeleteWork()

        val query = Query(
            BoundedDeleteEntity::class.java,
            QueryCriteria("group", QueryCriteriaOperator.EQUAL, "remove"),
        ).also {
            it.partition = "limited-targets"
            it.maxResults = 100
        }
        val first = manager.executeDeleteIfGuardMatches(query, currentGuard())
        assertEquals(100, first.affected)
        val firstWork = context.guardedDeleteWork.single()
        assertEquals(100, firstWork.pageLimit)
        assertEquals(100, firstWork.postingVisits)
        assertEquals(100, firstWork.deletedCount)

        val second = manager.executeDeleteIfGuardMatches(query, currentGuard())
        assertEquals(50, second.affected)
        assertEquals(listOf(100, 50), context.guardedDeleteWork.map { it.deletedCount })
        assertEquals(0, context.fullTableScans)
    }

    @Test
    fun guardedDeleteDoesNoRecordScanForSparseAndZeroExactPostings() {
        saveLease()
        manager.saveEntities(
            List(2_001) { ordinal ->
                BoundedDeleteEntity().apply {
                    id = "sparse-$ordinal"
                    partition = "sparse-targets"
                    group = if (ordinal == 1_337) "one-match" else "keep"
                }
            }
        )
        context.resetGuardedDeleteWork()

        val sparse = boundedDeleteQuery("sparse-targets", "one-match")
        assertEquals(1, manager.executeDeleteIfGuardMatches(sparse, currentGuard()).affected)
        assertEquals(0, manager.executeDeleteIfGuardMatches(sparse, currentGuard()).affected)

        val work = context.guardedDeleteWork
        assertEquals(listOf(1, 0), work.map { it.postingVisits })
        assertEquals(listOf(1, 0), work.map { it.recordLookups })
        assertEquals(listOf(1, 0), work.map { it.deletedCount })
        assertEquals(0, context.fullTableScans)
    }

    @Test
    fun guardedDeleteStreamsExactInRoutesAcrossRepeatedPages() {
        saveLease()
        manager.saveEntities(
            List(750) { ordinal ->
                BoundedDeleteEntity().apply {
                    id = "in-route-$ordinal"
                    partition = "in-targets"
                    group = when (ordinal % 3) {
                        0 -> "route-a"
                        1 -> "route-b"
                        else -> "keep"
                    }
                }
            }
        )
        context.resetGuardedDeleteWork()

        fun query() = Query(
            BoundedDeleteEntity::class.java,
            QueryCriteria(
                "group",
                QueryCriteriaOperator.IN,
                listOf("route-b", "route-a", "route-a"),
            ),
        ).also { it.partition = "in-targets" }

        assertEquals(500, manager.executeDeleteIfGuardMatches(query(), currentGuard()).affected)
        assertEquals(0, manager.executeDeleteIfGuardMatches(query(), currentGuard()).affected)
        assertEquals(listOf(500, 0), context.guardedDeleteWork.map { it.postingVisits })
        assertEquals(listOf(500, 0), context.guardedDeleteWork.map { it.deletedCount })
        assertEquals(0, context.fullTableScans)
    }

    @Test
    fun guardedDeleteRetainsFullTableFallbackForUnsupportedUnindexedShape() {
        saveLease()
        manager.saveEntities(
            List(4) { ordinal ->
                BoundedDeleteEntity().apply {
                    id = "fallback-$ordinal"
                    partition = "fallback-targets"
                    group = "keep"
                    state = if (ordinal < 2) "remove" else "keep"
                }
            }
        )
        context.resetGuardedDeleteWork()

        val query = Query(
            BoundedDeleteEntity::class.java,
            QueryCriteria("state", QueryCriteriaOperator.EQUAL, "remove"),
        ).also { it.partition = "fallback-targets" }
        assertEquals(2, manager.executeDeleteIfGuardMatches(query, currentGuard()).affected)

        assertTrue(context.guardedDeleteWork.isEmpty())
        assertEquals(1, context.fullTableScans)
    }

    @Test
    fun guardedDeleteSelectsASelectiveAndDriverAndExhaustsFilteredPostingExactly() {
        saveLease()
        manager.saveEntities(
            List(1_201) { ordinal ->
                BoundedDeleteEntity().apply {
                    id = "selective-$ordinal"
                    partition = "selective-targets"
                    group = "namespace-hot"
                    document = if (ordinal < 620) "target-document" else "other-document"
                    state = if (ordinal < 501) "remove" else "keep"
                }
            }
        )
        context.resetGuardedDeleteWork()

        val query = selectiveDeleteQuery()
        assertEquals(500, manager.executeDeleteIfGuardMatches(query, currentGuard()).affected)
        assertEquals(1, manager.executeDeleteIfGuardMatches(selectiveDeleteQuery(), currentGuard()).affected)

        val work = context.guardedDeleteWork
        assertEquals(listOf("document", "document"), work.map { it.drivingAttribute })
        assertEquals(listOf(2, 2), work.map { it.eligibleIndexCount })
        assertEquals(500, work[0].postingVisits)
        assertEquals(120, work[1].postingVisits)
        assertEquals(1, work[1].matchedReferenceCount)
        assertEquals(1, work[1].deletedCount)
        assertEquals(1_002, work[0].cardinalityProbePostingVisits)
        assertEquals(621, work[1].cardinalityProbePostingVisits)
        assertEquals(0, context.fullTableScans)
        assertEquals(0L, manager.countForQuery(selectiveDeleteQuery()))
    }

    @Test
    fun mutationExceptionPropagatesWithoutFalseAppliedResultAndReleasesGuardForTakeover() {
        saveLease()
        val throwing = ThrowingPersistenceManager(context).apply { throwId = "explode" }
        val error = assertFailsWith<ExecutionException> {
            Executors.newSingleThreadExecutor().use { executor ->
                executor.submit {
                    throwing.saveEntityIfGuardMatches(
                        child("explode", "children", "writer-a"),
                        currentGuard(),
                    )
                }.get(20, TimeUnit.SECONDS)
            }
        }
        assertTrue(error.cause is IllegalStateException)
        assertEquals(1, takeLease(manager), "the guard lock was not released after mutation failure")
    }

    private fun saveLease() {
        manager.saveEntity<IManagedEntity>(ConditionalUpdateEntity().apply {
            id = LEASE_ID
            region = LEASE_PARTITION
            owner = "writer-a"
            generation = 7L
        })
    }

    private fun currentGuard(): EntityMutationGuard = EntityMutationGuard(
        entityType = ConditionalUpdateEntity::class.java,
        identifier = LEASE_ID,
        partition = LEASE_PARTITION,
        expectedFields = linkedMapOf("generation" to 7L, "owner" to "writer-a"),
    )

    private fun takeoverGuard(): EntityMutationGuard = EntityMutationGuard(
        entityType = ConditionalUpdateEntity::class.java,
        identifier = LEASE_ID,
        partition = LEASE_PARTITION,
        expectedFields = linkedMapOf("generation" to 8L, "owner" to "writer-b"),
    )

    private fun takeLease(persistenceManager: PersistenceManager): Int {
        val query = Query(
            ConditionalUpdateEntity::class.java,
            QueryCriteria("id", QueryCriteriaOperator.EQUAL, LEASE_ID)
                .and("owner", QueryCriteriaOperator.EQUAL, "writer-a")
                .and("generation", QueryCriteriaOperator.EQUAL, 7L),
            AttributeUpdate("owner", "writer-b"),
            AttributeUpdate("generation", 8L),
        )
        query.partition = LEASE_PARTITION
        return persistenceManager.executeUpdate(query)
    }

    private fun child(id: String, region: String, owner: String) = ConditionalUpdateEntity().apply {
        this.id = id
        this.region = region
        this.owner = owner
        generation = 1L
    }

    private fun boundedDeleteQuery(
        partition: String = "bounded-targets",
        group: String = "remove",
    ) = Query(
        BoundedDeleteEntity::class.java,
        QueryCriteria("group", QueryCriteriaOperator.EQUAL, group),
    ).also { it.partition = partition }

    private fun boundedDeleteCount(): Long = manager.countForQuery(boundedDeleteQuery())

    private fun selectiveDeleteQuery() = Query(
        BoundedDeleteEntity::class.java,
        QueryCriteria("group", QueryCriteriaOperator.EQUAL, "namespace-hot")
            .and("document", QueryCriteriaOperator.EQUAL, "target-document")
            .and("state", QueryCriteriaOperator.EQUAL, "remove"),
    ).also { it.partition = "selective-targets" }

    private fun guardedUpdateQuery(
        id: String,
        partition: String,
        expectedOwner: String,
        updatedOwner: String,
    ) = Query(
        ConditionalUpdateEntity::class.java,
        QueryCriteria("id", QueryCriteriaOperator.EQUAL, id)
            .and("owner", QueryCriteriaOperator.EQUAL, expectedOwner),
        AttributeUpdate("owner", updatedOwner),
    ).also { it.partition = partition }

    private fun awaitBlocked(thread: AtomicReference<Thread>, message: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (thread.get().state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.yield()
        }
        assertEquals(Thread.State.BLOCKED, thread.get().state, message)
    }

    private fun nearest(vector: FloatArray): VectorSearchEntity = manager.from<VectorSearchEntity>()
        .hnswCandidates(HnswSearchQuery(CALIBRATION, vector, maxCandidates = 1, efSearch = 16))
        .list<VectorSearchEntity>()
        .single()

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

    private class ThrowingPersistenceManager(context: DefaultSchemaContext) : EmbeddedPersistenceManager(context) {
        var throwId: String? = null

        override fun onRecordSavedBeforeIndexes(
            entity: IManagedEntity,
            descriptor: EntityDescriptor,
            recordId: Long,
            isInsert: Boolean,
        ) {
            if (entity is ConditionalUpdateEntity && entity.id == throwId) {
                throw IllegalStateException("injected mutation failure")
            }
        }
    }

    private companion object {
        const val LEASE_ID = "lease"
        const val LEASE_PARTITION = "leases"
        const val CALIBRATION = 0x47554152444c
    }
}

@Entity
private class BoundedDeleteEntity : ManagedEntity() {
    @Identifier
    @Attribute(nullable = false)
    var id: String = ""

    @Partition
    @Attribute(nullable = false)
    var partition: String = ""

    @Attribute(nullable = false)
    @Index
    var group: String = ""

    @Attribute(nullable = false)
    @Index
    var document: String = ""

    @Attribute(nullable = false)
    var state: String = ""
}
