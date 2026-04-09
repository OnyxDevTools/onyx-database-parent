package database.partition

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import database.base.DatabaseBaseTest
import entities.delete.TestPartitionEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Test to verify the bug where using inPartition().delete() deletes ALL partitions
 * instead of just the specified partition.
 */
@RunWith(Parameterized::class)
class PartitionDeleteBugTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    /**
     * This test demonstrates the bug where manager.from<TestPartitionEntity>()
     * .inPartition("partition1").delete() deletes ALL partitions instead of just partition1.
     */
    @Test
    fun testInPartitionDeleteShouldOnlyDeleteSpecifiedPartition() {
        // Clean up any existing data
        manager.from(TestPartitionEntity::class).delete()

        // Create entities in partition 1
        val p1Entity1 = TestPartitionEntity()
        p1Entity1.id = "p1-e1"
        p1Entity1.partitionId = "partition1"
        p1Entity1.data = "Partition 1 Entity 1"
        manager.saveEntity<IManagedEntity>(p1Entity1)

        val p1Entity2 = TestPartitionEntity()
        p1Entity2.id = "p1-e2"
        p1Entity2.partitionId = "partition1"
        p1Entity2.data = "Partition 1 Entity 2"
        manager.saveEntity<IManagedEntity>(p1Entity2)

        // Create entities in partition 2
        val p2Entity1 = TestPartitionEntity()
        p2Entity1.id = "p2-e1"
        p2Entity1.partitionId = "partition2"
        p2Entity1.data = "Partition 2 Entity 1"
        manager.saveEntity<IManagedEntity>(p2Entity1)

        val p2Entity2 = TestPartitionEntity()
        p2Entity2.id = "p2-e2"
        p2Entity2.partitionId = "partition2"
        p2Entity2.data = "Partition 2 Entity 2"
        manager.saveEntity<IManagedEntity>(p2Entity2)

        // Create entities in partition 3
        val p3Entity1 = TestPartitionEntity()
        p3Entity1.id = "p3-e1"
        p3Entity1.partitionId = "partition3"
        p3Entity1.data = "Partition 3 Entity 1"
        manager.saveEntity<IManagedEntity>(p3Entity1)

        // Verify all entities exist
        val totalBeforeDelete = manager.from(TestPartitionEntity::class).count()
        assertEquals(5L, totalBeforeDelete, "Should have 5 entities total")

        val p1CountBefore = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition1").count()
        val p2CountBefore = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition2").count()
        val p3CountBefore = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition3").count()
        assertEquals(2L, p1CountBefore, "Partition 1 should have 2 entities")
        assertEquals(2L, p2CountBefore, "Partition 2 should have 2 entities")
        assertEquals(1L, p3CountBefore, "Partition 3 should have 1 entity")

        // THIS IS THE BUG: using inPartition should only delete partition1, not all partitions
        manager.from<TestPartitionEntity>().inPartition("partition1").delete()

        // Verify only partition1 entities are deleted
        val p1CountAfter = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition1").count()
        val p2CountAfter = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition2").count()
        val p3CountAfter = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition3").count()

        assertEquals(0L, p1CountAfter, "Partition 1 should have 0 entities after delete")
        assertEquals(2L, p2CountAfter, "Partition 2 should still have 2 entities - BUG: it has $p2CountAfter")
        assertEquals(1L, p3CountAfter, "Partition 3 should still have 1 entity - BUG: it has $p3CountAfter")

        val totalAfterDelete = manager.from(TestPartitionEntity::class).count()
        assertEquals(3L, totalAfterDelete, "Should have 3 entities total after deleting only partition 1")
    }

    /**
     * Test that inPartition with a where clause also only deletes the specified partition
     */
    @Test
    fun testInPartitionDeleteWithCriteriaShouldOnlyDeleteSpecifiedPartition() {
        // Clean up any existing data
        manager.from(TestPartitionEntity::class).delete()

        // Create entities in partition 1
        for (i in 1..3) {
            val entity = TestPartitionEntity()
            entity.id = "p1-e$i"
            entity.partitionId = "partition1"
            entity.data = "Data $i"
            manager.saveEntity<IManagedEntity>(entity)
        }

        // Create entities in partition 2
        for (i in 1..3) {
            val entity = TestPartitionEntity()
            entity.id = "p2-e$i"
            entity.partitionId = "partition2"
            entity.data = "Data $i"
            manager.saveEntity<IManagedEntity>(entity)
        }

        // Delete specific entity in partition 1 only
        manager.from<TestPartitionEntity>()
            .inPartition("partition1")
            .where("id" eq "p1-e1")
            .delete()

        // Verify only p1-e1 is deleted
        val p1Count = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition1").count()
        val p2Count = manager.from(TestPartitionEntity::class).where("partitionId" eq "partition2").count()

        assertEquals(2L, p1Count, "Partition 1 should have 2 entities remaining")
        assertEquals(3L, p2Count, "Partition 2 should still have all 3 entities")
    }

    /**
     * Test that delete without inPartition (default query) deletes all partitions
     * This is the expected behavior.
     */
    @Test
    fun testDeleteWithoutPartitionShouldDeleteAllPartitions() {
        // Clean up any existing data
        manager.from(TestPartitionEntity::class).delete()

        // Create entities in multiple partitions
        for (i in 1..2) {
            val entity = TestPartitionEntity()
            entity.id = "p1-e$i"
            entity.partitionId = "partition1"
            entity.data = "Data $i"
            manager.saveEntity<IManagedEntity>(entity)
        }

        for (i in 1..2) {
            val entity = TestPartitionEntity()
            entity.id = "p2-e$i"
            entity.partitionId = "partition2"
            entity.data = "Data $i"
            manager.saveEntity<IManagedEntity>(entity)
        }

        // Delete all without partition filter
        manager.from<TestPartitionEntity>().delete()

        // Verify all entities are deleted
        val totalCount = manager.from(TestPartitionEntity::class).count()
        assertEquals(0L, totalCount, "All entities should be deleted")
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(
            com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory::class
        )
    }
}