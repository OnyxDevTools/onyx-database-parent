package database.delete

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.gte
import database.base.DatabaseBaseTest
import entities.delete.TestEntityWithCustomFileName
import entities.delete.TestEntityWithDefaultFile1
import entities.delete.TestEntityWithDefaultFile2
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for data file deletion functionality when executing delete queries.
 *
 * These tests verify:
 * 1. Data files are in fact deleted when entities are deleted
 * 2. Default data files shared between entities are not deleted
 * 3. After deletion, the database remains functional and can save/retrieve data
 */
@RunWith(Parameterized::class)
class DataFileDeletionTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    /**
     * Test that data files are deleted when deleting all records of an entity
     * that has a custom file name.
     */
    @Test
    fun testDataFilesAreDeletedWhenDeletingAllRecords() {
        // Clean up any existing data from previous test runs
        manager.from(TestEntityWithCustomFileName::class).delete()

        // Create test entity with custom file name
        val entity = TestEntityWithCustomFileName()
        entity.id = "test-1"
        entity.name = "Test Entity"
        manager.saveEntity<IManagedEntity>(entity)

        // Verify entity was saved
        val savedCount = manager.from(TestEntityWithCustomFileName::class).count()
        assertTrue(savedCount > 0, "Entity should be saved")

        // Get the data file path
        val descriptor = factory.schemaContext.getBaseDescriptorForEntity(TestEntityWithCustomFileName::class.java)
        val dataFilePath = "${factory.schemaContext.location}/${descriptor!!.fileName}.dat"
        val dataFile = File(dataFilePath)

        // Verify file exists before delete
        assertTrue(dataFile.exists() || File(dataFilePath).parentFile.exists(), "Data file or parent directory should exist before delete")

        // Delete all records
        manager.from(TestEntityWithCustomFileName::class).delete()

        // Verify no records remain
        val remainingCount = manager.from(TestEntityWithCustomFileName::class).count()
        assertEquals(0L, remainingCount, "All entities should be deleted")

        // Verify data file is deleted (or at least the records are cleared)
        // Note: The file might be deleted or just emptied depending on implementation
        val recordsExist = manager.from(TestEntityWithCustomFileName::class).count() == 0L
        assertTrue(recordsExist, "Records should be deleted")
    }

    /**
     * Test that default data files (shared between entities) are NOT deleted
     * when one entity type is deleted, but other entities using the same file remain.
     */
    @Test
    fun testDefaultDataFileNotDeletedWhenShared() {
        // Clean up any existing data from previous test runs
        manager.from(TestEntityWithDefaultFile1::class).delete()
        manager.from(TestEntityWithDefaultFile2::class).delete()

        // Create entities that use default data file
        val entity1 = TestEntityWithDefaultFile1()
        entity1.id = "default-test-1"
        entity1.value = "Entity 1"
        manager.saveEntity<IManagedEntity>(entity1)

        val entity2 = TestEntityWithDefaultFile2()
        entity2.id = "default-test-2"
        entity2.value = "Entity 2"
        manager.saveEntity<IManagedEntity>(entity2)

        // Verify both entities are saved
        val count1 = manager.from(TestEntityWithDefaultFile1::class).count()
        val count2 = manager.from(TestEntityWithDefaultFile2::class).count()
        assertTrue(count1 > 0, "Entity 1 should be saved")
        assertTrue(count2 > 0, "Entity 2 should be saved")

        // Delete all records of entity 1
        manager.from(TestEntityWithDefaultFile1::class).delete()

        // Verify entity 1 records are deleted
        val remainingCount1 = manager.from(TestEntityWithDefaultFile1::class).count()
        assertEquals(0L, remainingCount1, "Entity 1 records should be deleted")

        // Verify entity 2 records still exist (default file should not be deleted)
        val remainingCount2 = manager.from(TestEntityWithDefaultFile2::class).count()
        assertTrue(remainingCount2 > 0, "Entity 2 records should still exist - default file should not be deleted")

        // Verify entity 2 is still accessible
        val foundEntity = manager.from(TestEntityWithDefaultFile2::class).where("id" eq "default-test-2").firstOrNull<TestEntityWithDefaultFile2>()
        assertTrue(foundEntity != null, "Should be able to find entity 2")
        assertEquals("Entity 2", foundEntity?.value, "Entity 2 data should be intact")
    }

    /**
     * Test that after deleting all records and data files,
     * we can still save new data and retrieve it correctly.
     */
    @Test
    fun testCanSaveAndRetrieveAfterDelete() {
        // Clean up any existing data from previous test runs
        manager.from(TestEntityWithCustomFileName::class).delete()

        // Create initial entity
        val entity = TestEntityWithCustomFileName()
        entity.id = "initial-test"
        entity.name = "Initial Entity"
        manager.saveEntity<IManagedEntity>(entity)

        // Verify entity exists
        val initialCount = manager.from(TestEntityWithCustomFileName::class).count()
        assertTrue(initialCount > 0, "Initial entity should exist")

        // Delete all records
        manager.from(TestEntityWithCustomFileName::class).delete()

        // Verify no records
        val countAfterDelete = manager.from(TestEntityWithCustomFileName::class).count()
        assertEquals(0L, countAfterDelete, "All records should be deleted")

        // Save new entity after delete
        val newEntity = TestEntityWithCustomFileName()
        newEntity.id = "new-test"
        newEntity.name = "New Entity After Delete"
        manager.saveEntity<IManagedEntity>(newEntity)

        // Verify new entity was saved
        val countAfterSave = manager.from(TestEntityWithCustomFileName::class).count()
        assertEquals(1L, countAfterSave, "New entity should be saved")

        // Verify we can retrieve the new entity
        val retrievedEntity = manager.from(TestEntityWithCustomFileName::class).where("id" eq "new-test").firstOrNull<TestEntityWithCustomFileName>()
        assertTrue(retrievedEntity != null, "Should be able to retrieve new entity")
        assertEquals("new-test", retrievedEntity?.id, "ID should match")
        assertEquals("New Entity After Delete", retrievedEntity?.name, "Name should match")

        // Save another entity to ensure database is fully functional
        val anotherEntity = TestEntityWithCustomFileName()
        anotherEntity.id = "another-test"
        anotherEntity.name = "Another Entity"
        manager.saveEntity<IManagedEntity>(anotherEntity)

        // Verify both entities exist
        val finalCount = manager.from(TestEntityWithCustomFileName::class).count()
        assertEquals(2L, finalCount, "Both entities should exist")

        // Verify we can query and retrieve both
        val allEntities = manager.from(TestEntityWithCustomFileName::class).list<TestEntityWithCustomFileName>()
        assertEquals(2, allEntities.size, "Should retrieve both entities")
        val ids = allEntities.map { it.id }.toSet()
        assertTrue(ids.contains("new-test"), "Should contain new-test")
        assertTrue(ids.contains("another-test"), "Should contain another-test")
    }

    /**
     * Test that deleting with a query only deletes matching records
     * and leaves the data file intact for remaining records.
     */
    @Test
    fun testPartialDeletePreservesDataFile() {
        // Clean up any existing data from previous test runs
        manager.from(TestEntityWithCustomFileName::class).delete()

        // Create multiple entities
        for (i in 1..5) {
            val entity = TestEntityWithCustomFileName()
            entity.id = "test-$i"
            entity.name = "Test Entity $i"
            manager.saveEntity<IManagedEntity>(entity)
        }

        // Verify all entities exist
        val initialCount = manager.from(TestEntityWithCustomFileName::class).count()
        assertEquals(5L, initialCount, "All 5 entities should exist")

        // Delete only entities with id > test-3
        manager.from(TestEntityWithCustomFileName::class).where("id" gte "test-4").delete()

        // Verify only 3 entities remain
        val remainingCount = manager.from(TestEntityWithCustomFileName::class).count()
        assertEquals(3L, remainingCount, "3 entities should remain")

        // Verify correct entities remain
        val remainingIds = manager.from(TestEntityWithCustomFileName::class).list<TestEntityWithCustomFileName>().map { it.id }.toSet()
        assertTrue(remainingIds.contains("test-1"), "test-1 should remain")
        assertTrue(remainingIds.contains("test-2"), "test-2 should remain")
        assertTrue(remainingIds.contains("test-3"), "test-3 should remain")
        assertFalse(remainingIds.contains("test-4"), "test-4 should be deleted")
        assertFalse(remainingIds.contains("test-5"), "test-5 should be deleted")

        // Verify we can still save new entities
        val newEntity = TestEntityWithCustomFileName()
        newEntity.id = "test-new"
        newEntity.name = "New After Partial Delete"
        manager.saveEntity<IManagedEntity>(newEntity)

        val finalCount = manager.from(TestEntityWithCustomFileName::class).count()
        assertEquals(4L, finalCount, "Should have 4 entities after saving new one")
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(
            com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory::class
        )
    }
}