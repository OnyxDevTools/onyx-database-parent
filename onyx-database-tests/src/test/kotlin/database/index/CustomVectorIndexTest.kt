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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test for custom VECTOR index functionality with custom properties
 */
@RunWith(Parameterized::class)
class CustomVectorIndexTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun prepare() {
        manager.from<CustomVectorIndexEntity>().delete()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = listOf(EmbeddedPersistenceManagerFactory::class)

        /**
         * Generate a random vector of given size
         */
        fun randomVector(size: Int, seed: Int = 0): FloatArray {
            val random = java.util.Random(seed.toLong())
            return FloatArray(size) { random.nextFloat() }
        }

        /**
         * Generate a similar vector by adding small noise
         */
        fun similarVector(base: FloatArray, noiseLevel: Float = 0.1f, seed: Int = 0): FloatArray {
            val random = java.util.Random(seed.toLong())
            return FloatArray(base.size) { i -> base[i] + (random.nextFloat() - 0.5f) * noiseLevel }
        }

        /**
         * Generate a completely different vector
         */
        fun differentVector(size: Int, seed: Int = 999): FloatArray {
            val random = java.util.Random(seed.toLong())
            return FloatArray(size) { random.nextFloat() * 2 - 1 } // Different range
        }
    }

    @Test
    fun testSaveEntityWithCustomVectorIndex() {
        val entity = CustomVectorIndexEntity()
        entity.label = "test_custom_vector"
        entity.customVectorData = randomVector(256)

        val savedEntity = manager.saveEntity<IManagedEntity>(entity)
        assertNotNull(savedEntity, "Entity should be saved successfully")
        assertTrue(entity.id > 0, "Entity should have been assigned an ID")
    }

    @Test
    fun testCustomVectorIndexProperties() {
        // Save an entity with custom vector data
        val entity = CustomVectorIndexEntity()
        entity.label = "properties_test"
        entity.customVectorData = randomVector(256)

        manager.saveEntity<IManagedEntity>(entity)

        // Get the schema context and descriptor to access the index interactor directly
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(CustomVectorIndexEntity::class.java)
        val indexDescriptor = descriptor?.indexes["customVectorData"]

        // Verify that the custom properties are correctly set
        assertNotNull(indexDescriptor, "Index descriptor should not be null")
        assertEquals(768, indexDescriptor.embeddingDimensions, "Embedding dimensions should be 768")
        assertTrue(indexDescriptor.minimumScore == 0.0f, "Minimum score should be 0.0")
    }

    @Test
    fun testVectorSimilaritySearch() {
        // Create a base vector
        val baseVector = randomVector(768, seed = 42)

        // Save entity with the base vector
        val entity1 = CustomVectorIndexEntity()
        entity1.label = "base_vector"
        entity1.customVectorData = baseVector
        manager.saveEntity<IManagedEntity>(entity1)

        // Save entity with a similar vector (should match)
        val entity2 = CustomVectorIndexEntity()
        entity2.label = "similar_vector"
        entity2.customVectorData = similarVector(baseVector, noiseLevel = 0.05f, seed = 1)
        manager.saveEntity<IManagedEntity>(entity2)

        // Save entity with a different vector (may not match due to minimumScore)
        val entity3 = CustomVectorIndexEntity()
        entity3.label = "different_vector"
        entity3.customVectorData = differentVector(768, seed = 999)
        manager.saveEntity<IManagedEntity>(entity3)

        // Use the index interactor to perform similarity search
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(CustomVectorIndexEntity::class.java)
        val indexInteractor = context.getIndexInteractor(descriptor!!.indexes["customVectorData"]!!)

        // Search for vectors similar to the base vector
        val results = indexInteractor.matchAll(baseVector, limit = 10, maxCandidates = 100)

        // Verify results
        assertTrue(results.isNotEmpty(), "Should find at least one matching vector")

        // The base vector should have perfect similarity (score = 1.0)
        val scores = results.values.filterIsInstance<Float>()
        assertTrue(scores.any { it > 0.99f }, "Should have at least one near-perfect match (the original)")
    }

    @Test
    fun testExactVectorMatch() {
        // Create and save an entity with a specific vector
        val testVector = FloatArray(768) { i -> i.toFloat() / 256f }

        val entity = CustomVectorIndexEntity()
        entity.label = "exact_match_test"
        entity.customVectorData = testVector
        manager.saveEntity<IManagedEntity>(entity)

        // Use the index interactor to search
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(CustomVectorIndexEntity::class.java)
        val indexInteractor = context.getIndexInteractor(descriptor!!.indexes["customVectorData"]!!)

        // Search with the same vector
        val results = indexInteractor.matchAll(testVector, limit = 5, maxCandidates = 100)

        // Should find the exact match
        assertTrue(results.isNotEmpty(), "Should find the exact match")

        // The score should be 1.0 (or very close) for identical vectors
        val topScore = results.values.first() as Float
        assertTrue(topScore > 0.999f, "Exact match should have score close to 1.0, got $topScore")
    }

    @Test
    fun testMinimumScoreFiltering() {
        // Create vectors with known similarity levels
        val baseVector = FloatArray(256) { 1.0f } // Unit vector in all dimensions

        // Save the base entity
        val entity1 = CustomVectorIndexEntity()
        entity1.label = "base"
        entity1.customVectorData = baseVector
        manager.saveEntity<IManagedEntity>(entity1)

        // Save an entity with orthogonal vector (similarity should be low)
        val orthogonalVector = FloatArray(256) { i -> if (i % 2 == 0) 1.0f else -1.0f }
        val entity2 = CustomVectorIndexEntity()
        entity2.label = "orthogonal"
        entity2.customVectorData = orthogonalVector
        manager.saveEntity<IManagedEntity>(entity2)

        // Use the index interactor
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(CustomVectorIndexEntity::class.java)
        val indexInteractor = context.getIndexInteractor(descriptor!!.indexes["customVectorData"]!!)

        // Search - the minimumScore is set to 0.25
        val results = indexInteractor.matchAll(baseVector, limit = 10, maxCandidates = 100)

        // Verify all returned scores are above the minimum score threshold
        results.values.filterIsInstance<Float>().forEach { score ->
            assertTrue(score >= 0.25f, "All returned scores should be >= minimumScore (0.25), got $score")
        }
    }

    @Test
    fun testVectorIndexRebuild() {
        // Save some entities
        for (i in 0 until 5) {
            val entity = CustomVectorIndexEntity()
            entity.label = "rebuild_test_$i"
            entity.customVectorData = randomVector(768, seed = i)
            manager.saveEntity<IManagedEntity>(entity)
        }

        // Get the index interactor and rebuild
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(CustomVectorIndexEntity::class.java)
        val indexInteractor = context.getIndexInteractor(descriptor!!.indexes["customVectorData"]!!)

        // Rebuild the index
        indexInteractor.rebuild()

        // Verify the index still works after rebuild
        val queryVector = randomVector(768, seed = 0) // Same as first entity
        val results = indexInteractor.matchAll(queryVector, limit = 5, maxCandidates = 100)

        assertTrue(results.isNotEmpty(), "Index should return results after rebuild")
    }

    @Test
    fun testVectorIndexClear() {
        // Save some entities
        for (i in 0 until 3) {
            val entity = CustomVectorIndexEntity()
            entity.label = "clear_test_$i"
            entity.customVectorData = randomVector(256, seed = i)
            manager.saveEntity<IManagedEntity>(entity)
        }

        // Get the index interactor
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(CustomVectorIndexEntity::class.java)
        val indexInteractor = context.getIndexInteractor(descriptor!!.indexes["customVectorData"]!!)

        // Clear the index
        indexInteractor.clear()

        // Verify the index is empty
        val queryVector = randomVector(256, seed = 0)
        val results = indexInteractor.matchAll(queryVector, limit = 5, maxCandidates = 100)

        assertTrue(results.isEmpty(), "Index should be empty after clear")
    }

    @Test
    fun testCosineSimilarityOrdering() {
        // Create vectors with known similarity levels to a query vector
        val queryVector = FloatArray(256) { 1.0f }

        // Very similar vector (all positive)
        val highSimilarityVector = FloatArray(256) { 0.9f + (it % 10) * 0.01f }
        val entity1 = CustomVectorIndexEntity()
        entity1.label = "high_similarity"
        entity1.customVectorData = highSimilarityVector
        manager.saveEntity<IManagedEntity>(entity1)

        // Moderately similar vector
        val mediumSimilarityVector = FloatArray(256) { if (it < 200) 1.0f else 0.5f }
        val entity2 = CustomVectorIndexEntity()
        entity2.label = "medium_similarity"
        entity2.customVectorData = mediumSimilarityVector
        manager.saveEntity<IManagedEntity>(entity2)

        // Use the index interactor
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(CustomVectorIndexEntity::class.java)
        val indexInteractor = context.getIndexInteractor(descriptor!!.indexes["customVectorData"]!!)

        // Search
        val results = indexInteractor.matchAll(queryVector, limit = 10, maxCandidates = 100)

        // Verify results are ordered by similarity (descending)
        val scores = results.values.filterIsInstance<Float>()
        for (i in 0 until scores.size - 1) {
            assertTrue(scores[i] >= scores[i + 1], "Results should be ordered by descending similarity")
        }
    }
}
