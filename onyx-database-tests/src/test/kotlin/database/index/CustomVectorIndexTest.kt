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
    }

    @Test
    fun testSaveEntityWithCustomVectorIndex() {
        val entity = CustomVectorIndexEntity()
        entity.label = "test_custom_vector"
        entity.customVectorData = "This is a test vector string with custom properties"
        
        val savedEntity = manager.saveEntity<IManagedEntity>(entity)
        assertNotNull(savedEntity, "Entity should be saved successfully")
    }

    @Test
    fun testCustomVectorIndexProperties() {
        // Save an entity with custom vector data
        val entity = CustomVectorIndexEntity()
        entity.label = "properties_test"
        entity.customVectorData = "This is a test vector string to verify custom properties"
        
        manager.saveEntity<IManagedEntity>(entity)
        
        // Get the schema context and descriptor to access the index interactor directly
        val context = manager.context
        val descriptor = context.getBaseDescriptorForEntity(CustomVectorIndexEntity::class.java)
        val indexDescriptor = descriptor?.indexes["customVectorData"]
        
        // Verify that the custom properties are correctly set
        assertNotNull(indexDescriptor, "Index descriptor should not be null")
        assertEquals(256, indexDescriptor.embeddingDimensions, "Embedding dimensions should be 256")
        assertEquals(0.25f, indexDescriptor.minimumScore, "Minimum score should be 0.25")
        assertEquals(8, indexDescriptor.hashTableCount, "Hash table count should be 8")
    }
}
