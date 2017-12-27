package database.partition

import com.onyx.exception.NoResultsException
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.partition.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.util.ArrayList
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("MemberVisibilityCanPrivate")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class RelationshipPartitionTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun testSaveEntityWithRelationshipToOne() {
        val parent = ToOnePartitionEntityParent()
        parent.id = 4L
        parent.partitionId = 20L

        parent.child = ToOnePartitionEntityChild()
        parent.child!!.id = 5L

        manager.saveEntity<IManagedEntity>(parent.child!!)
        manager.saveEntity<IManagedEntity>(parent)

        val parentToFind = ToOnePartitionEntityParent()
        parentToFind.id = 4L
        parentToFind.partitionId = 20L

        manager.find<IManagedEntity>(parentToFind)

        assertNotNull(parentToFind.child, "Child should exist")
        assertEquals(parent.child!!.id, parentToFind.child!!.id, "Child id does not match")

        val childToFind = ToOnePartitionEntityChild()
        childToFind.id = 5L

        manager.find<IManagedEntity>(childToFind)

        assertNotNull(childToFind.parent, "Parent relationship not saved")
        assertEquals(parent.id, childToFind.parent!!.id, "Parent id does not match")
    }

    @Test(expected = NoResultsException::class)
    fun testDeleteEntityWithRelationshipToOne() {
        testSaveEntityWithRelationshipToOne()

        val parent = ToOnePartitionEntityParent()
        parent.id = 4L
        parent.partitionId = 20L

        manager.find<IManagedEntity>(parent)
        manager.deleteEntity(parent)
        manager.find<IManagedEntity>(parent.child!!)
    }

    @Test
    fun testSaveEntityAsRelationshipOneToMany() {
        var parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ManyToOnePartitionEntity()
        parent.child!!.id = 32L

        manager.saveEntity<IManagedEntity>(parent)

        parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L

        manager.find<IManagedEntity>(parent)

        assertNotNull(parent.child, "Child relationship not saved")
        assertEquals(32L, parent.child!!.id, "Child id does not match")

        manager.find<IManagedEntity>(parent.child!!)

        assertEquals(1, parent.child!!.parents!!.size, "Parent relationship not saved")
        assertEquals(23L, parent.child!!.parents!![0].id, "Parent id does not match")
    }

    @Test
    fun testDeleteEntityAsRelationshipOneToMany() {
        var parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ManyToOnePartitionEntity()
        parent.child!!.id = 32L

        manager.saveEntity<IManagedEntity>(parent)

        parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L

        manager.deleteEntity(parent)

        parent.child = ManyToOnePartitionEntity()
        parent.child!!.id = 32L

        manager.find<IManagedEntity>(parent.child!!)

        assertEquals(0, parent.child!!.parents!!.size, "Parent relationship not cascaded")

        var failedToFind = false
        try {
            manager.find<IManagedEntity>(parent)
        } catch (e: OnyxException) {
            failedToFind = true
        }

        assertTrue(failedToFind, "Parent relationship is lingering post deletion")
    }

    @Test
    fun testUpdateEntityAsRelationshipOneToMany() {
        var parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ManyToOnePartitionEntity()
        parent.child!!.id = 32L

        manager.saveEntity<IManagedEntity>(parent)

        parent = OneToManyPartitionEntity()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ManyToOnePartitionEntity()
        parent.child!!.id = 32L

        manager.find<IManagedEntity>(parent.child!!)

        parent.child!!.partitionId = 2L

        manager.saveEntity<IManagedEntity>(parent.child!!)

        manager.find<IManagedEntity>(parent)

        assertNotNull(parent.child, "Child relationship not persisted")
        assertEquals(32L, parent.child!!.id, "Child id does not match")
        assertEquals(2L, parent.child!!.partitionId, "Child partitionId does not match")

        manager.find<IManagedEntity>(parent.child!!)

        assertEquals(1, parent.child!!.parents!!.size, "Parent relationship not persisted")
        assertEquals(23, parent.child!!.parents!![0].id, "Parent id does not match")
        assertEquals(3L, parent.child!!.parents!![0].partitionId, "Parent partitionId does not match")

    }

    @Test
    fun testSaveEntityWithRelationshipManyToMany() {
        var parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ArrayList()

        var child = ToManyPartitionEntityChild()
        parent.child!!.add(child)

        child.id = 33L

        manager.saveEntity<IManagedEntity>(parent)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        manager.find<IManagedEntity>(parent)

        assertNotNull(parent.child, "Child relationship not persisted")
        assertEquals(1, parent.child!!.size, "Child relationship has incorrect number of entities")

        child = manager.find(parent.child!![0])

        assertEquals(1, child.parent!!.size, "Parent relationship not saved")
        assertEquals(23L, child.parent!![0].id, "Parent id does not match")
    }

    @Test
    fun testDeleteEntityWithRelationshipManyToMany() {
        var parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ArrayList()

        var child = ToManyPartitionEntityChild()
        parent.child!!.add(child)

        child.id = 32L

        manager.saveEntity<IManagedEntity>(parent)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        manager.find<IManagedEntity>(parent)

        assertNotNull(parent.child, "Child relationship not persisted")
        assertEquals(1, parent.child!!.size, "Child relationship inverse not persisted")

        child = manager.find(parent.child!![0])

        assertEquals(1, child.parent!!.size, "Parent relationship not persisted")
        assertEquals(23L, child.parent!![0].id, "Parent id does not match")

        parent.child!!.clear()
        manager.saveEntity<IManagedEntity>(parent)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        manager.find<IManagedEntity>(parent)

        assertNotNull(parent.child, "Child relationship is not persisted")
        assertEquals(0, parent.child!!.size, "Child relationship should be empty")

        child = ToManyPartitionEntityChild()
        child.id = 32L
        manager.find<IManagedEntity>(child)

        assertEquals(0, child.parent!!.size, "Parent relationship should have been removed")
    }

    @Test
    fun testUpdateEntityWithRelationshipManyToMany() {
        var parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ArrayList()

        var child = ToManyPartitionEntityChild()
        parent.child!!.add(child)

        child.id = 34L

        manager.saveEntity<IManagedEntity>(parent)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        manager.find<IManagedEntity>(parent)

        assertNotNull(parent.child, "Child relationship was not persisted")
        assertEquals(1, parent.child!!.size, "Child relationship count has invalid result")

        manager.find<IManagedEntity>(parent.child!![0])

        assertEquals(1, parent.child!![0].parent!!.size, "Parent relationship was not persisted")
        assertEquals(23L, parent.child!![0].parent!![0].id, "Parent has invalid id")

        child = parent.child!![0]
        manager.initialize(child, "parent")
        child.partitionId = 3L
        manager.saveEntity<IManagedEntity>(child)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        manager.find<IManagedEntity>(parent)
        manager.initialize(parent, "child")

        assertNotNull(parent.child, "Child relationship did not persist")
        assertEquals(1, parent.child!!.size, "Child has invalid number of relationships")
        assertEquals(3L, parent.child!![0].partitionId, "Partition id does not match")
    }

    @Test
    fun testInitializeToMany() {
        var parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L
        parent.child = ArrayList()

        val child = ToManyPartitionEntityChild()
        parent.child!!.add(child)

        child.id = 32L

        manager.saveEntity<IManagedEntity>(parent)

        parent = ToManyPartitionEntityParent()
        parent.id = 23L
        parent.partitionId = 3L

        manager.initialize(parent, "child")

        assertNotNull(parent.child, "Child relationship was not persisted")
        assertEquals(1, parent.child!!.size, "Parent inverse relationship was not hydrated")
    }
}
