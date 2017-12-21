package database.relationship

import com.onyx.exception.NoResultsException
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.relationship.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.*

@RunWith(Parameterized::class)
class OneToOneTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun testOneToOneCascade() {
        val parent = OneToOneParent()
        parent.correlation = 1
        parent.identifier = "A"

        manager.saveEntity<IManagedEntity>(parent)
        manager.find<IManagedEntity>(parent)
        assertEquals(1, parent.correlation)
        assertNull(parent.child)

        parent.child = OneToOneChild()
        parent.child!!.identifier = "B"
        parent.child!!.correlation = 2

        manager.saveEntity<IManagedEntity>(parent)
        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.correlation)
        assertNotNull(parent.child)
        assertEquals(2, parent.child!!.correlation)

        val child = OneToOneChild()
        child.identifier = "B"
        manager.find<IManagedEntity>(child)
        manager.initialize(child, "parent")

        assertEquals(2, child.correlation)
        assertNotNull(child.parent)
        assertEquals(1, child.parent!!.correlation)

    }

    @Test
    fun testOneToOneNoCascade() {
        val child = OneToOneChild()
        child.identifier = "D"
        child.correlation = 4

        manager.saveEntity<IManagedEntity>(child)

        val parent = OneToOneParent()
        parent.correlation = 3
        parent.identifier = "C"

        manager.saveEntity<IManagedEntity>(parent)
        manager.find<IManagedEntity>(child)

        assertEquals(4, child.correlation)
        assertNull(child.parent)

        manager.find<IManagedEntity>(parent)
        assertEquals(3, parent.correlation)
        assertNull(parent.child)

        child.parent = parent
        manager.saveEntity<IManagedEntity>(child)
        manager.find<IManagedEntity>(parent)

        assertEquals(3, parent.correlation)
        assertNotNull(parent.child)
        assertEquals(4, parent.child!!.correlation)

        manager.find<IManagedEntity>(child)
        manager.initialize(child, "parent")

        assertEquals(4, child.correlation)
        assertNotNull(child.parent)
        assertEquals(3, child.parent!!.correlation)
    }

    @Test
    fun testDeleteRelationshipNoCascade() {

        // Save The Parent
        var parent = OneToOneParent()
        parent.correlation = 10
        parent.identifier = "E"
        manager.saveEntity<IManagedEntity>(parent)

        // Save the child
        var child = OneToOneChild()
        child.correlation = 11
        child.identifier = "F"
        manager.saveEntity<IManagedEntity>(child)

        parent.child = child
        manager.saveEntity<IManagedEntity>(parent)

        parent = OneToOneParent()
        parent.identifier = "E"
        manager.find<IManagedEntity>(parent)

        assertNotNull(parent.child)

        manager.initialize(child, "parent")

        assertNotNull(child.parent)
        assertEquals(child.parent!!.identifier, parent.identifier)
        assertEquals(parent.child!!.identifier, child.identifier)

        parent.child = null
        manager.saveEntity<IManagedEntity>(parent)
        manager.find<IManagedEntity>(parent)
        child = OneToOneChild()
        child.identifier = "F"
        manager.initialize(child, "parent")

        assertNull(child.parent)
        assertNull(parent.child)
    }

    @Test
    fun testDeleteCascade() {
        // Save The Parent
        var parent = OneToOneParent()
        parent.correlation = 10
        parent.identifier = "G"
        manager.saveEntity<IManagedEntity>(parent)

        // Save the child
        var child = OneToOneChild()
        child.correlation = 11
        child.identifier = "H"
        manager.saveEntity<IManagedEntity>(child)

        parent.cascadeChild = child
        manager.saveEntity<IManagedEntity>(parent)

        parent = OneToOneParent()
        parent.identifier = "G"
        parent = manager.find<IManagedEntity>(parent) as OneToOneParent

        assertNotNull(parent.cascadeChild)

        manager.initialize(child, "cascadeParent")

        assertNotNull(child.cascadeParent)
        assertEquals(child.cascadeParent!!.identifier, parent.identifier)
        assertEquals(parent.cascadeChild!!.identifier, child.identifier)

        parent.cascadeChild = null
        manager.saveEntity<IManagedEntity>(parent)
        @Suppress("UNUSED_VALUE")
        parent = manager.find<IManagedEntity>(parent) as OneToOneParent
        child = OneToOneChild()
        child.identifier = "H"

        var exceptionThrown = false
        try {
            @Suppress("UNUSED_VALUE")
            child = manager.find(child)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                exceptionThrown = true
            }
        }

        assertTrue(exceptionThrown)
    }

    @Test
    fun testNoInverseCascade() {
        // Save The Parent
        var parent = OneToOneParent()
        parent.correlation = 10
        parent.identifier = "G"
        manager.saveEntity<IManagedEntity>(parent)

        // Save the child
        var child = OneToOneChild()
        child.correlation = 11
        child.identifier = "H"
        manager.saveEntity<IManagedEntity>(child)

        parent.childNoInverseCascade = child
        manager.saveEntity<IManagedEntity>(parent)

        parent = OneToOneParent()
        parent.identifier = "G"
        manager.find<IManagedEntity>(parent)

        assertNotNull(parent.childNoInverseCascade)

        manager.initialize(child, "cascadeParent")

        assertEquals(parent.childNoInverseCascade!!.identifier, child.identifier)

        parent.childNoInverseCascade = null

        parent.cascadeChild = null
        manager.saveEntity<IManagedEntity>(parent)
        manager.find<IManagedEntity>(parent)

        child = OneToOneChild()
        child.identifier = "H"

        var exceptionThrown = false
        try {
            manager.find<IManagedEntity>(child)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                exceptionThrown = true
            }
        }

        assertTrue(exceptionThrown)
    }

    @Test
    fun testNoInverse() {
        // Save The Parent
        var parent = OneToOneParent()
        parent.correlation = 133
        parent.identifier = "G"
        manager.saveEntity<IManagedEntity>(parent)

        // Save the child
        var child = OneToOneChild()
        child.correlation = 112
        child.identifier = "I"
        manager.saveEntity<IManagedEntity>(child)

        parent.childNoInverse = child
        manager.saveEntity<IManagedEntity>(parent)

        parent = OneToOneParent()
        parent.identifier = "G"
        manager.find<IManagedEntity>(parent)

        assertNotNull(parent.childNoInverse)
        assertEquals(parent.childNoInverse!!.identifier, child.identifier)

        parent.childNoInverse = null

        parent.cascadeChild = null
        manager.saveEntity<IManagedEntity>(parent)
        manager.find<IManagedEntity>(parent)

        child = OneToOneChild()
        child.identifier = "I"

        var exceptionThrown = false
        try {
            manager.find<IManagedEntity>(child)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                exceptionThrown = true
            }
        }

        assertFalse(exceptionThrown)
        assertEquals(112, child.correlation)

    }

    @Test
    fun testInverseCascadeDelete() {
        // Save The Parent
        var parent = OneToOneParent()
        parent.correlation = 10
        parent.identifier = "Z"
        manager.saveEntity<IManagedEntity>(parent)

        // Save the child
        var child = OneToOneChild()
        child.correlation = 11
        child.identifier = "X"
        manager.saveEntity<IManagedEntity>(child)

        parent.cascadeChild = child
        manager.saveEntity<IManagedEntity>(parent)

        parent = OneToOneParent()
        parent.identifier = "Z"
        manager.find<IManagedEntity>(parent)

        assertNotNull(parent.cascadeChild)

        manager.initialize(child, "cascadeParent")

        assertEquals(parent.cascadeChild!!.identifier, child.identifier)

        manager.saveEntity<IManagedEntity>(parent)
        manager.find<IManagedEntity>(parent)
        manager.deleteEntity(parent)

        child = OneToOneChild()
        child.identifier = "X"

        var exceptionThrown = false
        try {
            manager.find<IManagedEntity>(child)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                exceptionThrown = true
            }
        }

        assertTrue(exceptionThrown)

    }


    @Test
    fun testRecursiveOneToOne() {
        val parent = OneToOneRecursive()
        parent.id = 1
        parent.child = OneToOneRecursiveChild()
        parent.child!!.id = 2
        parent.child!!.third = OneToOneThreeDeep()
        parent.child!!.third!!.id = 3


        manager.saveEntity<IManagedEntity>(parent)

        val newParent = OneToOneRecursive()
        newParent.id = 1
        manager.find<IManagedEntity>(newParent)

        assertNotNull(parent)
        assertNotNull(parent.child)
        assertNotNull(parent.child!!.third)

        assertNotNull(parent.child!!.parent)
        assertNotNull(parent.child!!.third!!.parent)
    }
}
