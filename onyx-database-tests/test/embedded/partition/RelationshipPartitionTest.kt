package embedded.partition

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import entities.partition.*
import junit.framework.Assert
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters

import java.util.ArrayList

/**
 * Created by timothy.osborn on 2/12/15.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(EmbeddedDatabaseTests::class)
class RelationshipPartitionTest : BasePartitionTest() {

    @Test
    @Throws(OnyxException::class)
    fun testSaveEntityWithRelationshipToOne() {
        val parent = ToOnePartitionEntityParent()
        parent.id = 4L
        parent.partitionId = 20L

        parent.child = ToOnePartitionEntityChild()
        parent.child!!.id = 5L

        save(parent.child!!)
        save(parent)

        val parentToFind = ToOnePartitionEntityParent()
        parentToFind.id = 4L
        parentToFind.partitionId = 20L

        find(parentToFind)

        Assert.assertNotNull(parentToFind.child)
        Assert.assertTrue(parentToFind.child!!.id === parent.child!!.id)


        val childToFind = ToOnePartitionEntityChild()
        childToFind.id = 5L

        find(childToFind)

        Assert.assertNotNull(childToFind.parent)
        Assert.assertTrue(childToFind.parent!!.id === parent.id)
    }

    @Test(expected = NoResultsException::class)
    @Throws(OnyxException::class)
    fun testDeleteEntityWithRelationshipToOne() {
        testSaveEntityWithRelationshipToOne()

        val parent = ToOnePartitionEntityParent()
        parent.id = 4L
        parent.partitionId = 20L

        find(parent)

        delete(parent)

        manager.find<IManagedEntity>(parent.child!!)
    }

    @Ignore
    @Test(expected = NoResultsException::class)
    @Throws(OnyxException::class)
    fun testUpdateEntityWithRelationshipToOne() {
        testSaveEntityWithRelationshipToOne()
        testSaveEntityWithRelationshipToOne()

        val parent = ToOnePartitionEntityParent()
        parent.id = 4L
        parent.partitionId = 20L

        find(parent)

        parent.child!!.partitionId = 34L
        save(parent.child!!)

        find(parent)

        org.junit.Assert.assertNotNull(parent.child)
        Assert.assertTrue(parent.child!!.partitionId === 34L)

        val child = ToOnePartitionEntityChild()
        child.id = parent.child!!.id
        child.partitionId = 34L

        manager.find<IManagedEntity>(child)
    }

    @Test
    @Throws(OnyxException::class)
    fun testSaveEntityAsRelationshipOneToMany() {
        var parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ManyToOnePartitionEntity()
        parent.child!!.id = 32L

        save(parent)

        parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L

        find(parent)

        Assert.assertNotNull(parent.child)
        Assert.assertTrue(parent.child!!.id === 32L)

        find(parent.child!!)

        org.junit.Assert.assertTrue(parent.child!!.parents!!.size == 1)
        org.junit.Assert.assertTrue(parent.child!!.parents!![0].id === 23L)
    }

    @Test
    @Throws(OnyxException::class)
    fun testDeleteEntityAsRelationshipOneToMany() {
        var parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ManyToOnePartitionEntity()
        parent.child!!.id = 32L

        save(parent)

        parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L

        delete(parent)

        parent.child = ManyToOnePartitionEntity()
        parent.child!!.id = 32L

        find(parent.child!!)

        org.junit.Assert.assertTrue(parent.child!!.parents!!.size == 0)

        var failedToFind = false
        try {
            manager.find<IManagedEntity>(parent)
        } catch (e: OnyxException) {
            failedToFind = true
        }

        Assert.assertTrue(failedToFind)
    }

    @Test
    @Throws(OnyxException::class)
    fun testUpdateEntityAsRelationshipOneToMany() {
        var parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ManyToOnePartitionEntity()
        parent.child!!.id = 32L

        save(parent)

        parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ManyToOnePartitionEntity()
        parent.child!!.id = 32L

        find(parent.child!!)

        parent.child!!.partitionId = 2L

        save(parent.child!!)

        find(parent)


        Assert.assertNotNull(parent.child)
        Assert.assertTrue(parent.child!!.id === 32L)
        Assert.assertTrue(parent.child!!.partitionId === 2L)

        find(parent.child!!)

        org.junit.Assert.assertTrue(parent.child!!.parents!!.size == 1)
        org.junit.Assert.assertTrue(parent.child!!.parents!![0].id === 23L)
        org.junit.Assert.assertTrue(parent.child!!.parents!![0].partitionId === 3L)

    }

    @Test
    @Throws(OnyxException::class)
    fun testSaveEntityWithRelationshipManyToMany() {
        var parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ArrayList()

        var child = ToManyPartitionEntityChild()
        parent.child!!.add(child)

        child.id = 33L

        save(parent)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        find(parent)

        Assert.assertNotNull(parent.child)
        Assert.assertTrue(parent.child!!.size == 1)

        child = find(parent.child!![0]) as ToManyPartitionEntityChild

        org.junit.Assert.assertTrue(child.parent!!.size == 1)
        org.junit.Assert.assertTrue(child.parent!![0].id === 23L)
    }

    @Test
    @Throws(OnyxException::class)
    fun testDeleteEntityWithRelationshipManyToMany() {
        var parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ArrayList()

        var child = ToManyPartitionEntityChild()
        parent.child!!.add(child)

        child.id = 32L

        save(parent)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        find(parent)

        Assert.assertNotNull(parent.child)
        Assert.assertTrue(parent.child!!.size == 1)

        child = manager.find<ToManyPartitionEntityChild>(parent.child!![0])

        org.junit.Assert.assertTrue(child.parent!!.size == 1)
        org.junit.Assert.assertTrue(child.parent!![0].id === 23L)


        parent.child!!.clear()
        save(parent)


        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        find(parent)

        Assert.assertNotNull(parent.child)
        Assert.assertTrue(parent.child!!.size == 0)

        child = ToManyPartitionEntityChild()
        child.id = 32L
        find(child)

        org.junit.Assert.assertTrue(child.parent!!.size == 0)
    }

    @Test
    @Throws(OnyxException::class)
    fun testUpdateEntityWithRelationshipManyToMany() {
        var parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ArrayList()

        var child = ToManyPartitionEntityChild()
        parent.child!!.add(child)

        child.id = 34L

        save(parent)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        find(parent)

        Assert.assertNotNull(parent.child)
        Assert.assertTrue(parent.child!!.size == 1)

        find(parent.child!![0])

        org.junit.Assert.assertTrue(parent.child!![0].parent!!.size == 1)
        org.junit.Assert.assertTrue(parent.child!![0].parent!![0].id === 23L)


        child = parent.child!![0]
        manager.initialize(child, "parent")
        child.partitionId = 3L
        save(child)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        find(parent)
        initialize(parent, "child")

        Assert.assertNotNull(parent.child)
        Assert.assertTrue(parent.child!!.size == 1)
        Assert.assertTrue(parent.child!![0].partitionId === 3L)
    }

    @Test
    @Throws(OnyxException::class)
    fun testInitializeToMany() {
        var parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ArrayList()

        val child = ToManyPartitionEntityChild()
        parent.child!!.add(child)

        child.id = 32L

        save(parent)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        manager.initialize(parent, "child")


        Assert.assertNotNull(parent.child)
        Assert.assertTrue(parent.child!!.size == 1)
    }
}
