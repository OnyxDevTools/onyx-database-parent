package database.partition

import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.partition.BasicPartitionEntity
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class BasicPartitionTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun aTestSavePartitionEntityTest() {
        val entity = BasicPartitionEntity()
        entity.partitionId = 2L
        assertTrue(manager.saveEntity(entity).id!! > 0L, "Failed to save entity with partition")
    }

    @Test
    fun bTestFindPartitionEntityTest() {
        val entity = BasicPartitionEntity()
        entity.partitionId = 1L

        manager.saveEntity<IManagedEntity>(entity)

        val id = entity.id!!

        val entity2Find = BasicPartitionEntity()
        entity2Find.id = id
        entity2Find.partitionId = 1L

        manager.find<IManagedEntity>(entity2Find)

        assertNotNull(entity2Find, "Entity should not be null")
        assertEquals(id, entity2Find.id, "id has incorrect value")
        assertEquals(1L, entity2Find.partitionId, "partitionId has incorrect value")
    }

    @Test(expected = NoResultsException::class)
    fun cTestFindPartitionEntityByIDTest() {
        val entity = BasicPartitionEntity()
        entity.partitionId = 2L

        manager.saveEntity<IManagedEntity>(entity)

        val id = entity.id!!

        val entity2Find = BasicPartitionEntity()
        entity2Find.id = id

        manager.find<IManagedEntity>(entity2Find)
    }


    @Test
    fun dTestNonUniqueEntityWithDifferentPartition() {
        val entity = BasicPartitionEntity()
        entity.partitionId = 1L
        entity.id = 1L

        manager.saveEntity<IManagedEntity>(entity)

        val entity2 = BasicPartitionEntity()
        entity2.partitionId = 2L
        entity2.id = 1L

        manager.saveEntity<IManagedEntity>(entity2)

        var entity2Find = BasicPartitionEntity()
        entity2Find.id = 1L
        entity2Find.partitionId = 1L

        manager.find<IManagedEntity>(entity2Find)

        assertNotNull(entity2Find)
        assertEquals(1L, entity2Find.id)
        assertEquals(1L, entity2Find.partitionId)

        entity2Find = BasicPartitionEntity()
        entity2Find.id = 1L
        entity2Find.partitionId = 2L

        manager.find<IManagedEntity>(entity2Find)

        assertNotNull(entity2Find, "Entity should not be null")
        assertEquals(1L, entity2Find.id, "Id has incorrect value")
        assertEquals(2L, entity2Find.partitionId, "partitionId has incorrect value")
    }

    @Test
    fun testDeletePartitionEntity() {
        val entity = BasicPartitionEntity()
        entity.partitionId = 1L
        entity.id = 1L

        manager.saveEntity<IManagedEntity>(entity)

        val entity2Find = BasicPartitionEntity()
        entity2Find.id = 1L
        entity2Find.partitionId = 1L

        manager.find<IManagedEntity>(entity2Find)
        manager.deleteEntity(entity)

        var exceptionThrown = false
        try {
            manager.find<IManagedEntity>(entity2Find)
        } catch (e: NoResultsException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown, "No Results exception should have been thrown")
    }
}
