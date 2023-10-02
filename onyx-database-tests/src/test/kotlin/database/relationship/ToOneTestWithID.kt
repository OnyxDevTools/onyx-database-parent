package database.relationship

import com.onyx.exception.RelationshipEntityNotFoundException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.relationship.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.*

@RunWith(Parameterized::class)
class ToOneTestWithID(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test(expected = RelationshipEntityNotFoundException::class)
    fun testChildDoesNotExist() {
        val parent = ToOneParent()
        parent.correlation = 1
        parent.identifier = "A"

        manager.saveEntity<IManagedEntity>(parent)
        manager.find<IManagedEntity>(parent)
        assertEquals(1, parent.correlation)

        parent.childId = "B"

        manager.saveEntity<IManagedEntity>(parent)
    }

    @Test
    fun testToOneInsert() {

        val child = ToOneChild()
        child.identifier = "B"
        manager.saveEntity(child)

        val parent = ToOneParent()
        parent.correlation = 1
        parent.childId = child.identifier
        parent.identifier = "A"

        manager.saveEntity<IManagedEntity>(parent)
        val loadedParent = manager.find<ToOneParent>(parent)
        val loadedChild = manager.find<ToOneChild>(child)

        assertNotNull(loadedChild)
        assertNotNull(loadedParent.childId)
        assertEquals(loadedParent.identifier, loadedChild.parentId)
        assertEquals(loadedChild.identifier, loadedParent.childId)
    }


    @Test
    fun testToOneDelete() {

        val child = ToOneChild()
        child.identifier = "B"
        manager.saveEntity(child)

        val parent = ToOneParent()
        parent.correlation = 1
        parent.childId = child.identifier
        parent.identifier = "A"

        manager.saveEntity<IManagedEntity>(parent)
        val loadedParent = manager.find<ToOneParent>(parent)
        var loadedChild = manager.find<ToOneChild>(child)

        assertNotNull(loadedChild)
        assertNotNull(loadedParent.childId)
        assertEquals(loadedParent.identifier, loadedChild.parentId)
        assertEquals(loadedChild.identifier, loadedParent.childId)

        manager.deleteEntity(loadedParent)
        loadedChild = manager.find<ToOneChild>(child)
        assertNull(loadedChild.parentId)
    }

    @Test(expected = RelationshipEntityNotFoundException::class)
    fun testChildToManyDoesNotExist() {
        val child = ToOneChild()
        child.identifier = "K"
        child.parentToManyId = "Z"

        manager.saveEntity<IManagedEntity>(child)
    }

    @Test
    fun testToOneToManyInsert() {

        val parent = ToOneParent()
        parent.correlation = 1
        parent.identifier = "F"
        manager.saveEntity<IManagedEntity>(parent)

        val child = ToOneChild()
        child.identifier = "D"
        child.parentToManyId = "F"
        manager.saveEntity(child)

        val loadedParent = manager.find<ToOneParent>(parent)
        val loadedChild = manager.find<ToOneChild>(child)

        assertNotNull(loadedChild)
        assertNotNull(loadedChild.parentToManyId)
        assertEquals(loadedParent.identifier, loadedChild.parentToManyId)
        assertTrue(loadedParent.childrenToMany.firstOrNull { it.identifier == loadedChild.identifier } != null)
    }


    @Test
    fun testOneManyDelete() {

        val parent = ToOneParent()
        parent.correlation = 1
        parent.identifier = "F"
        manager.saveEntity<IManagedEntity>(parent)

        val child = ToOneChild()
        child.identifier = "D"
        child.parentToManyId = "F"
        manager.saveEntity(child)

        var loadedParent = manager.find<ToOneParent>(parent)
        val loadedChild = manager.find<ToOneChild>(child)

        assertNotNull(loadedChild)
        assertNotNull(loadedChild.parentToManyId)
        assertEquals(loadedParent.identifier, loadedChild.parentToManyId)
        assertTrue(loadedParent.childrenToMany.firstOrNull { it.identifier == loadedChild.identifier } != null)

        manager.deleteEntity(loadedChild)
        loadedParent = manager.find<ToOneParent>(parent)
        assertFalse(loadedParent.childrenToMany.contains(loadedChild))
    }
}
