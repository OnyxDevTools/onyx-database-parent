package database.index

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.CustomVectorIndexEntity
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Concurrent stress test for VECTOR index integration through the database API.
 * Tests the full persistence flow including save, query, update, and delete operations
 * under concurrent load.
 */
@RunWith(Parameterized::class)
class VectorIndexStressTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun prepare() {
        manager.from<CustomVectorIndexEntity>().delete()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = listOf(EmbeddedPersistenceManagerFactory::class)

        private const val NUM_THREADS = 10
        private const val OPERATIONS_PER_THREAD = 100
        private const val VECTOR_DIMENSIONS = 768

        /**
         * Generate a deterministic vector based on seed
         */
        fun generateVector(seed: Int): FloatArray {
            val r = java.util.Random(seed.toLong())
            return FloatArray(VECTOR_DIMENSIONS) { r.nextGaussian().toFloat() } // mean 0
        }

        /**
         * Generate a similar vector by adding small perturbations
         */
        fun similarVector(base: FloatArray, seed: Int): FloatArray {
            val random = java.util.Random(seed.toLong())
            return FloatArray(base.size) { i -> base[i] + (random.nextFloat() - 0.5f) * 0.1f }
        }
    }

    /**
     * Test concurrent inserts through the database API
     */
    @Test
    fun testConcurrentInserts() {
        val executor = Executors.newFixedThreadPool(NUM_THREADS)
        val latch = CountDownLatch(NUM_THREADS)
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        for (threadId in 0 until NUM_THREADS) {
            executor.submit {
                try {
                    for (i in 0 until OPERATIONS_PER_THREAD) {
                        val entity = CustomVectorIndexEntity()
                        entity.label = "concurrent_insert_${threadId}_$i"
                        entity.customVectorData = generateVector(threadId * OPERATIONS_PER_THREAD + i)
                        
                        manager.saveEntity<IManagedEntity>(entity)
                        successCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "Operations should complete within timeout")
        executor.shutdown()

        val totalExpected = NUM_THREADS * OPERATIONS_PER_THREAD
        println("Concurrent inserts: $successCount successes, $errorCount errors out of $totalExpected")
        
        assertTrue(errorCount.get() == 0, "Should have no errors during concurrent inserts")
        assertTrue(successCount.get() == totalExpected, "All inserts should succeed")

        // Verify count in database
        val count = manager.from<CustomVectorIndexEntity>().count()
        assertTrue(count.toInt() == totalExpected, "Database should contain all inserted entities, found $count")
    }

    /**
     * Test concurrent queries through the database API
     */
    @Test
    fun testConcurrentQueries() {
        // First, insert some data
        val testVectors = mutableListOf<FloatArray>()
        for (i in 0 until 100) {
            val entity = CustomVectorIndexEntity()
            entity.label = "query_test_$i"
            val vector = generateVector(i)
            entity.customVectorData = vector
            testVectors.add(vector)
            manager.saveEntity<IManagedEntity>(entity)
        }

        val executor = Executors.newFixedThreadPool(NUM_THREADS)
        val latch = CountDownLatch(NUM_THREADS)
        val queryCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val emptyResultCount = AtomicInteger(0)
        
        for (threadId in 0 until NUM_THREADS) {
            executor.submit {
                try {
                    for (i in 0 until OPERATIONS_PER_THREAD) {
                        // Query using a random test vector
                        val queryVector = testVectors[i % testVectors.size]
                        
                        val results = manager.from(CustomVectorIndexEntity::class)
                            .where("customVectorData" match queryVector)
                            .limit(10)
                            .list<CustomVectorIndexEntity>()
                        
                        queryCount.incrementAndGet()
                        
                        // Track empty results (LSH is approximate, some may be empty)
                        if (results.isEmpty()) {
                            emptyResultCount.incrementAndGet()
                        }
                    }
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                    println("Thread $threadId error: ${e.message}")
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "Queries should complete within timeout")
        executor.shutdown()

        val totalQueries = NUM_THREADS * OPERATIONS_PER_THREAD
        println("Concurrent queries: $queryCount queries completed, $emptyResultCount empty results, $errorCount errors out of $totalQueries")
        assertTrue(errorCount.get() == 0, "Should have no errors during concurrent queries, got ${errorCount.get()}")
    }

    /**
     * Test mixed concurrent operations: reads and writes together
     */
    @Test
    fun testMixedConcurrentOperations() {
        // Pre-populate some data
        for (i in 0 until 50) {
            val entity = CustomVectorIndexEntity()
            entity.label = "mixed_initial_$i"
            entity.customVectorData = generateVector(i)
            manager.saveEntity<IManagedEntity>(entity)
        }

        val executor = Executors.newFixedThreadPool(NUM_THREADS)
        val latch = CountDownLatch(NUM_THREADS)
        val writeCount = AtomicInteger(0)
        val readCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val idCounter = AtomicLong(1000)

        for (threadId in 0 until NUM_THREADS) {
            executor.submit {
                try {
                    val random = java.util.Random(threadId.toLong())

                    for (i in 0 until OPERATIONS_PER_THREAD) {
                        if (random.nextBoolean()) {
                            // Write operation
                            val entity = CustomVectorIndexEntity()
                            entity.label = "mixed_write_${threadId}_$i"
                            entity.customVectorData = generateVector((threadId * 1000 + i))
                            manager.saveEntity<IManagedEntity>(entity)
                            writeCount.incrementAndGet()
                        } else {
                            // Read operation
                            val queryVector = generateVector(random.nextInt(100))
                            val results = manager.from(CustomVectorIndexEntity::class)
                                .where("customVectorData" match queryVector)
                                .limit(5)
                                .list<CustomVectorIndexEntity>()
                            readCount.incrementAndGet()
                        }
                    }
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                    println("Thread $threadId error: ${e.message}")
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(120, TimeUnit.SECONDS), "Mixed operations should complete within timeout")
        executor.shutdown()

        println("Mixed operations: $writeCount writes, $readCount reads, $errorCount errors")
        assertTrue(errorCount.get() == 0, "Should have no errors during mixed operations, got ${errorCount.get()}")
    }

    /**
     * Test concurrent updates to the same entities
     */
    @Test
    fun testConcurrentUpdates() {
        // Create entities to update
        val entityIds = mutableListOf<Long>()
        for (i in 0 until 20) {
            val entity = CustomVectorIndexEntity()
            entity.label = "update_test_$i"
            entity.customVectorData = generateVector(i)
            manager.saveEntity<IManagedEntity>(entity)
            entityIds.add(entity.id)
        }

        val executor = Executors.newFixedThreadPool(NUM_THREADS)
        val latch = CountDownLatch(NUM_THREADS)
        val updateCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        for (threadId in 0 until NUM_THREADS) {
            executor.submit {
                try {
                    val random = java.util.Random(threadId.toLong())

                    for (i in 0 until OPERATIONS_PER_THREAD / 2) {
                        // Pick a random entity to update
                        val entityId = entityIds[random.nextInt(entityIds.size)]

                        // Fetch the entity
                        val entity =
                            manager.findById<CustomVectorIndexEntity>(CustomVectorIndexEntity::class.java, entityId)
                        if (entity != null) {
                            // Update with new vector
                            entity.customVectorData = generateVector(threadId * 1000 + i)
                            entity.label = "updated_${threadId}_$i"
                            manager.saveEntity<IManagedEntity>(entity)
                            updateCount.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "Updates should complete within timeout")
        executor.shutdown()

        println("Concurrent updates: $updateCount updates, $errorCount errors")
        assertTrue(errorCount.get() == 0, "Should have no errors during concurrent updates")

        // Verify entities still exist and are queryable
        val count = manager.from<CustomVectorIndexEntity>()
            .where("label" startsWith "update")
            .count()
        assertTrue(count > 0, "Updated entities should be queryable")
    }

    /**
     * Test concurrent deletes
     */
    @Test
    fun testConcurrentDeletes() {
        // Create entities to delete
        val entityIds = mutableListOf<Long>()
        for (i in 0 until NUM_THREADS * OPERATIONS_PER_THREAD / 2) {
            val entity = CustomVectorIndexEntity()
            entity.label = "delete_test_$i"
            entity.customVectorData = generateVector(i)
            manager.saveEntity<IManagedEntity>(entity)
            entityIds.add(entity.id)
        }

        val initialCount = manager.from<CustomVectorIndexEntity>().count()
        println("Initial count before deletes: $initialCount")

        val executor = Executors.newFixedThreadPool(NUM_THREADS)
        val latch = CountDownLatch(NUM_THREADS)
        val deleteCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        // Split entity IDs among threads
        val idsPerThread = entityIds.size / NUM_THREADS

        for (threadId in 0 until NUM_THREADS) {
            executor.submit {
                try {
                    val startIdx = threadId * idsPerThread
                    val endIdx = minOf(startIdx + idsPerThread, entityIds.size)

                    for (i in startIdx until endIdx) {
                        val entityId = entityIds[i]
                        val entity =
                            manager.findById<CustomVectorIndexEntity>(CustomVectorIndexEntity::class.java, entityId)
                        if (entity != null) {
                            manager.deleteEntity(entity)
                            deleteCount.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "Deletes should complete within timeout")
        executor.shutdown()

        val finalCount = manager.from<CustomVectorIndexEntity>().count()
        println("Concurrent deletes: $deleteCount deletes, $errorCount errors. Final count: $finalCount")

        assertTrue(errorCount.get() == 0, "Should have no errors during concurrent deletes")
    }

    /**
     * Stress test with high volume of data
     */
    @Test
    fun testHighVolumeInsertAndQuery() {
        val totalEntities = 1000
        val batchSize = 100

        // Insert in batches
        val insertStart = System.currentTimeMillis()
        for (batch in 0 until totalEntities / batchSize) {
            val entities = mutableListOf<IManagedEntity>()
            for (i in 0 until batchSize) {
                val entity = CustomVectorIndexEntity()
                entity.label = "volume_test_${batch * batchSize + i}"
                entity.customVectorData = generateVector(batch * batchSize + i)
                entities.add(entity)
            }
            manager.saveEntities(entities)
        }
        val insertTime = System.currentTimeMillis() - insertStart
        println("Inserted $totalEntities entities in ${insertTime}ms")

        // Verify count
        val count = manager.from<CustomVectorIndexEntity>().count()
        assertTrue(count.toInt() == totalEntities, "Should have $totalEntities entities, found $count")

        // Perform queries
        val queryStart = System.currentTimeMillis()
        val numQueries = 100
        for (i in 0 until numQueries) {
            val queryVector = generateVector(i * 10)
            val results = manager.from(CustomVectorIndexEntity::class)
                .where("customVectorData" match queryVector)
                .limit(10)
                .list<CustomVectorIndexEntity>()

            assertTrue(results.isNotEmpty(), "Query $i should return results")
        }
        val queryTime = System.currentTimeMillis() - queryStart
        println("Executed $numQueries queries in ${queryTime}ms (avg ${queryTime / numQueries}ms per query)")
    }

    /**
     * Bulk ranking quality test using dotProduct ground truth.
     *
     * For each query:
     *  1) Full-scan ALL vectors in memory and compute dotProduct scores (ground truth).
     *  2) Compute the ground-truth rank of each DB-returned item (1 = best).
     *  3) Aggregate metrics like avg GT-rank(DB#1), avg GT-rank(DB topK), recall@K vs GT-topK, and MRR.
     *
     * This is a drop-in replacement test you can paste into VectorIndexStressTest.
     */
    @Test
    @Ignore
    fun testRankingDegradesEveryXItems() {
        // -------------------------
        // Tunables
        // -------------------------
        val totalEntitiesTarget = 100_000_000
        val batchSize = 200

        val checkpointEvery = 1_000   // <--- degrade every X items
        val topK = 20
        val queryCount = 10
        val queryNoiseStd = 0.02f

        // If you don't clear the DB between test runs, this prevents cross-run contamination:
        val runTag = System.nanoTime().toString()
        val labelPrefix = "dot_rank_${runTag}_"

        // DB may return some old labels first (if you didn’t clear), so over-fetch and then filter.
        val extraFetch = 256

        // -------------------------
        // Local types / helpers
        // -------------------------
        data class InMemoryVector(val label: String, val vector: FloatArray)

        fun dotProduct(a: FloatArray, b: FloatArray): Float {
            var sum = 0f
            val n = minOf(a.size, b.size)
            var i = 0
            while (i < n) {
                sum += a[i] * b[i]
                i++
            }
            return sum
        }

        fun normalizeInPlace(v: FloatArray) {
            var mag2 = 0f
            var i = 0
            while (i < v.size) {
                val x = v[i]
                mag2 += x * x
                i++
            }
            if (mag2 <= 0f) return
            val inv = 1.0f / kotlin.math.sqrt(mag2)
            i = 0
            while (i < v.size) {
                v[i] *= inv
                i++
            }
        }

        fun normalizeCopy(v: FloatArray): FloatArray = v.copyOf().also { normalizeInPlace(it) }

        fun addNoiseAndNormalize(base: FloatArray, seed: Long, noiseStd: Float): FloatArray {
            val r = java.util.Random(seed)
            val out = base.copyOf()
            var i = 0
            while (i < out.size) {
                out[i] = out[i] + r.nextGaussian().toFloat() * noiseStd
                i++
            }
            normalizeInPlace(out)
            return out
        }

        // Deterministic mixing constant as signed Long (avoids “value out of range”)
        val MIX = -0x61C8864680B583EBL

        // -------------------------
        // Insert dataset progressively
        // -------------------------
        val inMemoryVectors = ArrayList<InMemoryVector>(totalEntitiesTarget)
        val labelToVector = HashMap<String, FloatArray>(totalEntitiesTarget * 2)

        val insertStart = System.currentTimeMillis()

        var inserted = 0
        while (inserted < totalEntitiesTarget) {
            val toInsert = minOf(batchSize, totalEntitiesTarget - inserted)
            val entities = ArrayList<IManagedEntity>(toInsert)

            var i = 0
            while (i < toInsert) {
                val id = inserted + i
                val label = "$labelPrefix$id"
                val vector = normalizeCopy(generateVector(id))

                val entity = CustomVectorIndexEntity()
                entity.label = label
                entity.customVectorData = vector

                inMemoryVectors.add(InMemoryVector(label, vector))
                labelToVector[label] = vector
                entities.add(entity)

                i++
            }

            manager.saveEntities(entities)
            inserted += toInsert

            // Evaluate at checkpoints (and at the end)
            if (inserted % checkpointEvery == 0 || inserted == totalEntitiesTarget) {
                val elapsed = System.currentTimeMillis() - insertStart
                println("\n=== checkpoint inserted=$inserted / $totalEntitiesTarget (elapsed=${elapsed}ms) ===")

                // -------------------------
                // Evaluate DB vs ground truth at this checkpoint
                // -------------------------
                var sumOverlap = 0.0
                var sumRankFirst = 0.0
                var sumAvgRankList = 0.0
                var sumMRRBest = 0.0

                var emptyDb = 0
                var missingInMemory = 0
                var totalDbTimeMs = 0L

                val stride = (inserted / queryCount).coerceAtLeast(1)

                var qi = 0
                while (qi < queryCount) {
                    val anchorId = (qi * stride) % inserted
                    val selfLabel = "$labelPrefix$anchorId"
                    val anchorVec = labelToVector[selfLabel] ?: run {
                        // Should never happen if our mirror is correct
                        missingInMemory++
                        qi++
                        continue
                    }

                    val queryVector = addNoiseAndNormalize(
                        base = anchorVec,
                        seed = (anchorId.toLong() shl 32) xor MIX xor inserted.toLong(),
                        noiseStd = queryNoiseStd
                    )

                    // --- DB query (timed) ---
                    val t0 = System.currentTimeMillis()
                    val raw = manager.from(CustomVectorIndexEntity::class)
                        .where("customVectorData" match queryVector)
                        .limit(topK)
                        .list<CustomVectorIndexEntity>()
                    val t1 = System.currentTimeMillis()
                    totalDbTimeMs += (t1 - t0)

                    // Filter to THIS RUN only + exclude anchor + distinct + take topK
                    val dbLabels = raw.asSequence()
                        .mapNotNull { it.label }
                        .filter { it.startsWith(labelPrefix, true) }
                        .filter { it != selfLabel }
                        .distinct()
                        .take(topK)
                        .toList()

                    if (dbLabels.isEmpty()) {
                        emptyDb++
                        qi++
                        continue
                    }

                    // Build target list to rank (the returned list)
                    val targets = dbLabels
                    val targetScores = FloatArray(targets.size)
                    val higherCounts = IntArray(targets.size)

                    var ti = 0
                    while (ti < targets.size) {
                        val tv = labelToVector[targets[ti]]
                        if (tv == null) {
                            // If this ever increments, your DB has labels not in your current mirror (contamination).
                            missingInMemory++
                            targetScores[ti] = Float.POSITIVE_INFINITY // forces rank to be worst-ish
                        } else {
                            targetScores[ti] = dotProduct(queryVector, tv)
                        }
                        ti++
                    }

                    // Ground-truth topK (one-pass, O(N*K) insert) + ranks (one-pass)
                    val topLabels = arrayOfNulls<String>(topK)
                    val topScores = FloatArray(topK) { Float.NEGATIVE_INFINITY }

                    fun considerTopK(label: String, score: Float) {
                        if (score <= topScores[topK - 1]) return
                        var p = topK - 1
                        while (p > 0 && score > topScores[p - 1]) {
                            topScores[p] = topScores[p - 1]
                            topLabels[p] = topLabels[p - 1]
                            p--
                        }
                        topScores[p] = score
                        topLabels[p] = label
                    }

                    var vi = 0
                    while (vi < inserted) {
                        val item = inMemoryVectors[vi]
                        if (item.label == selfLabel) { vi++; continue }

                        val s = dotProduct(queryVector, item.vector)

                        // update true topK
                        considerTopK(item.label, s)

                        // update rank counters for returned labels
                        var j = 0
                        while (j < targetScores.size) {
                            // If targetScores[j] was INF due to missing vector, this won't increment (fine)
                            if (s > targetScores[j]) higherCounts[j]++
                            j++
                        }

                        vi++
                    }

                    // Build true topK set
                    val trueTopKSet = HashSet<String>(topK * 2)
                    var k = 0
                    while (k < topK) {
                        val lbl = topLabels[k]
                        if (lbl != null) trueTopKSet.add(lbl)
                        k++
                    }

                    // 1) overlap fraction
                    var overlap = 0
                    for (lbl in dbLabels) if (trueTopKSet.contains(lbl)) overlap++
                    sumOverlap += overlap.toDouble() / dbLabels.size.toDouble()

                    // 2) true rank of DB first result
                    val rankFirst = higherCounts[0] + 1
                    sumRankFirst += rankFirst.toDouble()

                    // 3) average true rank of returned list
                    var sumRanks = 0.0
                    var rj = 0
                    while (rj < higherCounts.size) {
                        sumRanks += (higherCounts[rj] + 1).toDouble()
                        rj++
                    }
                    sumAvgRankList += (sumRanks / higherCounts.size.toDouble())

                    // 4) MRR of true best label
                    val trueBest = topLabels[0]
                    var rr = 0.0
                    if (trueBest != null) {
                        val pos = dbLabels.indexOf(trueBest)
                        if (pos >= 0) rr = 1.0 / (pos + 1).toDouble()
                    }
                    sumMRRBest += rr

                    qi++
                }

                val effective = (queryCount - emptyDb).coerceAtLeast(1)
                println(
                    buildString {
                        appendLine("DotProduct Ground-Truth Ranking Eval (checkpoint)")
                        appendLine("  inserted=$inserted, queryCount=$queryCount (effective=$effective), topK=$topK")
                        appendLine("  queryNoiseStd=$queryNoiseStd")
                        appendLine("  emptyDbResults=$emptyDb")
                        appendLine("  missingInMemoryLabels=$missingInMemory (should be 0; >0 means DB contamination)")
                        appendLine("  avgTopKOverlapFraction: ${sumOverlap / effective.toDouble()}")
                        appendLine("  avgTrueRankOfDatabaseFirstResult: ${sumRankFirst / effective.toDouble()}")
                        appendLine("  avgTrueRankOfDatabaseReturnedList: ${sumAvgRankList / effective.toDouble()}")
                        appendLine("  avgReciprocalRankOfTrueBestLabel: ${sumMRRBest / effective.toDouble()}")
                        appendLine("  Total Database Time: ${totalDbTimeMs}ms")
                        appendLine("  Avg Database Latency: ${totalDbTimeMs.toDouble() / queryCount.toDouble()}ms/query")
                    }
                )
            }
        }
    }

    /**
     * Test query accuracy - verify similar vectors are returned first
     */
    @Test
    fun testQueryAccuracy() {
        // Insert a target vector and several similar/dissimilar vectors
        val targetVector = FloatArray(VECTOR_DIMENSIONS) { 1.0f }

        val targetEntity = CustomVectorIndexEntity()
        targetEntity.label = "target"
        targetEntity.customVectorData = targetVector
        manager.saveEntity<IManagedEntity>(targetEntity)

        // Very similar vector
        val similarEntity = CustomVectorIndexEntity()
        similarEntity.label = "similar"
        similarEntity.customVectorData = FloatArray(VECTOR_DIMENSIONS) { 0.95f + (it % 10) * 0.005f }
        manager.saveEntity<IManagedEntity>(similarEntity)

        // Moderately similar
        val moderateEntity = CustomVectorIndexEntity()
        moderateEntity.label = "moderate"
        moderateEntity.customVectorData = FloatArray(VECTOR_DIMENSIONS) { if (it < 200) 1.0f else 0.5f }
        manager.saveEntity<IManagedEntity>(moderateEntity)

        // Less similar
        val lessEntity = CustomVectorIndexEntity()
        lessEntity.label = "less"
        lessEntity.customVectorData = FloatArray(VECTOR_DIMENSIONS) { if (it < 128) 1.0f else 0.0f }
        manager.saveEntity<IManagedEntity>(lessEntity)

        // Query with target vector
        val results = manager.from(CustomVectorIndexEntity::class)
            .where("customVectorData" match targetVector)
            .limit(10)
            .list<CustomVectorIndexEntity>()

        assertTrue(results.isNotEmpty(), "Should find results")

        // The target should be first (exact match)
        val firstResult = results.first()
        assertTrue(firstResult.label == "target", "Target entity should be first result, got: ${firstResult.label}")
    }
}
