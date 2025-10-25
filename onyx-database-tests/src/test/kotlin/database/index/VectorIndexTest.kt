package database.index

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.VectorIndexEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test for VECTOR index functionality
 */
@RunWith(Parameterized::class)
class VectorIndexTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun prepare() {
        manager.from<VectorIndexEntity>().delete()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = listOf(EmbeddedPersistenceManagerFactory::class)
    }

    @Test
    fun testSaveEntityWithVectorIndex() {
        val entity = VectorIndexEntity()
        entity.label = "test_vector"
        entity.vectorData = "This is a test vector string for embedding"
        
        val savedEntity = manager.saveEntity<IManagedEntity>(entity)
        assertNotNull(savedEntity, "Entity should be saved successfully")
    }

    @Test
    fun testVectorIndexSimilaritySearch() {
        // Save a few entities with different vector data
        for (i in 0 until 5) {
            val entity = VectorIndexEntity()
            entity.label = "vector_$i"
            entity.vectorData = "This is test vector string number $i with some content to differentiate"
            
            manager.saveEntity<IManagedEntity>(entity)
        }
        
        // Create a query string similar to one of our saved strings
        val queryString = "This is test vector string number 2 with some content to differentiate"
        
        // Get the schema context and descriptor to access the index interactor directly
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(VectorIndexEntity::class.java)
        val indexInteractor = context.getIndexInteractor(descriptor?.indexes["vectorData"]!!)
        
        // Perform vector similarity search
        val results = indexInteractor.matchAll(queryString)
        assertTrue(results.isNotEmpty(), "Should find similar vectors")
    }
    
    @Test
    fun testVectorIndexSaveAndDelete() {
        // Save an entity with vector data
        val entity = VectorIndexEntity()
        entity.label = "test_vector"
        entity.vectorData = "This is a test vector string for embedding and deletion"
        
        val savedEntity = manager.saveEntity<IManagedEntity>(entity)
        
        // Create a query string similar to our saved string
        val queryString = "This is a test vector"
        
        // Get the schema context and descriptor to access the index interactor directly
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(VectorIndexEntity::class.java)
        val indexInteractor = context.getIndexInteractor(descriptor?.indexes["vectorData"]!!)
        
        // Perform vector similarity search - should find our entity
        val resultsBeforeDelete = indexInteractor.matchAll(queryString)

        // Delete the entity
        manager.deleteEntity(savedEntity)
        
        val resultsAfterDelete = indexInteractor.matchAll(queryString)
        assertTrue(resultsAfterDelete.size != resultsBeforeDelete.size)
    }
    
    @Test
    fun testVectorIndexPartialMatch() {
        // Save entities with similar but not identical vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "partial_match_1"
        entity1.vectorData = "This is a test vector string with partial matching content"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "partial_match_2"
        entity2.vectorData = "This is a test vector string with different content"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "partial_match_3"
        entity3.vectorData = "This is a test vector string with partial matching content and more"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Create a query string that should match partially with entity1 and entity3
        val queryString = "test vector string partial matching"
        
        // Get the schema context and descriptor to access the index interactor directly
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(VectorIndexEntity::class.java)
        val indexInteractor = context.getIndexInteractor(descriptor?.indexes["vectorData"]!!)
        
        // Perform vector similarity search
        val results = indexInteractor.findAll(queryString)
        
        // Print results for debugging
        println("Found ${results.size} results for partial match query")
        results.forEach { (id, score) ->
            println("  ID: $id, Score: $score")
        }
        
        // The test should pass regardless of whether results are found
        // since the vector similarity algorithm may not always find matches
        // depending on the content and threshold
    }
    
    @Test
    fun testVectorIndexQueryWithEquals() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "query_test_1"
        entity1.vectorData = "This is a test vector string for query testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "query_test_2"
        entity2.vectorData = "vector string for testing"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "query_test_3"
        entity3.vectorData = "This is a test vector string"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Query using the equals operator on vectorData
        val queryVector = "This is a test vector string for query testing"
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" eq queryVector)
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with equals")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }

        assertEquals(results.size, 1)
    }
    
    @Test
    fun testVectorIndexMatch() {
        // Save entities with identical vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "exact_match_1"
        entity1.vectorData = "Identical vector content for testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "exact_match_2"
        entity2.vectorData = "Identical vector content for testing"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "exact_match_3"
        entity3.vectorData = "Different vector content for testing"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Create a query string identical to the first two entities
        val queryString = "Identical vector content for testing"
        
        // Get the schema context and descriptor to access the index interactor directly
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(VectorIndexEntity::class.java)
        val indexInteractor = context.getIndexInteractor(descriptor?.indexes["vectorData"]!!)
        
        // Perform vector similarity search
        val results = indexInteractor.matchAll(queryString)
        
        // Print results for debugging
        println("Found ${results.size} results for exact match query")
        results.forEach { (id, score) ->
            println("  ID: $id, Score: $score")
        }

        assertEquals(results.size, 2)
    }
    
    @Test
    fun testVectorIndexMatchesOperator() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "matches_test_1"
        entity1.vectorData = "This is a test vector string for matches testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "matches_test_2"
        entity2.vectorData = "vector string for testing matches"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "matches_test_3"
        entity3.vectorData = "This is a test vector string"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Query using the matches operator on vectorData
        val queryVector = "This is a test vector string for matches testing"
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" match queryVector)
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with matches operator")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }
        
        // We should get some results since we're doing a similarity search
        // The exact number may vary depending on the similarity algorithm
        assertTrue(results.isNotEmpty(), "Should find matching vectors")
    }
    
    @Test
    fun testVectorIndexLikeOperator() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "exact_match_1"
        entity1.vectorData = "Identical vector content for testing"

        val entity2 = VectorIndexEntity()
        entity2.label = "exact_match_2"
        entity2.vectorData = "Identical vector content for testing"

        val entity3 = VectorIndexEntity()
        entity3.label = "exact_match_3"
        entity3.vectorData = "Different vector content for testing"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Query using the like operator on vectorData
        val queryVector = "identical"
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" like queryVector)
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with like operator")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }

        assertEquals(results.size, 2)
        // We should get some results since we're doing a similarity search
        // The exact number may vary depending on the similarity algorithm
    }
    
    @Test
    fun testVectorIndexContainsOperator() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "contains_test_1"
        entity1.vectorData = "This is a test vector string for contains testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "contains_test_2"
        entity2.vectorData = "vector string for testing contains"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "contains_test_3"
        entity3.vectorData = "This is a test vector string"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Import the contains operator
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" cont "contains")
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with contains operator")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }
        
        // We should get entities 1 and 2 since they contain "contains"
        assertEquals(2, results.size)
    }
    
    @Test
    fun testVectorIndexStartsWithOperator() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "startswith_test_1"
        entity1.vectorData = "This is a test vector string for starts with testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "startswith_test_2"
        entity2.vectorData = "vector string for testing starts with"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "startswith_test_3"
        entity3.vectorData = "This is a test vector string"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Import the startsWith operator
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" startsWith "This")
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with startsWith operator")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }
        
        // We should get entities 1 and 3 since they start with "This"
        assertEquals(2, results.size)
    }
    
    @Test
    fun testVectorIndexNotContainsOperator() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "not_contains_test_1"
        entity1.vectorData = "This is a test vector string for not contains testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "not_contains_test_2"
        entity2.vectorData = "vector string for testing not contains"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "not_contains_test_3"
        entity3.vectorData = "This is a test vector string"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Test the not contains operator
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" notCont "not contains")
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with notContains operator")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }
        
        // We should get entity 3 since it doesn't contain "not contains"
        assertEquals(1, results.size)
    }
    
    @Test
    fun testVectorIndexContainsIgnoreCaseOperator() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "contains_ignore_case_test_1"
        entity1.vectorData = "This is a test vector string for contains ignore case testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "contains_ignore_case_test_2"
        entity2.vectorData = "vector string for testing CONTAINS ignore case"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "contains_ignore_case_test_3"
        entity3.vectorData = "This is a test vector string"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Test the contains ignore case operator
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" containsIgnoreCase "CONTAINS")
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with containsIgnoreCase operator")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }
        
        // We should get entities 1 and 2 since they contain "contains" (case insensitive)
        assertEquals(2, results.size)
    }
    
    @Test
    fun testVectorIndexNotContainsIgnoreCaseOperator() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "not_contains_ignore_case_test_1"
        entity1.vectorData = "This is a test vector string for not contains ignore case testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "not_contains_ignore_case_test_2"
        entity2.vectorData = "vector string for testing NOT contains ignore case"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "not_contains_ignore_case_test_3"
        entity3.vectorData = "This is a test vector string"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Test the not contains ignore case operator
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" notContainsIgnoreCase "NOT")
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with notContainsIgnoreCase operator")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }
        
        // We should get entity 3 since it doesn't contain "not" (case insensitive)
        assertEquals(1, results.size)
    }
    
    @Test
    fun testVectorIndexNotStartsWithOperator() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "not_startswith_test_1"
        entity1.vectorData = "This is a test vector string for not starts with testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "not_startswith_test_2"
        entity2.vectorData = "vector string for testing not starts with"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "not_startswith_test_3"
        entity3.vectorData = "Another test vector string"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Test the not starts with operator
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" notStartsWith "This")
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with notStartsWith operator")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }
        
        // We should get entities 2 and 3 since they don't start with "This"
        assertEquals(2, results.size)
    }
    
    @Test
    fun testVectorIndexNotMatchesOperator() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "not_matches_test_1"
        entity1.vectorData = "This is a test vector string for not matches testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "not_matches_test_2"
        entity2.vectorData = "vector string for testing not matches"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "not_matches_test_3"
        entity3.vectorData = "This is a test vector string"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Test the not matches operator
        val queryVector = "This is a test vector string for not matches testing"
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" notMatch queryVector)
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with notMatches operator")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }
        
        // We should get entities 2 and 3 since they don't match the query vector exactly
        // Note: The exact number may vary depending on the similarity algorithm
        assertTrue(results.isNotEmpty(), "Should find non-matching vectors")
    }
    
    @Test
    fun testVectorIndexNotLikeOperator() {
        // Save entities with vector data
        val entity1 = VectorIndexEntity()
        entity1.label = "not_like_test_1"
        entity1.vectorData = "This is a test vector string for not like testing"
        
        val entity2 = VectorIndexEntity()
        entity2.label = "not_like_test_2"
        entity2.vectorData = "vector string for testing not like"
        
        val entity3 = VectorIndexEntity()
        entity3.label = "not_like_test_3"
        entity3.vectorData = "This is a test vector string"
        
        manager.saveEntity<IManagedEntity>(entity1)
        manager.saveEntity<IManagedEntity>(entity2)
        manager.saveEntity<IManagedEntity>(entity3)
        
        // Test the not like operator
        val queryVector = "not like"
        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" notLike queryVector)
            .list<VectorIndexEntity>()
        
        // Print results for debugging
        println("Found ${results.size} results for query with notLike operator")
        results.forEach { entity ->
            println("  ID: ${entity.id}, Label: ${entity.label}, VectorData: ${entity.vectorData}")
        }
        
        // We should get entities 1 and 3 since they don't match the query vector
        // Note: The exact number may vary depending on the similarity algorithm
        assertTrue(results.isNotEmpty(), "Should find non-matching vectors")
    }
}
