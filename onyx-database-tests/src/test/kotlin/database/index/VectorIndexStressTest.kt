package database.index

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.CustomVectorIndexEntity
import org.junit.Before
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
        private const val VECTOR_DIMENSIONS = 256

        /**
         * Generate a deterministic vector based on seed
         */
        fun generateVector(seed: Int): FloatArray {
            val random = java.util.Random(seed.toLong())
            return FloatArray(VECTOR_DIMENSIONS) { random.nextFloat() }
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
                        val entity = manager.findById<CustomVectorIndexEntity>(CustomVectorIndexEntity::class.java, entityId)
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
                        val entity = manager.findById<CustomVectorIndexEntity>(CustomVectorIndexEntity::class.java, entityId)
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
