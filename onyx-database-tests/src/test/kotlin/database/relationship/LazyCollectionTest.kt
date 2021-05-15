package database.relationship

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.collections.LazyRelationshipCollection
import com.onyx.persistence.factory.impl.CacheManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import database.base.DatabaseBaseTest
import entities.relationship.ManyToManyChild
import entities.relationship.ManyToManyParent
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.util.ArrayList
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class LazyCollectionTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun testExists() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        manager.find<IManagedEntity>(parent)

        assertTrue(parent.childCascade is LazyRelationshipCollection<*>, "Child cascade relationship should be of type LazyRelationshipCollection")
        assertTrue(parent.childCascade!!.contains(parent.childCascade!![0]), "Contains method gives invalid results")
    }

    @Test
    fun testSize() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        manager.find<IManagedEntity>(parent)

        assertTrue(parent.childCascade is LazyRelationshipCollection<*>, "Child cascade relationship should be of type LazyRelationshipCollection")
        assertEquals(1, parent.childCascade!!.size, "Size does not match for relationship collection")
    }

    @Test(expected = RuntimeException::class)
    fun testAdd() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        manager.find<IManagedEntity>(parent)

        val child2 = ManyToManyChild()
        child2.identifier = "C"
        child2.correlation = 2
        manager.saveEntity<IManagedEntity>(child2)

        parent.childCascade!!.add(child2)
        assertTrue(parent.childCascade is LazyRelationshipCollection<*>, "Child cascade relationship should be of type LazyRelationshipCollection")
        assertEquals(2, parent.childCascade!!.size, "Size does not match for relationship collection")
        assertTrue(parent.childCascade!!.contains(child2), "Contains method gives invalid data")
    }

    @Test
    fun testEmpty() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        manager.find<IManagedEntity>(parent)

        assertFalse(parent.childCascade!!.isEmpty(), "Is empty gives invalid data")
    }

    @Test
    fun testClear() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        manager.find<IManagedEntity>(parent)

        parent.childCascade!!.clear()

        assertTrue(parent.childCascade!!.isEmpty(), "Relationship collection is not empty")
    }

    @Test(expected = RuntimeException::class)
    fun testSet() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        manager.find<IManagedEntity>(parent)

        val child2 = ManyToManyChild()
        child2.identifier = "C"
        child2.correlation = 2
        manager.saveEntity<IManagedEntity>(child2)

        parent.childCascade!![0] = child2
        assertTrue(parent.childCascade is LazyRelationshipCollection<*>, "Child cascade relationship should be of type LazyRelationshipCollection")
        assertEquals(1, parent.childCascade!!.size,"Size does not match for relationship collection")
        assertTrue(parent.childCascade!!.contains(child2), "Invalid data for contains method")
    }

    @Test
    fun testRemove() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        manager.find<IManagedEntity>(parent)


        parent.childCascade!!.removeAt(0)
        assertTrue(parent.childCascade is LazyRelationshipCollection<*>, "Child cascade relationship should be of type LazyRelationshipCollection")
        assertEquals(0, parent.childCascade!!.size, "Size does not match for relationship collection")
    }

    @Test(expected = RuntimeException::class)
    fun testRemoveByObject() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        manager.find<IManagedEntity>(parent)

        parent.childCascade!!.remove(child)
        assertTrue(parent.childCascade is LazyRelationshipCollection<*>, "Child cascade relationship should be of type LazyRelationshipCollection")
        assertEquals(0, parent.childCascade!!.size, "Size does not match for relationship collection")
    }

    companion object {
        /**
         * Lazy relationship collection is unsupported for Web Persistence Manager Factory
         */
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(CacheManagerFactory::class, EmbeddedPersistenceManagerFactory::class, RemotePersistenceManagerFactory::class)
    }
}
