package web.partition

import category.WebServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import entities.partition.BasicPartitionEntity
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters

/**
 * Created by timothy.osborn on 2/12/15.
 */
@Category(WebServerTests::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BasicPartitionTest : BasePartitionTest() {

    @Test
    @Throws(OnyxException::class)
    fun aTestSavePartitionEntityTest() {
        val entity = BasicPartitionEntity()
        entity.partitionId = 2L

        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test
    @Throws(OnyxException::class)
    fun bTestFindPartitionEntityTest() {
        val entity = BasicPartitionEntity()
        entity.partitionId = 1L

        manager.saveEntity<IManagedEntity>(entity)

        val id = entity.id!!

        val entity2Find = BasicPartitionEntity()
        entity2Find.id = id
        entity2Find.partitionId = 1L

        find(entity2Find)

        Assert.assertNotNull(entity2Find)
        Assert.assertTrue(entity2Find.id === id)
        Assert.assertTrue(entity2Find.partitionId === 1L)
    }

    @Test(expected = NoResultsException::class)
    @Throws(OnyxException::class)
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
    @Throws(OnyxException::class)
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

        find(entity2Find)

        Assert.assertNotNull(entity2Find)
        Assert.assertTrue(entity2Find.id === 1L)
        Assert.assertTrue(entity2Find.partitionId === 1L)

        entity2Find = BasicPartitionEntity()
        entity2Find.id = 1L
        entity2Find.partitionId = 2L

        find(entity2Find)

        Assert.assertNotNull(entity2Find)
        Assert.assertTrue(entity2Find.id === 1L)
        Assert.assertTrue(entity2Find.partitionId === 2L)
    }

    @Test
    @Throws(OnyxException::class)
    fun testDeletePartitionEntity() {
        val entity = BasicPartitionEntity()
        entity.partitionId = 1L
        entity.id = 1L

        manager.saveEntity<IManagedEntity>(entity)

        val entity2Find = BasicPartitionEntity()
        entity2Find.id = 1L
        entity2Find.partitionId = 1L

        find(entity2Find)

        manager.deleteEntity(entity)

        var exceptionThrown = false
        try {
            manager.find<IManagedEntity>(entity2Find)
        } catch (e: OnyxException) {
            exceptionThrown = true
        }

        Assert.assertTrue(exceptionThrown)
    }


}
