package database.delete

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.gte
import database.base.DatabaseBaseTest
import entities.delete.TestPartitionEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for partition data file deletion functionality.
 *
 * These tests verify:
 * 1. Partition data files are deleted when deleting all records in a partition
 * 2. Other partitions remain intact when one partition is deleted
 * 3. After partition deletion, we can still save and retrieve data
 */
@RunWith(Parameterized::class)
class PartitionDataFileDeletionTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    /**
     * Test that when deleting all records in a specific partition,
     * the partition's data file is deleted but other partitions remain intact.
     */
    @Test
    fun testPartitionDataFileDeletedWhenDeletingAllPartitionRecords() {
        // Clean up any existing data from previous test runs
        manager.from(TestPartitionEntity::class).delete()

        // Create entities in multiple partitions
        val partition1Entity1 = TestPartitionEntity()
        partition1Entity1.id = "p1-e1"
        partition1Entity1.partitionId = "partition1"
        partition1Entity1.data = "Partition 1 Entity 1"
        manager.saveEntity<IManagedEntity>(partition1Entity1)

        val partition1Entity2 = TestPartitionEntity()
        partition1Entity2.id = "p1-e2"
        partition1Entity2.partitionId = "partition1"
        partition1Entity2.data = "Partition 1 Entity 2"
        manager.saveEntity<IManagedEntity>(partition1Entity2)

        val partition2Entity1 = TestPartitionEntity()
        partition2Entity1.id = "p2-e1"
        partition2Entity1.partitionId = "partition2"
        partition2Entity1.data = "Partition 2 Entity 1"
        manager.saveEntity<IManagedEntity>(partition2Entity1)

        val partition2Entity2 = TestPartitionEntity()
        partition2Entity2.id = "p2-e2"
        partition2Entity2.partitionId = "partition2"
        partition2Entity2.data = "Partition 2 Entity 2"
        manager.saveEntity<IManagedEntity>(partition2Entity2)

        // Verify all entities exist
        val partition1Count = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition1").count()
        val partition2Count = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition2").count()
        assertEquals(2L, partition1Count, "Partition 1 should have 2 entities")
        assertEquals(2L, partition2Count, "Partition 2 should have 2 entities")

        // Delete all records in partition 1
        manager.from(TestPartitionEntity::class).where("partitionId" eq "partition1").delete()

        // Verify partition 1 entities are deleted
        val partition1CountAfterDelete = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition1").count()
        assertEquals(0L, partition1CountAfterDelete, "Partition 1 should have 0 entities")

        // Verify partition 2 entities still exist
        val partition2CountAfterDelete = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition2").count()
        assertEquals(2L, partition2CountAfterDelete, "Partition 2 should still have 2 entities")

        // Verify partition 2 data is intact
        val p2Entity = manager.from(TestPartitionEntity::class).where("id" eq "p2-e1").firstOrNull<TestPartitionEntity>()
        assertTrue(p2Entity != null, "Partition 2 entity should still be accessible")
        assertEquals("Partition 2 Entity 1", p2Entity?.data, "Partition 2 data should be intact")
    }

    /**
     * Test that after deleting all records in a partition,
     * we can still save new data to that partition.
     */
    @Test
    fun testCanSaveToPartitionAfterDelete() {
        // Clean up any existing data from previous test runs
        manager.from(TestPartitionEntity::class).delete()

        // Create initial entity in partition
        val entity = TestPartitionEntity()
        entity.id = "initial"
        entity.partitionId = "test-partition"
        entity.data = "Initial Data"
        manager.saveEntity<IManagedEntity>(entity)

        // Verify entity exists
        val initialCount = manager.from(TestPartitionEntity::class).where("partitionId" eq "test-partition").count()
        assertEquals(1L, initialCount, "Initial entity should exist")

        // Delete all records in the partition
        manager.from(TestPartitionEntity::class).where("partitionId" eq "test-partition").delete()

        // Verify no records in partition
        val countAfterDelete = manager.from(TestPartitionEntity::class).where("partitionId" eq "test-partition").count()
        assertEquals(0L, countAfterDelete, "Partition should have 0 entities after delete")

        // Save new entity to the same partition after delete
        val newEntity = TestPartitionEntity()
        newEntity.id = "new-after-delete"
        newEntity.partitionId = "test-partition"
        newEntity.data = "New Data After Delete"
        manager.saveEntity<IManagedEntity>(newEntity)

        // Verify new entity was saved
        val countAfterSave = manager.from(TestPartitionEntity::class).where("partitionId" eq "test-partition").count()
        assertEquals(1L, countAfterSave, "New entity should be saved to partition")

        // Verify we can retrieve the new entity
        val retrievedEntity = manager.from(TestPartitionEntity::class)
            .where("id" eq "new-after-delete")
            .firstOrNull<TestPartitionEntity>()
        assertTrue(retrievedEntity != null, "Should be able to retrieve new entity")
        assertEquals("new-after-delete", retrievedEntity?.id, "ID should match")
        assertEquals("test-partition", retrievedEntity?.partitionId, "Partition ID should match")
        assertEquals("New Data After Delete", retrievedEntity?.data, "Data should match")
    }

    /**
     * Test that deleting all records across all partitions (without partition filter)
     * deletes all partition data files.
     */
    @Test
    fun testDeleteAllRecordsDeletesAllPartitionFiles() {
        // Clean up any existing data from previous test runs
        manager.from(TestPartitionEntity::class).delete()

        // Create entities in multiple partitions
        for (i in 1..3) {
            val entity = TestPartitionEntity()
            entity.id = "p1-$i"
            entity.partitionId = "partition1"
            entity.data = "Data $i"
            manager.saveEntity<IManagedEntity>(entity)
        }

        for (i in 1..3) {
            val entity = TestPartitionEntity()
            entity.id = "p2-$i"
            entity.partitionId = "partition2"
            entity.data = "Data $i"
            manager.saveEntity<IManagedEntity>(entity)
        }

        // Verify all entities exist
        val totalCount = manager.from(TestPartitionEntity::class).count()
        assertEquals(6L, totalCount, "Should have 6 entities total")

        // Delete all records (no partition filter)
        manager.from(TestPartitionEntity::class).delete()

        // Verify no records remain
        val countAfterDelete = manager.from(TestPartitionEntity::class).count()
        assertEquals(0L, countAfterDelete, "All entities should be deleted")

        // Verify we can save new entities to any partition
        val newEntity1 = TestPartitionEntity()
        newEntity1.id = "new-p1"
        newEntity1.partitionId = "partition1"
        newEntity1.data = "New in partition 1"
        manager.saveEntity<IManagedEntity>(newEntity1)

        val newEntity2 = TestPartitionEntity()
        newEntity2.id = "new-p2"
        newEntity2.partitionId = "partition2"
        newEntity2.data = "New in partition 2"
        manager.saveEntity<IManagedEntity>(newEntity2)

        // Verify both entities exist
        val finalCount = manager.from(TestPartitionEntity::class).count()
        assertEquals(2L, finalCount, "Should have 2 new entities")

        // Verify they are in correct partitions
        val p1Count = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition1").count()
        val p2Count = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition2").count()
        assertEquals(1L, p1Count, "Partition 1 should have 1 entity")
        assertEquals(1L, p2Count, "Partition 2 should have 1 entity")
    }

    /**
     * Test that partial deletes within a partition work correctly
     * and don't affect other partitions.
     */
    @Test
    fun testPartialDeleteWithinPartition() {
        // Clean up any existing data from previous test runs
        manager.from(TestPartitionEntity::class).delete()

        // Create multiple entities in partition 1
        for (i in 1..5) {
            val entity = TestPartitionEntity()
            entity.id = "p1-entity-$i"
            entity.partitionId = "partition1"
            entity.data = "Data $i"
            manager.saveEntity<IManagedEntity>(entity)
        }

        // Create entities in partition 2
        for (i in 1..3) {
            val entity = TestPartitionEntity()
            entity.id = "p2-entity-$i"
            entity.partitionId = "partition2"
            entity.data = "Data $i"
            manager.saveEntity<IManagedEntity>(entity)
        }

        // Delete only entities with id > p1-entity-3 in partition 1
        val deleteCriteria = ("partitionId" eq "partition1")
        val idCriteria = ("id" gte "p1-entity-4")
        manager.from(TestPartitionEntity::class)
            .where(deleteCriteria.and(idCriteria))
            .delete()

        // Verify partition 1 has only 3 entities
        val p1Count = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition1").count()
        assertEquals(3L, p1Count, "Partition 1 should have 3 entities")

        // Verify partition 2 still has all 3 entities
        val p2Count = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition2").count()
        assertEquals(3L, p2Count, "Partition 2 should still have 3 entities")

        // Verify correct entities remain in partition 1
        val remainingIds = manager.from(TestPartitionEntity::class)
            .where("partitionId" eq "partition1")
            .list<TestPartitionEntity>()
            .map { it.id }
            .toSet()
        assertTrue(remainingIds.contains("p1-entity-1"), "p1-entity-1 should remain")
        assertTrue(remainingIds.contains("p1-entity-2"), "p1-entity-2 should remain")
        assertTrue(remainingIds.contains("p1-entity-3"), "p1-entity-3 should remain")

        // Verify we can still save to partition 1
        val newEntity = TestPartitionEntity()
        newEntity.id = "p1-new"
        newEntity.partitionId = "partition1"
        newEntity.data = "New after partial delete"
        manager.saveEntity<IManagedEntity>(newEntity)

        val finalP1Count = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition1").count()
        assertEquals(4L, finalP1Count, "Partition 1 should have 4 entities after save")
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(
            com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory::class
        )
    }
}