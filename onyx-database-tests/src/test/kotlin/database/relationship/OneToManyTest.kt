package database.relationship

import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.relationship.OneToManyChild
import entities.relationship.OneToManyParent
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.util.ArrayList
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class OneToManyTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun aTestOneToManyNoCascade() {
        val parent = OneToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = OneToManyChild()
        child.identifier = "B"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)

        manager.saveEntity<IManagedEntity>(parent)

        var child1 = OneToManyChild()
        child1.identifier = "B"
        manager.find<IManagedEntity>(child1)

        // Validate the child contains the relationship
        assertEquals(2, child1.correlation, "Invalid Correlation")
        assertNotNull(child1.parentNoCascade, "Parent relationship invalid")
        assertEquals(child1.parentNoCascade!!.identifier, parent.identifier, "Parent identifier invalid")

        val parent1 = OneToManyParent()
        parent1.identifier = "A"
        manager.find<IManagedEntity>(parent1)

        assertEquals(1, parent1.correlation, "Invalid Correlation")
        assertNotNull(parent1.childNoCascade, "Child relationship invalid")
        assertEquals(1, parent1.childNoCascade!!.size, "Child relationship expecting a single entity")
        assertEquals(parent1.childNoCascade!![0].identifier, child.identifier, "Child identifier invalid")

        child1 = OneToManyChild()
        child1.identifier = "B"
        manager.find<IManagedEntity>(child1)
        child1.parentNoCascade = null
        manager.saveEntity<IManagedEntity>(child1)

        assertNull(child1.parentNoCascade, "Parent relationship not deleted")
        assertEquals(2, child1.correlation, "Invalid Correlation")

        manager.find<IManagedEntity>(parent1)

        assertEquals(0, parent1.childNoCascade!!.size, "Inverse relationship not cascaded")

    }

    @Test
    fun bTestOneToManyCascade() {
        val parent = OneToManyParent()
        parent.identifier = "E"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)

        val child = OneToManyChild()
        child.identifier = "F"
        child.correlation = 2
        manager.saveEntity<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)

        manager.saveEntity<IManagedEntity>(parent)

        var child1 = OneToManyChild()
        child1.identifier = "F"
        manager.find<IManagedEntity>(child1)

        // Validate the child contains the relationship
        assertEquals(2, child1.correlation, "Invalid Correlation")
        assertNotNull(child1.parentCascade, "Parent relationship invalid")
        assertEquals(child1.parentCascade!!.identifier, parent.identifier, "Invalid parent identifier")

        var parent1 = OneToManyParent()
        parent1.identifier = "E"
        manager.find<IManagedEntity>(parent1)

        assertEquals(1,  parent1.correlation, "Invalid Correlation")
        assertNotNull(parent1.childCascade, "Failed to save child relationship")
        assertEquals(1, parent1.childCascade!!.size, "Invalid number of relationship items")
        assertEquals(parent1.childCascade!![0].identifier, child.identifier, "Invalid child identifier")

        parent1.childCascade!!.removeAt(0)
        manager.saveEntity<IManagedEntity>(parent1)

        parent1 = OneToManyParent()
        parent1.identifier = "E"
        manager.find<IManagedEntity>(parent1)

        assertEquals(0, parent1.childCascade!!.size, "Failure to remove relationship")

        child1 = OneToManyChild()
        child1.identifier = "F"

        var exception = false
        try {
            manager.find<IManagedEntity>(child1)
        } catch (e: NoResultsException) {
            exception = true
        }

        assertFalse(exception, "Failure to cascade relationship")
    }


    @Test
    fun cTestOneToManyCascadeInverse() {

        val tmpPar = OneToManyChild()
        tmpPar.identifier = "ASDF"
        manager.saveEntity<IManagedEntity>(tmpPar)

        tmpPar.parentCascade = OneToManyParent()
        tmpPar.parentCascade!!.identifier = "ASDFs"
        manager.saveEntity<IManagedEntity>(tmpPar)


        val parent = OneToManyParent()
        parent.identifier = "C"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)
        manager.find<IManagedEntity>(parent)

        val child = OneToManyChild()
        child.identifier = "D"
        child.correlation = 2
        child.parentCascade = parent
        manager.saveEntity<IManagedEntity>(child)
        manager.find<IManagedEntity>(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)

        manager.saveEntity<IManagedEntity>(parent)

        val child1 = OneToManyChild()
        child1.identifier = "D"
        manager.find<IManagedEntity>(child1)

        // Validate the child contains the relationship
        assertEquals(2, child1.correlation, "Invalid Correlation")
        assertNotNull(child1.parentCascade, "Invalid parent relationship")
        assertEquals(child1.parentCascade!!.identifier, parent.identifier, "Invalid parent identifier")

        val parent1 = OneToManyParent()
        parent1.identifier = "C"
        manager.find<IManagedEntity>(parent1)

        assertEquals(1, parent1.correlation, "Invalid Correlation")
        assertNotNull(parent1.childCascade, "Invalid child relationship")
        assertEquals(1, parent1.childCascade!!.size, "Expected only 1 result")
        assertEquals(parent1.childCascade!![0].identifier, child.identifier, "Invalid child identifier")

        manager.deleteEntity(child1)

        val parent2 = OneToManyParent()
        parent2.identifier = "C"
        manager.find<IManagedEntity>(parent2)

        assertEquals(0, parent2.childCascade!!.size, "Failure to remove relationship")

    }

    @Test
    fun dTestOneToManyNoCascade() {
        val parent = OneToManyParent()
        parent.identifier = "F"
        parent.correlation = 1
        manager.saveEntity<IManagedEntity>(parent)
        manager.find<IManagedEntity>(parent)

        val child = OneToManyChild()
        child.identifier = "G"
        child.correlation = 2
        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)
        manager.saveEntity<IManagedEntity>(parent)

        // Ensure the object was not cascaded
        val parent2 = OneToManyParent()
        parent2.identifier = "F"
        manager.find<IManagedEntity>(parent2)
        assertEquals(0, parent2.childNoCascade!!.size, "Should not have cascaded relationship")
    }

    @Test
    fun eTestOneToManyNoCascadeNoInverse() {
        val parent = OneToManyParent()
        parent.identifier = "H"
        parent.correlation = 14
        manager.saveEntity<IManagedEntity>(parent)

        val child = OneToManyChild()
        child.identifier = "I"
        child.correlation = 22
        manager.saveEntity<IManagedEntity>(child)

        parent.childNoInverseNoCascade = ArrayList()
        parent.childNoInverseNoCascade!!.add(child)

        manager.saveEntity<IManagedEntity>(parent)

        var child1 = OneToManyChild()
        child1.identifier = "I"
        manager.find<IManagedEntity>(child1)

        // Validate the child contains the relationship
        assertEquals(22, child1.correlation, "Invalid Correlation")

        val parent1 = OneToManyParent()
        parent1.identifier = "H"
        manager.find<IManagedEntity>(parent1)

        // Verify the relationship is still there
        assertEquals(14, parent1.correlation, "Invalid Correlation")
        assertNotNull(parent1.childNoInverseNoCascade, "Invalid child relationship")
        assertEquals(1, parent1.childNoInverseNoCascade!!.size, "Expected a single relationship object")
        assertEquals(child.identifier, parent1.childNoInverseNoCascade!![0].identifier, "Invalid child identifier")

        manager.initialize(parent1, "childNoInverseNoCascade")
        parent1.childNoInverseNoCascade!!.removeAt(0)
        manager.saveEntity<IManagedEntity>(parent1)

        // Ensure the child still loads and the parent did not wipe out the entity
        child1 = OneToManyChild()
        child1.identifier = "I"
        manager.find<IManagedEntity>(child1)
        assertEquals(22, child1.correlation, "Invalid Correlation")

        // Get the parent to check relationships
        val parent2 = OneToManyParent()
        parent2.identifier = "H"
        manager.find<IManagedEntity>(parent2)

        // Ensure the relationship was not removed
        assertEquals(1, parent2.childNoInverseNoCascade!!.size, "Invalid child relationship")
        assertEquals(14, parent2.correlation, "Invalid correlation")
        child1.parentNoCascade = null

    }

    @Test
    fun fTestOneToManyNoCascadeNoInverse() {
        val parent = OneToManyParent()
        parent.identifier = "J"
        parent.correlation = 15
        manager.saveEntity<IManagedEntity>(parent)

        val child = OneToManyChild()
        child.identifier = "K"
        child.correlation = 23
        manager.saveEntity<IManagedEntity>(child)

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)

        manager.saveEntity<IManagedEntity>(parent)

        var child1 = OneToManyChild()
        child1.identifier = "K"
        manager.find<IManagedEntity>(child1)

        // Validate the child still exists
        assertEquals(23, child1.correlation, "Invalid Correlation")

        val parent1 = OneToManyParent()
        parent1.identifier = "J"
        manager.find<IManagedEntity>(parent1)

        // Verify the relationship is still there
        assertEquals(15, parent1.correlation, "Invalid correlation")
        assertNotNull(parent1.childNoInverseCascade, "Invalid child relationship")
        assertEquals(1, parent1.childNoInverseCascade!!.size, "Expected single relationship entity")
        assertEquals(child.identifier, parent1.childNoInverseCascade!![0].identifier, "Invalid child identifier")

        //parent1.childNoInverseNoCascade = null;
        manager.initialize(parent1, "childNoInverseCascade")
        parent1.childNoInverseCascade!!.removeAt(0)
        manager.saveEntity<IManagedEntity>(parent1)

        // Ensure the child still loads and the parent did not wipe out the entity
        child1 = OneToManyChild()
        child1.identifier = "K"
        manager.find<IManagedEntity>(child1)
        assertEquals(23, child1.correlation, "Invalid Correlation")

        // Get the parent to check relationships
        val parent2 = OneToManyParent()
        parent2.identifier = "J"
        manager.find<IManagedEntity>(parent2)

        // Ensure the relationship was not removed
        assertEquals(0, parent2.childNoInverseCascade!!.size, "Failure to remove relationship")
        assertEquals(15, parent2.correlation, "Invalid Correlation")
    }

    @Test
    fun gTestOneToManyRemoveHasMultiple() {
        val parent = OneToManyParent()
        parent.identifier = "Z"
        parent.correlation = 30
        manager.saveEntity<IManagedEntity>(parent)

        var child = OneToManyChild()
        child.identifier = "Y"
        child.correlation = 31

        var child2 = OneToManyChild()
        child2.identifier = "X"
        child2.correlation = 32

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)
        parent.childNoInverseCascade!!.add(child2)

        manager.saveEntity<IManagedEntity>(parent)

        child = OneToManyChild()
        child.identifier = "Y"
        manager.find<IManagedEntity>(child)

        child2 = OneToManyChild()
        child2.identifier = "X"
        manager.find<IManagedEntity>(child2)

        // Validate the child still exists
        assertEquals(32, child2.correlation, "Invalid Correlation")

        val parent1 = OneToManyParent()
        parent1.identifier = "Z"
        manager.find<IManagedEntity>(parent1)

        // Verify the relationship is still there
        assertEquals(30, parent1.correlation, "Invalid Correlation")
        assertNotNull(parent1.childNoInverseCascade, "Invalid child relationship")
        assertEquals(2, parent1.childNoInverseCascade!!.size, "Expected 2 relationship entities")

        manager.initialize(parent1, "childNoInverseCascade")
        parent1.childNoInverseCascade!!.removeAt(1)
        manager.saveEntity<IManagedEntity>(parent1)

        // Get the parent to check relationships
        val parent2 = OneToManyParent()
        parent2.identifier = "Z"
        manager.find<IManagedEntity>(parent2)

        // Ensure the relationship was not removed
        assertEquals(1, parent2.childNoInverseCascade!!.size, "Did not cascade correctly")
        assertEquals(child2.identifier, parent2.childNoInverseCascade!![0].identifier, "Invalid child identifier")
        assertEquals(30, parent2.correlation, "Invalid Correlation")

    }

    @Test
    fun fTestOneToManyRemoveHasMultiple() {
        val parent = OneToManyParent()
        parent.identifier = "ZZ"
        parent.correlation = 30
        manager.saveEntity<IManagedEntity>(parent)

        var child = OneToManyChild()
        child.identifier = "YY"
        child.correlation = 31

        var child2 = OneToManyChild()
        child2.identifier = "XX"
        child2.correlation = 32

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)
        parent.childNoInverseCascade!!.add(child2)

        manager.saveEntity<IManagedEntity>(parent)

        child = OneToManyChild()
        child.identifier = "YY"
        manager.find<IManagedEntity>(child)

        child2 = OneToManyChild()
        child2.identifier = "XX"
        manager.find<IManagedEntity>(child2)

        // Validate the child still exists
        assertEquals(32, child2.correlation, "Invalid Correlation")

        val parent1 = OneToManyParent()
        parent1.identifier = "ZZ"
        manager.find<IManagedEntity>(parent1)

        // Verify the relationship is still there
        assertEquals(30, parent1.correlation, "Invalid correlation")
        assertNotNull(parent1.childNoInverseCascade, "Invalid child relationship")
        assertEquals(2, parent1.childNoInverseCascade!!.size, "Expected 2 relationship entities")

        manager.initialize(parent1, "childNoInverseCascade")
        parent1.childNoInverseCascade!!.removeAll(parent1.childNoInverseCascade!!)
        manager.saveEntity<IManagedEntity>(parent1)

        // Get the parent to check relationships
        val parent2 = OneToManyParent()
        parent2.identifier = "ZZ"
        manager.find<IManagedEntity>(parent2)

        assertEquals(0, parent2.childNoInverseCascade!!.size, "Invalid cascading")
        assertEquals(30, parent2.correlation, "Invalid Correlation")
    }
}