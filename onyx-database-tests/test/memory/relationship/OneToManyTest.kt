package memory.relationship

import category.InMemoryDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import entities.relationship.OneToManyChild
import entities.relationship.OneToManyParent
import junit.framework.Assert
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters

import java.io.IOException
import java.util.ArrayList

/**
 * Created by timothy.osborn on 11/3/14.
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(InMemoryDatabaseTests::class)
class OneToManyTest : memory.base.BaseTest() {

    @Before
    @Throws(OnyxException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Test
    fun atestOneToManyNoCascade() {
        val parent = OneToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        save(parent)

        val child = OneToManyChild()
        child.identifier = "B"
        child.correlation = 2
        save(child)

        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)

        save(parent)

        var child1 = OneToManyChild()
        child1.identifier = "B"
        find(child1)

        // Validate the child contains the relationship
        Assert.assertEquals(child1.correlation, 2)
        Assert.assertNotNull(child1.parentNoCascade)
        Assert.assertEquals(child1.parentNoCascade!!.identifier, parent.identifier)

        val parent1 = OneToManyParent()
        parent1.identifier = "A"
        find(parent1)

        Assert.assertEquals(parent1.correlation, 1)
        Assert.assertNotNull(parent1.childNoCascade)
        Assert.assertEquals(parent1.childNoCascade!!.size, 1)
        Assert.assertEquals(parent1.childNoCascade!![0].identifier, child.identifier)

        child1 = OneToManyChild()
        child1.identifier = "B"
        find(child1)
        child1.parentNoCascade = null
        save(child1)

        Assert.assertNull(child1.parentNoCascade)
        Assert.assertEquals(child1.correlation, 2)

        find(parent1)

        Assert.assertEquals(parent1.childNoCascade!!.size, 0)

    }

    @Test
    fun btestOneToManyCascade() {
        val parent = OneToManyParent()
        parent.identifier = "E"
        parent.correlation = 1
        save(parent)

        val child = OneToManyChild()
        child.identifier = "F"
        child.correlation = 2
        save(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)

        save(parent)

        var child1 = OneToManyChild()
        child1.identifier = "F"
        find(child1)

        // Validate the child contains the relationship
        Assert.assertEquals(child1.correlation, 2)
        Assert.assertNotNull(child1.parentCascade)
        Assert.assertEquals(child1.parentCascade!!.identifier, parent.identifier)

        var parent1 = OneToManyParent()
        parent1.identifier = "E"
        find(parent1)

        Assert.assertEquals(parent1.correlation, 1)
        Assert.assertNotNull(parent1.childCascade)
        Assert.assertEquals(parent1.childCascade!!.size, 1)
        Assert.assertEquals(parent1.childCascade!![0].identifier, child.identifier)

        parent1.childCascade!!.removeAt(0)
        save(parent1)

        parent1 = OneToManyParent()
        parent1.identifier = "E"
        find(parent1)

        Assert.assertEquals(parent1.childCascade!!.size, 0)

        child1 = OneToManyChild()
        child1.identifier = "F"

        var exception = false
        try {
            manager.find<IManagedEntity>(child1)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                exception = true
            }
        }

        Assert.assertFalse(exception)
    }


    @Test
    fun ctestOneToManyCascadeInverse() {

        val tmpPar = OneToManyChild()
        tmpPar.identifier = "ASDF"
        save(tmpPar)

        tmpPar.parentCascade = OneToManyParent()
        tmpPar.parentCascade!!.identifier = "ASDFs"
        save(tmpPar)


        val parent = OneToManyParent()
        parent.identifier = "C"
        parent.correlation = 1
        save(parent)
        find(parent)

        val child = OneToManyChild()
        child.identifier = "D"
        child.correlation = 2
        child.parentCascade = parent
        save(child)
        find(child)
        //

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)

        save(parent)
        //find(parent);

        val child1 = OneToManyChild()
        child1.identifier = "D"
        find(child1)

        initialize(child1, "parentCascade")
        // Validate the child contains the relationship
        Assert.assertEquals(child1.correlation, 2)
        Assert.assertNotNull(child1.parentCascade)
        Assert.assertEquals(child1.parentCascade!!.identifier, parent.identifier)

        val parent1 = OneToManyParent()
        parent1.identifier = "C"
        find(parent1)

        Assert.assertEquals(parent1.correlation, 1)
        Assert.assertNotNull(parent1.childCascade)
        Assert.assertEquals(parent1.childCascade!!.size, 1)
        Assert.assertEquals(parent1.childCascade!![0].identifier, child.identifier)

        delete(child1)

        val parent2 = OneToManyParent()
        parent2.identifier = "C"
        find(parent2)

        Assert.assertEquals(0, parent2.childCascade!!.size)

    }

    @Test
    fun dtestOneToManyNoCascade() {
        val parent = OneToManyParent()
        parent.identifier = "F"
        parent.correlation = 1
        save(parent)
        find(parent)

        val child = OneToManyChild()
        child.identifier = "G"
        child.correlation = 2
        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)
        save(parent)

        // Ensure the object was not cascaded
        val parent2 = OneToManyParent()
        parent2.identifier = "F"
        find(parent2)
        Assert.assertEquals(0, parent2.childNoCascade!!.size)
    }

    @Test
    fun etestOneToManyNoCascadeNoInverse() {
        val parent = OneToManyParent()
        parent.identifier = "H"
        parent.correlation = 14
        save(parent)

        val child = OneToManyChild()
        child.identifier = "I"
        child.correlation = 22
        save(child)

        parent.childNoInverseNoCascade = ArrayList()
        parent.childNoInverseNoCascade!!.add(child)

        save(parent)

        var child1 = OneToManyChild()
        child1.identifier = "I"
        find(child1)

        // Validate the child contains the relationship
        Assert.assertEquals(child1.correlation, 22)

        val parent1 = OneToManyParent()
        parent1.identifier = "H"
        find(parent1)

        // Verify the relationship is still there
        Assert.assertEquals(parent1.correlation, 14)
        Assert.assertNotNull(parent1.childNoInverseNoCascade)
        Assert.assertEquals(parent1.childNoInverseNoCascade!!.size, 1)
        Assert.assertEquals(parent1.childNoInverseNoCascade!![0].identifier, child.identifier)

        initialize(parent1, "childNoInverseNoCascade")
        parent1.childNoInverseNoCascade!!.removeAt(0)
        save(parent1)

        // Ensure the child still loads and the parent did not wipe out the entity
        child1 = OneToManyChild()
        child1.identifier = "I"
        find(child1)
        Assert.assertEquals(22, child1.correlation)

        // Get the parent to check relationships
        val parent2 = OneToManyParent()
        parent2.identifier = "H"
        find(parent2)

        // Ensure the relationship was not removed
        Assert.assertEquals(1, parent2.childNoInverseNoCascade!!.size)
        Assert.assertEquals(14, parent2.correlation)
        child1.parentNoCascade = null

    }

    @Test
    fun ftestOneToManyNoCascadeNoInverse() {
        val parent = OneToManyParent()
        parent.identifier = "J"
        parent.correlation = 15
        save(parent)

        val child = OneToManyChild()
        child.identifier = "K"
        child.correlation = 23
        save(child)

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)

        save(parent)


        var child1 = OneToManyChild()
        child1.identifier = "K"
        find(child1)

        // Validate the child still exists
        Assert.assertEquals(child1.correlation, 23)

        val parent1 = OneToManyParent()
        parent1.identifier = "J"
        find(parent1)

        // Verify the relationship is still there
        Assert.assertEquals(parent1.correlation, 15)
        Assert.assertNotNull(parent1.childNoInverseCascade)
        Assert.assertEquals(parent1.childNoInverseCascade!!.size, 1)
        Assert.assertEquals(parent1.childNoInverseCascade!![0].identifier, child.identifier)

        //parent1.childNoInverseNoCascade = null;
        initialize(parent1, "childNoInverseCascade")
        parent1.childNoInverseCascade!!.removeAt(0)
        save(parent1)

        // Ensure the child still loads and the parent did not wipe out the entity
        child1 = OneToManyChild()
        child1.identifier = "K"
        find(child1)
        Assert.assertEquals(23, child1.correlation)

        // Get the parent to check relationships
        val parent2 = OneToManyParent()
        parent2.identifier = "J"
        find(parent2)

        // Ensure the relationship was not removed
        Assert.assertEquals(0, parent2.childNoInverseCascade!!.size)
        Assert.assertEquals(15, parent2.correlation)

    }

    @Test
    fun gtestOneToManyRemoveHasMultiple() {
        val parent = OneToManyParent()
        parent.identifier = "Z"
        parent.correlation = 30
        save(parent)

        var child = OneToManyChild()
        child.identifier = "Y"
        child.correlation = 31
        //save(child);

        var child2 = OneToManyChild()
        child2.identifier = "X"
        child2.correlation = 32
        //save(child);

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)
        parent.childNoInverseCascade!!.add(child2)

        save(parent)

        child = OneToManyChild()
        child.identifier = "Y"
        find(child)

        child2 = OneToManyChild()
        child2.identifier = "X"
        find(child2)

        // Validate the child still exists
        Assert.assertEquals(child2.correlation, 32)

        val parent1 = OneToManyParent()
        parent1.identifier = "Z"
        find(parent1)

        // Verify the relationship is still there
        Assert.assertEquals(parent1.correlation, 30)
        Assert.assertNotNull(parent1.childNoInverseCascade)
        Assert.assertEquals(parent1.childNoInverseCascade!!.size, 2)

        initialize(parent1, "childNoInverseCascade")
        parent1.childNoInverseCascade!!.removeAt(1)
        save(parent1)

        // Get the parent to check relationships
        val parent2 = OneToManyParent()
        parent2.identifier = "Z"
        find(parent2)

        // Ensure the relationship was not removed
        Assert.assertEquals(1, parent2.childNoInverseCascade!!.size)
        Assert.assertEquals(parent2.childNoInverseCascade!![0].identifier, child2.identifier)
        Assert.assertEquals(30, parent2.correlation)

    }

    @Test
    fun ftestOneToManyRemoveHasMultiple() {
        val parent = OneToManyParent()
        parent.identifier = "ZZ"
        parent.correlation = 30
        save(parent)

        var child = OneToManyChild()
        child.identifier = "YY"
        child.correlation = 31
        //save(child);

        var child2 = OneToManyChild()
        child2.identifier = "XX"
        child2.correlation = 32
        //save(child);

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)
        parent.childNoInverseCascade!!.add(child2)

        save(parent)


        child = OneToManyChild()
        child.identifier = "YY"
        find(child)

        child2 = OneToManyChild()
        child2.identifier = "XX"
        find(child2)

        // Validate the child still exists
        Assert.assertEquals(child2.correlation, 32)

        val parent1 = OneToManyParent()
        parent1.identifier = "ZZ"
        find(parent1)

        // Verify the relationship is still there
        Assert.assertEquals(parent1.correlation, 30)
        Assert.assertNotNull(parent1.childNoInverseCascade)
        Assert.assertEquals(parent1.childNoInverseCascade!!.size, 2)
        //Assert.assertEquals(parent1.childNoInverseCascade.get(0).identifier, child.identifier);
        //Assert.assertEquals(parent1.childNoInverseCascade.get(1).identifier, child2.identifier);

        //parent1.childNoInverseNoCascade = null;
        initialize(parent1, "childNoInverseCascade")
        parent1.childNoInverseCascade!!.removeAll(parent1.childNoInverseCascade!!)
        save(parent1)

        // Get the parent to check relationships
        val parent2 = OneToManyParent()
        parent2.identifier = "ZZ"
        find(parent2)

        // Ensure the relationship was not removed
        Assert.assertEquals(0, parent2.childNoInverseCascade!!.size)
        Assert.assertEquals(30, parent2.correlation)

    }
    // Test Multiple remove

}
