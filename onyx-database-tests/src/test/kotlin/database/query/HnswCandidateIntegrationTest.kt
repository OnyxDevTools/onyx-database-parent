package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.diskmap.DiskMap
import com.onyx.interactors.index.impl.FingerprintIndexInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.HnswSearchQuery
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.from
import com.onyx.persistence.query.hnswCandidates
import com.onyx.persistence.manager.findById
import com.onyx.extension.referenceId
import com.onyx.vector.SemanticVectorSignature
import com.onyx.vector.VectorRepresentation
import entities.VectorPartitionedEntity
import entities.VectorSearchEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HnswCandidateIntegrationTest {
    private lateinit var databaseDirectory: Path
    private lateinit var context: HnswWorkTrackingSchemaContext
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager
    private var open = false

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-hnsw-")
        openDatabase()
    }

    @After
    fun cleanup() {
        try {
            closeDatabase()
        } finally {
            if (::databaseDirectory.isInitialized) databaseDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun nearestCandidatesPersistAcrossReopenAndExcludeSignatureOnlyRows() {
        save("north", floatArrayOf(1f, 0f, 0f))
        save("near-north", floatArrayOf(0.9f, 0.1f, 0f))
        save("south", floatArrayOf(-1f, 0f, 0f))
        manager.saveEntity<IManagedEntity>(VectorSearchEntity().apply {
            title = "signature-only"
            semanticSignature(signature(CALIBRATION_ONE))
        })

        assertEquals(
            listOf("north", "near-north"),
            query(floatArrayOf(1f, 0f, 0f), maxCandidates = 2).map(VectorSearchEntity::title)
        )

        closeDatabase()
        openDatabase()

        assertEquals(
            listOf("north", "near-north", "south"),
            query(floatArrayOf(1f, 0f, 0f), maxCandidates = 4).map(VectorSearchEntity::title)
        )
        assertFalse(query(floatArrayOf(1f, 0f, 0f), maxCandidates = 4).any { it.title == "signature-only" })
    }

    @Test
    fun updateMovesNearestNeighborAndRepeatedDeletesRepairEntryPoint() {
        val records = (0 until 24).map { ordinal ->
            val angle = ordinal * 0.19
            save("node-$ordinal", floatArrayOf(cos(angle).toFloat(), sin(angle).toFloat(), 0.1f))
        }.toMutableList()
        val moved = records.first()
        assertEquals("node-0", query(floatArrayOf(1f, 0f, 0.1f), maxCandidates = 1).single().title)

        moved.hnswVector(floatArrayOf(-1f, 0f, 0.1f), CALIBRATION_ONE)
        manager.saveEntity<IManagedEntity>(moved)
        assertFalse(query(floatArrayOf(1f, 0f, 0.1f), maxCandidates = 1).single().title == "node-0")

        // Deleting every insertion ordinal necessarily removes whichever nodes became entry
        // points. Search must remain usable after every repair until the graph is empty.
        records.forEachIndexed { index, entity ->
            assertTrue(manager.deleteEntity(entity))
            val remaining = records.size - index - 1
            val results = query(floatArrayOf(0f, 1f, 0.1f), maxCandidates = 3)
            assertTrue(results.none { it.id == entity.id })
            if (remaining == 0) assertTrue(results.isEmpty()) else assertTrue(results.isNotEmpty())
        }
    }

    @Test
    fun calibrationDimensionAndPartitionIsolationAreFailClosed() {
        save("calibration-one", floatArrayOf(1f, 0f, 0f), CALIBRATION_ONE)
        save("calibration-two", floatArrayOf(1f, 0f, 0f), CALIBRATION_TWO)

        assertEquals(
            listOf("calibration-one"),
            query(floatArrayOf(1f, 0f, 0f), calibrationId = CALIBRATION_ONE).map(VectorSearchEntity::title)
        )
        assertEquals(
            listOf("calibration-two"),
            query(floatArrayOf(1f, 0f, 0f), calibrationId = CALIBRATION_TWO).map(VectorSearchEntity::title)
        )
        assertFailsWith<IllegalArgumentException> {
            query(floatArrayOf(1f, 0f), calibrationId = CALIBRATION_ONE)
        }
        assertTrue(query(floatArrayOf(1f, 0f, 0f), calibrationId = 999L).isEmpty())

        savePartition("north", "north-near", floatArrayOf(1f, 0f, 0f))
        savePartition("south", "south-near", floatArrayOf(1f, 0f, 0f))
        val north = manager.from<VectorPartitionedEntity>()
            .hnswCandidates(HnswSearchQuery(CALIBRATION_ONE, floatArrayOf(1f, 0f, 0f), 5, 16))
            .inPartition("north")
            .list<VectorPartitionedEntity>()
        assertEquals(listOf("north-near"), north.map(VectorPartitionedEntity::tag))

        assertFailsWith<IllegalArgumentException> {
            manager.from<VectorPartitionedEntity>()
                .hnswCandidates(HnswSearchQuery(CALIBRATION_ONE, floatArrayOf(1f, 0f, 0f), 5, 16))
                .list<VectorPartitionedEntity>()
        }
    }

    @Test
    fun rebuildRestoresPersistentGraphWithoutQueryTimeReconstruction() {
        repeat(40) { ordinal ->
            save("rebuild-$ordinal", deterministicVector(ordinal, 12))
        }
        val interactor = manager.context.getIndexInteractor(
            requireNotNull(
                manager.context.getBaseDescriptorForEntity(VectorSearchEntity::class.java)
                    ?.indexes
                    ?.get(VectorManagedEntity.REPRESENTATION_FIELD)
            )
        )
        val queryVector = deterministicVector(17, 12)
        val nearestBeforeRebuild = query(queryVector, maxCandidates = 1, efSearch = 40).single().title

        interactor.clear()
        assertTrue(query(queryVector, maxCandidates = 3).isEmpty())

        interactor.rebuild()
        assertEquals(
            nearestBeforeRebuild,
            query(queryVector, maxCandidates = 1, efSearch = 40).single().title,
        )
    }

    @Test
    fun efSearchIsAnObservedHardLevelZeroDistanceBound() {
        repeat(500) { ordinal -> save("bounded-$ordinal", deterministicVector(ordinal, 16)) }
        context.resetHnswWork()

        val results = query(
            deterministicVector(301, 16),
            maxCandidates = 10,
            efSearch = 37,
        )

        assertTrue(context.isCaptured)
        assertEquals(37, context.efSearch)
        assertEquals(10, context.maxCandidates)
        assertTrue(context.distanceEvaluations <= 37)
        assertTrue(context.upperLayerDistanceEvaluations <= 64)
        assertEquals(results.size, context.resultCount)
        assertFalse(context.isExactFilteredScan)
        assertTrue(results.size <= 10)
    }

    @Test
    fun heldOutApproximateRecallTracksBruteForceCosine() {
        val random = Random(0x4f4e5958)
        val dimensions = 32
        val centers = List(8) { randomUnitVector(random, dimensions) }
        val corpus = ArrayList<Pair<Long, FloatArray>>(800)
        repeat(800) { ordinal ->
            val vector = if (ordinal % 9 == 0) {
                randomUnitVector(random, dimensions)
            } else {
                noisyUnitVector(centers[ordinal % centers.size], random, noise = 0.18f)
            }
            corpus += save("quality-$ordinal", vector).id to vector
        }
        val heldOutQueries = List(12) { ordinal ->
            if (ordinal >= 10) randomUnitVector(random, dimensions)
            else noisyUnitVector(centers[ordinal % centers.size], random, noise = 0.11f)
        }

        var hits = 0
        heldOutQueries.forEach { queryVector ->
            check(corpus.none { (_, indexed) -> indexed.contentEquals(queryVector) })
            val expected = corpus.asSequence()
                .map { (id, vector) -> id to cosine(queryVector, vector) }
                .sortedWith(compareByDescending<Pair<Long, Float>> { it.second }.thenBy { it.first })
                .take(RECALL_K)
                .map(Pair<Long, Float>::first)
                .toSet()
            val actual = query(
                queryVector,
                maxCandidates = RECALL_K,
                efSearch = 240,
            ).mapTo(HashSet(), VectorSearchEntity::id)
            hits += actual.count(expected::contains)
        }

        val recallAtK = hits.toDouble() / (heldOutQueries.size * RECALL_K).toDouble()
        assertTrue(
            recallAtK >= MIN_HELD_OUT_RECALL,
            "held-out recall@$RECALL_K was $recallAtK; expected at least $MIN_HELD_OUT_RECALL",
        )
    }

    @Test
    fun rejectedWrongDimensionUpdatePreservesRowAndGraphThenValidRetrySucceeds() {
        val moving = save("moving", floatArrayOf(1f, 0f, 0f))
        save("anchor", floatArrayOf(0f, 1f, 0f))
        val originalVector = moving.vectorRepresentation()!!.hnswVector.copyOf()

        moving.hnswVector(floatArrayOf(1f, 0f), CALIBRATION_ONE)
        assertFailsWith<IllegalArgumentException> { manager.saveEntity<IManagedEntity>(moving) }

        val persisted = manager.findById<VectorSearchEntity>(moving.id)!!
        assertTrue(persisted.vectorRepresentation()!!.hnswVector.contentEquals(originalVector))
        assertEquals("moving", query(floatArrayOf(1f, 0f, 0f), maxCandidates = 1).single().title)
        assertEquals(2L, hnswInteractor().validateHnswGraph(CALIBRATION_ONE))

        moving.hnswVector(floatArrayOf(-1f, 0f, 0f), CALIBRATION_ONE)
        manager.saveEntity<IManagedEntity>(moving)
        assertEquals("moving", query(floatArrayOf(-1f, 0f, 0f), maxCandidates = 1).single().title)
        assertEquals(2L, hnswInteractor().validateHnswGraph(CALIBRATION_ONE))
    }

    @Test
    fun heavyPruningUpdateDeleteAndReinsertKeepsEveryEdgeLiveReciprocalAndBounded() {
        val active = LinkedHashMap<Long, Pair<VectorSearchEntity, FloatArray>>()
        repeat(180) { ordinal ->
            val vector = deterministicVector(ordinal, 20)
            val entity = save("churn-$ordinal", vector)
            active[entity.id] = entity to vector
        }
        assertEquals(active.size.toLong(), hnswInteractor().validateHnswGraph(CALIBRATION_ONE))

        repeat(4) { round ->
            active.values.take(28).forEachIndexed { offset, (entity, _) ->
                val moved = deterministicVector(10_000 + round * 100 + offset, 20)
                entity.hnswVector(moved, CALIBRATION_ONE)
                manager.saveEntity<IManagedEntity>(entity)
                active[entity.id] = entity to moved
            }
            val removed = active.values.drop(50).take(12).map { it.first }
            removed.forEach { entity ->
                assertTrue(manager.deleteEntity(entity))
                active.remove(entity.id)
            }
            repeat(12) { offset ->
                val vector = deterministicVector(20_000 + round * 100 + offset, 20)
                val entity = save("reinsert-$round-$offset", vector)
                active[entity.id] = entity to vector
            }

            assertEquals(active.size.toLong(), hnswInteractor().validateHnswGraph(CALIBRATION_ONE))
            val queryVector = deterministicVector(30_000 + round, 20)
            val expected = active.values.asSequence()
                .sortedByDescending { (_, vector) -> cosine(queryVector, vector) }
                .take(10)
                .map { (entity, _) -> entity.id }
                .toSet()
            val actual = query(queryVector, maxCandidates = 10, efSearch = 120)
                .mapTo(HashSet(), VectorSearchEntity::id)
            assertTrue(actual.count(expected::contains) >= 5, "churn recall@10 fell below 0.5")
        }
    }

    @Test
    fun saturated768DimensionDeletesHaveHardRepairAndWriteBoundsUnderChurn() {
        val dimensions = 768
        val active = LinkedHashMap<Long, Pair<VectorSearchEntity, FloatArray>>()
        repeat(180) { ordinal ->
            val vector = deterministicVector(60_000 + ordinal, dimensions)
            val entity = save("wide-$ordinal", vector)
            active[entity.id] = entity to vector
        }
        val interactor = hnswInteractor()
        val saturated = active.values.asSequence()
            .map(Pair<VectorSearchEntity, FloatArray>::first)
            .filter { interactor.hnswNodeLevel(graphRecordId(it)) == 0 }
            .maxBy { interactor.hnswNodeDegree(graphRecordId(it)) ?: -1 }
        assertEquals(32, interactor.hnswNodeDegree(graphRecordId(saturated)), "test graph did not saturate M0")

        repeat(10) { round ->
            val victim = if (round == 0) saturated else active.values.asSequence()
                .map(Pair<VectorSearchEntity, FloatArray>::first)
                .filter { interactor.hnswNodeLevel(graphRecordId(it)) == 0 }
                .maxWith(
                    compareBy<VectorSearchEntity> { interactor.hnswNodeDegree(graphRecordId(it)) ?: -1 }
                        .thenByDescending(VectorSearchEntity::id)
                )
            val victimRecordId = graphRecordId(victim)
            assertTrue(manager.deleteEntity(victim))
            active.remove(victim.id)

            val work = interactor.lastHnswRemovalWork()
            assertEquals(victimRecordId, work.recordId)
            assertEquals(1, work.layersVisited)
            assertTrue(work.repairPairEvaluations <= 192, "delete repair exceeded its vector-work budget")
            assertTrue(work.repairEdgesAdded <= 32, "delete repair exceeded its edge budget")
            assertTrue(work.distinctPeersRewritten <= 32)
            assertTrue(work.nodeWrites <= 33, "saturated delete wrote ${work.nodeWrites} graph nodes")
            assertEquals(1, work.metadataWrites)

            val replacementVector = deterministicVector(70_000 + round, dimensions)
            val replacement = save("wide-replacement-$round", replacementVector)
            active[replacement.id] = replacement to replacementVector
            assertEquals(active.size.toLong(), interactor.validateHnswGraph(CALIBRATION_ONE))
        }

        var hits = 0
        val queryCount = 6
        repeat(queryCount) { ordinal ->
            val queryVector = deterministicVector(80_000 + ordinal, dimensions)
            val expected = active.values.asSequence()
                .sortedByDescending { (_, vector) -> cosine(queryVector, vector) }
                .take(10)
                .map { (entity, _) -> entity.id }
                .toSet()
            val actual = query(queryVector, maxCandidates = 10, efSearch = 160)
                .mapTo(HashSet(), VectorSearchEntity::id)
            hits += actual.count(expected::contains)
        }
        val recallAtTen = hits.toDouble() / (queryCount * 10).toDouble()
        assertTrue(recallAtTen >= 0.60, "768d delete-churn recall@10 fell to $recallAtTen")
    }

    @Test
    fun concurrentSearchesShareTheGraphReadLock() {
        repeat(500) { ordinal -> save("concurrent-$ordinal", deterministicVector(ordinal, 64)) }
        context.resetHnswWork()
        val executor = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until 8).map { ordinal ->
                executor.submit<List<VectorSearchEntity>> {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    query(
                        deterministicVector(40_000 + ordinal, 64),
                        maxCandidates = 20,
                        efSearch = 400,
                    )
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            futures.forEach { assertTrue(it.get(30, TimeUnit.SECONDS).isNotEmpty()) }
        } finally {
            executor.shutdownNow()
        }
        assertTrue(
            context.maxConcurrentSearchesObserved >= 2,
            "native HNSW searches did not overlap",
        )
    }

    @Test
    fun concurrentSaveAndStreamingRebuildShareRecordBeforeGraphLockOrder() {
        repeat(120) { ordinal -> save("maintenance-$ordinal", deterministicVector(ordinal, 16)) }
        val interactor = hnswInteractor()
        val descriptor = requireNotNull(
            manager.context.getBaseDescriptorForEntity(VectorSearchEntity::class.java)
        )
        val recordInteractor = manager.context.getRecordInteractor(descriptor)
        val executor = Executors.newFixedThreadPool(2)
        val rebuildThread = AtomicReference<Thread>()
        val rebuildStarted = CountDownLatch(1)
        try {
            val rebuildFuture = synchronized(recordInteractor) {
                val future = executor.submit {
                    rebuildThread.set(Thread.currentThread())
                    rebuildStarted.countDown()
                    interactor.rebuild()
                }
                assertTrue(rebuildStarted.await(10, TimeUnit.SECONDS))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (rebuildThread.get().state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                    Thread.yield()
                }
                assertEquals(Thread.State.BLOCKED, rebuildThread.get().state)
                val saveFuture = executor.submit {
                    save("maintenance-concurrent", deterministicVector(50_000, 16))
                }
                future to saveFuture
            }
            rebuildFuture.first.get(30, TimeUnit.SECONDS)
            rebuildFuture.second.get(30, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
        assertEquals(121L, interactor.validateHnswGraph(CALIBRATION_ONE))
        assertTrue(query(deterministicVector(50_000, 16)).isNotEmpty())
    }

    @Test
    fun failedStreamingRebuildStaysDirtyAcrossReopenUntilExplicitRecovery() {
        save("valid-three", floatArrayOf(1f, 0f, 0f), CALIBRATION_ONE)
        val incompatible = save("valid-two", floatArrayOf(0f, 1f), CALIBRATION_TWO)
        val corrupted = incompatible.vectorRepresentation()!!.copy(
            hnswCalibrationId = CALIBRATION_ONE,
        )
        incompatible.vectorRepresentation(corrupted)
        rawRecords()[incompatible.id] = incompatible

        assertFailsWith<IllegalArgumentException> { hnswInteractor().rebuild() }
        assertTrue(hnswInteractor().hnswRequiresRebuild())
        assertFailsWith<IllegalStateException> { query(floatArrayOf(1f, 0f, 0f)) }

        closeDatabase()
        openDatabase()
        assertTrue(hnswInteractor().hnswRequiresRebuild())
        assertFailsWith<IllegalStateException> { query(floatArrayOf(1f, 0f, 0f)) }

        val repaired = manager.findById<VectorSearchEntity>(incompatible.id)!!
        repaired.hnswVector(floatArrayOf(0f, 1f, 0f), CALIBRATION_ONE)
        rawRecords()[repaired.id] = repaired
        hnswInteractor().rebuild()
        assertFalse(hnswInteractor().hnswRequiresRebuild())
        assertEquals(2L, hnswInteractor().validateHnswGraph(CALIBRATION_ONE))
        assertTrue(query(floatArrayOf(0f, 1f, 0f)).isNotEmpty())
    }

    @Test
    fun legacyOrCorruptPersistentStateFailsClosedButExplicitRebuildRemainsReachable() {
        save("recoverable-state", floatArrayOf(1f, 0f, 0f))
        closeDatabase()

        openDatabase()
        hnswStateMap()[1L] = legacyHnswState()
        manager.context.getDataFile(vectorDescriptor()).commit()
        closeDatabase()

        openDatabase()
        assertTrue(hnswInteractor().hnswRequiresRebuild())
        assertFailsWith<IllegalStateException> { query(floatArrayOf(1f, 0f, 0f)) }
        hnswInteractor().rebuild()
        assertEquals(1L, hnswInteractor().validateHnswGraph(CALIBRATION_ONE))
        closeDatabase()

        openDatabase()
        hnswStateMap()[1L] = byteArrayOf(1, 2, 3)
        manager.context.getDataFile(vectorDescriptor()).commit()
        closeDatabase()

        openDatabase()
        assertTrue(hnswInteractor().hnswRequiresRebuild())
        assertFailsWith<IllegalStateException> { query(floatArrayOf(1f, 0f, 0f)) }
        hnswInteractor().rebuild()
        assertEquals(1L, hnswInteractor().validateHnswGraph(CALIBRATION_ONE))
    }

    @Test
    fun vectorRemovalArmsDurableDirtyMarkerBeforeRecordWrite() {
        val entityId = save("remove-vector", floatArrayOf(1f, 0f, 0f)).id
        closeDatabase()
        openDatabase()
        val entity = manager.findById<VectorSearchEntity>(entityId)!!
        val descriptor = requireNotNull(manager.context.getBaseDescriptorForEntity(VectorSearchEntity::class.java))
        val stateMap: DiskMap<Long, ByteArray> = manager.context.getDataFile(descriptor).getHashMap(
            Long::class.java,
            hnswMapBaseName() + "_hnsw_state",
        )
        val cleanState = stateMap.getValue(1L).copyOf()
        val withoutHnsw = entity.vectorRepresentation()!!.copy(
            hnswCalibrationId = VectorRepresentation.NO_CALIBRATION,
            hnswVector = byteArrayOf(),
        )
        entity.vectorRepresentation(withoutHnsw)

        // Save only the authoritative record, deliberately stopping before saveIndexes to model
        // the crash window. The pre-write index hook must already have persisted DIRTY.
        manager.context.getRecordInteractor(descriptor).save(entity)
        assertFalse(
            stateMap.getValue(1L).contentEquals(cleanState),
            "DIRTY marker was not armed before the row write",
        )
        assertTrue(hnswInteractor().hnswRequiresRebuild())

        closeDatabase()
        openDatabase()
        assertTrue(hnswInteractor().hnswRequiresRebuild())
        assertFailsWith<IllegalStateException> { query(floatArrayOf(1f, 0f, 0f)) }
        hnswInteractor().rebuild()
        assertFalse(hnswInteractor().hnswRequiresRebuild())
        assertTrue(query(floatArrayOf(1f, 0f, 0f)).isEmpty())
    }

    @Test
    fun admissionContractRejectsCompositionMutationAndNegation() {
        val hnsw = hnswCandidates(
            HnswSearchQuery(CALIBRATION_ONE, floatArrayOf(1f, 0f, 0f), 5, 16)
        )
        val composed = Query(
            VectorSearchEntity::class.java,
            hnsw.and("id", QueryCriteriaOperator.GREATER_THAN, 0L)
        )
        val delete = Query(
            VectorSearchEntity::class.java,
            hnswCandidates(HnswSearchQuery(CALIBRATION_ONE, floatArrayOf(1f, 0f, 0f), 5, 16))
        )
        val negated = QueryCriteria(
            Query.FULL_TEXT_ATTRIBUTE,
            QueryCriteriaOperator.HNSW_CANDIDATES,
            HnswSearchQuery(CALIBRATION_ONE, floatArrayOf(1f, 0f, 0f), 5, 16)
        ).also { it.isNot = true }

        assertFailsWith<IllegalArgumentException> { manager.executeQuery<VectorSearchEntity>(composed) }
        assertFailsWith<IllegalArgumentException> { manager.executeDelete(delete) }
        assertFailsWith<IllegalArgumentException> {
            manager.executeQuery<VectorSearchEntity>(Query(VectorSearchEntity::class.java, negated))
        }
    }

    private fun save(
        title: String,
        vector: FloatArray,
        calibrationId: Long = CALIBRATION_ONE,
    ): VectorSearchEntity = manager.saveEntity<IManagedEntity>(VectorSearchEntity().apply {
        this.title = title
        body = "HNSW integration payload"
        hnswVector(vector, calibrationId)
    }) as VectorSearchEntity

    private fun savePartition(region: String, tag: String, vector: FloatArray) {
        manager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
            this.region = region
            this.tag = tag
            body = "partitioned HNSW payload"
            hnswVector(vector, CALIBRATION_ONE)
        })
    }

    private fun query(
        vector: FloatArray,
        calibrationId: Long = CALIBRATION_ONE,
        maxCandidates: Int = 5,
        efSearch: Int = maxOf(16, maxCandidates),
    ): List<VectorSearchEntity> = manager.from<VectorSearchEntity>()
        .hnswCandidates(
            HnswSearchQuery(
                calibrationId = calibrationId,
                vector = vector,
                maxCandidates = maxCandidates,
                efSearch = efSearch,
            )
        )
        .list()

    private fun deterministicVector(ordinal: Int, dimensions: Int): FloatArray =
        FloatArray(dimensions) { dimension ->
            (sin((ordinal + 1.0) * (dimension + 1.0) * 0.017) +
                cos((ordinal + 3.0) * (dimension + 2.0) * 0.011)).toFloat()
        }

    private fun randomUnitVector(random: Random, dimensions: Int): FloatArray =
        normalize(FloatArray(dimensions) { random.nextFloat() * 2f - 1f })

    private fun noisyUnitVector(
        center: FloatArray,
        random: Random,
        noise: Float,
    ): FloatArray = normalize(
        FloatArray(center.size) { dimension ->
            center[dimension] + (random.nextFloat() * 2f - 1f) * noise
        }
    )

    private fun normalize(vector: FloatArray): FloatArray {
        val magnitude = sqrt(vector.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        return FloatArray(vector.size) { vector[it] / magnitude }
    }

    private fun cosine(first: FloatArray, second: FloatArray): Float {
        var dot = 0.0
        var firstMagnitude = 0.0
        var secondMagnitude = 0.0
        for (index in first.indices) {
            dot += first[index].toDouble() * second[index].toDouble()
            firstMagnitude += first[index].toDouble() * first[index].toDouble()
            secondMagnitude += second[index].toDouble() * second[index].toDouble()
        }
        return (dot / sqrt(firstMagnitude * secondMagnitude)).toFloat()
    }

    private fun signature(calibrationId: Long): SemanticVectorSignature {
        val fingerprint = longArrayOf(0x1357_2468_1357_2468L)
        return SemanticVectorSignature(
            calibrationId = calibrationId,
            bucketId = 0,
            cells = intArrayOf(0),
            cellCounts = intArrayOf(2),
            fingerprint = fingerprint,
            bands = SemanticVectorSignature.splitIntoFourBands(fingerprint),
        )
    }

    private fun hnswInteractor(): FingerprintIndexInteractor {
        val descriptor = vectorDescriptor()
        return manager.context.getIndexInteractor(
            requireNotNull(descriptor.indexes[VectorManagedEntity.REPRESENTATION_FIELD])
        ) as FingerprintIndexInteractor
    }

    private fun graphRecordId(entity: VectorSearchEntity): Long =
        entity.referenceId(manager.context, vectorDescriptor())

    private fun hnswMapBaseName(): String {
        val descriptor = vectorDescriptor()
        val index = requireNotNull(descriptor.indexes[VectorManagedEntity.REPRESENTATION_FIELD])
        return descriptor.entityClass.name + index.name + "_fingerprint_v${index.encodingVersion}"
    }

    private fun rawRecords(): DiskMap<Long, IManagedEntity> {
        val descriptor = vectorDescriptor()
        return manager.context.getDataFile(descriptor).getHashMap(
            descriptor.identifier!!.type,
            descriptor.entityClass.name,
        )
    }

    private fun vectorDescriptor() = requireNotNull(
        manager.context.getBaseDescriptorForEntity(VectorSearchEntity::class.java)
    )

    private fun hnswStateMap(): DiskMap<Long, ByteArray> =
        manager.context.getDataFile(vectorDescriptor()).getHashMap(
            Long::class.java,
            hnswMapBaseName() + "_hnsw_state",
        )

    private fun legacyHnswState(): ByteArray {
        val payloadSize = 4 + 2 + 1
        val bytes = ByteArray(payloadSize + 4)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(0x4f485354)
        buffer.putShort(1.toShort())
        buffer.put(1.toByte())
        buffer.putInt(CRC32().apply { update(bytes, 0, payloadSize) }.value.toInt())
        return bytes
    }

    private fun openDatabase() {
        val location = databaseDirectory.toString()
        context = HnswWorkTrackingSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = context,
            addShutdownHook = false,
        ).apply {
            storeType = StoreType.MEMORY_MAPPED_FILE
            maxCardinality = 5_000
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager
        open = true
    }

    private fun closeDatabase() {
        if (open) {
            factory.close()
            open = false
        }
    }

    private companion object {
        const val CALIBRATION_ONE = 73L
        const val CALIBRATION_TWO = 91L
        const val RECALL_K = 10
        const val MIN_HELD_OUT_RECALL = 0.80
    }
}
