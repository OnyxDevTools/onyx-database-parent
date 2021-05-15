package database.relationship

import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.relationship.ManyToManyChild
import entities.relationship.ManyToManyParent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.ArrayList
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class ManyToManyTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun testManyToManyBasic() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        var child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.correlation, "Expected correlation as 1")
        assertEquals(1, parent.childNoCascade!!.size, "Child relationship not persisted")
        assertEquals(child.identifier, parent.childNoCascade!![0].identifier, "Child identifier invalid")

        child = ManyToManyChild()
        child.identifier = "B"
        manager.find<IManagedEntity>(child)

        assertEquals(2, child.correlation, "Child correlation invalid")
        assertEquals(1, child.parentNoCascade!!.size, "Parent relationship not persisted")
        assertEquals(parent.identifier, child.parentNoCascade!![0].identifier, "Invalid parent identifier")
    }

    @Test
    fun testManyToManyMultiple() {
        var parent = ManyToManyParent()
        parent.identifier = "C"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        var child = ManyToManyChild()
        child.identifier = "D"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        val child2 = ManyToManyChild()
        child2.identifier = "E"
        child2.correlation = 3
        manager.saveEntity<IManagedEntity>(child2)

        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)
        parent.childNoCascade!!.add(child2)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "C"
        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.correlation, "Invalid correlation")
        assertEquals(2, parent.childNoCascade!!.size, "Invalid child relationship")
        assertEquals(child.identifier, parent.childNoCascade!![0].identifier, "Invalid child identifier")

        child = ManyToManyChild()
        child.identifier = "D"
        manager.find<IManagedEntity>(child)

        assertEquals(2, child.correlation, "Invalid correlation")
        assertEquals(1, child.parentNoCascade!!.size, "Invalid parent relationship")
        assertEquals(parent.identifier, child.parentNoCascade!![0].identifier, "Invalid parent identifier")
    }

    @Test
    fun testManyToManyNoCascadeRemove() {
        var parent = ManyToManyParent()
        parent.identifier = "F"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "G"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent.childNoCascade!!.remove(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "F"
        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.correlation, "Invalid correlation")
        assertEquals(1, parent.childNoCascade!!.size, "Invalid child relationship")

    }

    @Test
    fun testManyToManyCascadeRemove() {
        var parent = ManyToManyParent()
        parent.identifier = "H"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "I"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent.childCascade!!.remove(parent.childCascade!![0])
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "H"
        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.correlation, "Invalid correlation")
        assertEquals(0, parent.childCascade!!.size, "Invalid child relationship")

    }

    @Test
    fun testManyToManyCascadeDelete() {
        var parent = ManyToManyParent()
        parent.identifier = "J"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "K"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        manager.deleteEntity(child)

        parent = ManyToManyParent()
        parent.identifier = "J"
        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.correlation, "Invalid correlation")
        assertEquals(0, parent.childNoCascade!!.size, "Invalid Child relationship")
    }

    @Test
    fun testManyToManyNoInverse() {
        var parent = ManyToManyParent()
        parent.identifier = "J"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "K"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "J"
        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.correlation, "Invalid correlation")
        assertEquals(1, parent.childNoInverseCascade!!.size, "Invalid child relationship")
    }

    @Test
    fun testManyToManyNoInverseDelete() {
        var parent = ManyToManyParent()
        parent.identifier = "Z"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = ManyToManyChild()
        child.identifier = "P"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        parent.childNoInverseCascade!!.remove(parent.childNoInverseCascade!![0])
        manager.saveEntity<IManagedEntity>(parent)

        parent = ManyToManyParent()
        parent.identifier = "Z"
        manager.find<IManagedEntity>(parent)

        assertEquals(1, parent.correlation, "Invalid correlation")
        assertEquals(0, parent.childNoInverseCascade!!.size, "Invalid child relationship")
    }
}
