package database.relationship

import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.relationship.OneToManyChild
import entities.relationship.OneToManyParent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(Parameterized::class)
class ManyToOneTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun testManyToOneRelationshipSaved() {
        val parent = OneToManyParent()
        parent.identifier = "MTO"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = OneToManyChild()
        child.identifier = "MTOB"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        child.parentNoCascade = parent
        manager.saveEntity<IManagedEntity>(child)

        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.childNoCascade!!.size, "Invalid child relationship")
        assertEquals(parent.childNoCascade!![0].identifier, child.identifier, "Invalid child identifier")
        assertEquals(parent.childNoCascade!![0].correlation, child.correlation, "Invalid correlation")
        assertEquals(2, child.correlation, "Invalid correlation")
    }

    @Test
    fun testManyToOneRelationshipSavedAndRemoved() {
        val parent = OneToManyParent()
        parent.identifier = "MTO1"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = OneToManyChild()
        child.identifier = "MTOB1"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        child.parentNoCascade = parent
        manager.saveEntity<IManagedEntity>(child)

        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.childNoCascade!!.size, "Invalid child relationship")
        assertEquals(parent.childNoCascade!![0].identifier, child.identifier, "Invalid child identifier")
        assertEquals(parent.childNoCascade!![0].correlation, child.correlation, "Invalid correlation")
        assertEquals(2, child.correlation, "Invalid correlation")

        child.parentNoCascade = null
        manager.saveEntity<IManagedEntity>(child)

        manager.find<IManagedEntity>(parent)

        assertEquals(0, parent.childNoCascade!!.size, "Failed to remove relationship")

        manager.find<IManagedEntity>(child)

        assertNull(child.parentNoCascade)

    }

    @Test
    fun testManyToOneRelationshipSavedAndReassigned() {
        var parent = OneToManyParent()
        parent.identifier = "MTO1"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = OneToManyChild()
        child.identifier = "MTOB1"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        child.parentNoCascade = parent
        manager.saveEntity<IManagedEntity>(child)

        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.childNoCascade!!.size, "Invalid child relationship")
        assertEquals(parent.childNoCascade!![0].identifier, child.identifier, "Invalid child identifier")
        assertEquals(parent.childNoCascade!![0].correlation, child.correlation, "Invalid correlation")
        assertEquals(2, child.correlation, "Invalid correlation")

        child.parentNoCascade = OneToManyParent()
        child.parentNoCascade!!.identifier = "SECOND"
        manager.saveEntity<IManagedEntity>(child.parentNoCascade!!)
        manager.saveEntity<IManagedEntity>(child)

        parent = OneToManyParent()
        parent.identifier = "MTO1"
        manager.find<IManagedEntity>(parent)

        assertEquals(0, parent.childNoCascade!!.size, "Failed to remove relationship")

        manager.find<IManagedEntity>(child)

        assertEquals(child.parentNoCascade!!.identifier, "SECOND", "Invalid parent relationship identifier")

        manager.find<IManagedEntity>(child.parentNoCascade!!)

        assertEquals(1, child.parentNoCascade!!.childNoCascade!!.size, "Failed to add relationship")
    }

    @Test
    fun testManyToOneRelationshipSavedAndReassignedCascade() {
        var parent = OneToManyParent()
        parent.identifier = "MTO11"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = OneToManyChild()
        child.identifier = "MTOB11"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        child.parentCascadeTwo = parent
        manager.saveEntity<IManagedEntity>(child)

        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.childCascadeTwo!!.size, "Invalid child relationship")
        assertEquals(parent.childCascadeTwo!![0].identifier, child.identifier, "Invalid child identifier")
        assertEquals(parent.childCascadeTwo!![0].correlation, child.correlation, "Invalid correlation")
        assertEquals(2, child.correlation, "Invalid correlation")

        child.parentCascadeTwo = OneToManyParent()
        child.parentCascadeTwo!!.identifier = "SECOND1"
        manager.saveEntity<IManagedEntity>(child.parentCascadeTwo!!)
        manager.saveEntity<IManagedEntity>(child)

        parent = OneToManyParent()
        parent.identifier = "MTO11"
        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.childCascadeTwo!!.size, "Failed to add child relationship")

        manager.find<IManagedEntity>(child)

        assertEquals(child.parentCascadeTwo!!.identifier, "SECOND1", "Invalid parent relationship identifier")

        manager.find<IManagedEntity>(child.parentCascadeTwo!!)

        assertEquals(1, child.parentCascadeTwo!!.childCascadeTwo!!.size, "Failed to add parent relationship")
    }

    @Test
    fun testManyToOneRelationshipParentDeleted() {
        val parent = OneToManyParent()
        parent.identifier = "MTO111"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        var child = OneToManyChild()
        child.identifier = "MTOB111"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        child.parentNoCascade = parent
        manager.saveEntity<IManagedEntity>(child)

        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.childNoCascade!!.size, "Invalid child relationship")
        assertEquals(parent.childNoCascade!![0].identifier, child.identifier, "Invalid child identifier")
        assertEquals(parent.childNoCascade!![0].correlation, child.correlation, "Invalid correlation")
        assertEquals(2, child.correlation, "Invalid correlation")

        manager.deleteEntity(parent)

        child = OneToManyChild()
        child.identifier = "MTOB111"
        manager.find<IManagedEntity>(child)

        assertNull(child.parentNoCascade)
        assertEquals(2, child.correlation, "Invalid correlation")
    }
}
