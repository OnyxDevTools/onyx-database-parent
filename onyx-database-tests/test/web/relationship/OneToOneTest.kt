package web.relationship

import category.WebServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import entities.relationship.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import web.base.BaseTest

import java.io.IOException

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.Assert.assertNull

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category(WebServerTests::class)
class OneToOneTest : BaseTest() {

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
    fun testOneToOneCascade() {
        val parent = OneToOneParent()
        parent.correlation = 1
        parent.identifier = "A"

        save(parent)
        find(parent)
        assertEquals(1, parent.correlation)
        assertNull(parent.child)

        parent.child = OneToOneChild()
        parent.child!!.identifier = "B"
        parent.child!!.correlation = 2

        save(parent)
        find(parent)

        assertEquals(1, parent.correlation)
        assertNotNull(parent.child)
        assertEquals(2, parent.child!!.correlation)

        val child = OneToOneChild()
        child.identifier = "B"
        find(child)
        //        initialize(child, "parent");

        assertEquals(2, child.correlation)
        assertNotNull(child.parent)
        assertEquals(1, child.parent!!.correlation)

    }

    @Test
    fun testOneToOneNoCascade() {
        val child = OneToOneChild()
        child.identifier = "D"
        child.correlation = 4

        save(child)

        val parent = OneToOneParent()
        parent.correlation = 3
        parent.identifier = "C"

        save(parent)
        find(child)

        assertEquals(4, child.correlation)
        assertNull(child.parent)

        find(parent)
        assertEquals(3, parent.correlation)
        assertNull(parent.child)

        child.parent = parent
        save(child)
        find(parent)

        assertEquals(3, parent.correlation)
        assertNotNull(parent.child)
        assertEquals(4, parent.child!!.correlation)

        find(child)
        //        initialize(child, "parent");

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
        save(parent)

        // Save the child
        var child = OneToOneChild()
        child.correlation = 11
        child.identifier = "F"
        save(child)

        parent.child = child
        save(parent)

        parent = OneToOneParent()
        parent.identifier = "E"
        find(parent)

        Assert.assertNotNull(parent.child)

        find(child)

        Assert.assertNotNull(child.parent)
        assertEquals(child.parent!!.identifier, parent.identifier)
        assertEquals(parent.child!!.identifier, child.identifier)

        parent.child = null
        save(parent)
        find(parent)
        child = OneToOneChild()
        child.identifier = "F"
        find(child)

        Assert.assertNull(child.parent)
        Assert.assertNull(parent.child)
    }

    @Test
    fun testDeleteCascade() {
        // Save The Parent
        var parent = OneToOneParent()
        parent.correlation = 10
        parent.identifier = "G"
        save(parent)

        // Save the child
        var child = OneToOneChild()
        child.correlation = 11
        child.identifier = "H"
        save(child)

        parent.cascadeChild = child
        save(parent)

        parent = OneToOneParent()
        parent.identifier = "G"
        parent = find(parent) as OneToOneParent

        Assert.assertNotNull(parent.cascadeChild)

        find(child)

        Assert.assertNotNull(child.cascadeParent)
        assertEquals(child.cascadeParent!!.identifier, parent.identifier)
        assertEquals(parent.cascadeChild!!.identifier, child.identifier)

        parent.cascadeChild = null
        save(parent)
        parent = find(parent) as OneToOneParent
        child = OneToOneChild()
        child.identifier = "H"

        var exceptionThrown = false
        try {
            child = manager.find(child)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                exceptionThrown = true
            }
        }

        Assert.assertTrue(exceptionThrown)
        // Ensure this bad boy was deleted
        //Assert.assertEquals(child.correlation, 11);
        //Assert.assertNull(child.parent);

    }

    @Test
    fun testNoInverseCascade() {
        // Save The Parent
        var parent = OneToOneParent()
        parent.correlation = 10
        parent.identifier = "G"
        save(parent)

        // Save the child
        var child = OneToOneChild()
        child.correlation = 11
        child.identifier = "H"
        save(child)

        parent.childNoInverseCascade = child
        save(parent)

        parent = OneToOneParent()
        parent.identifier = "G"
        find(parent)

        Assert.assertNotNull(parent.childNoInverseCascade)

        find(child)

        assertEquals(parent.childNoInverseCascade!!.identifier, child.identifier)

        parent.childNoInverseCascade = null

        parent.cascadeChild = null
        save(parent)
        find(parent)

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

        Assert.assertTrue(exceptionThrown)
        // Ensure this bad boy was deleted
        //Assert.assertEquals(child.correlation, 11);
        //Assert.assertNull(child.cascadeParent);

    }

    @Test
    fun testNoInverse() {
        // Save The Parent
        var parent = OneToOneParent()
        parent.correlation = 133
        parent.identifier = "G"
        save(parent)

        // Save the child
        var child = OneToOneChild()
        child.correlation = 112
        child.identifier = "I"
        save(child)

        parent.childNoInverse = child
        save(parent)

        parent = OneToOneParent()
        parent.identifier = "G"
        find(parent)

        Assert.assertNotNull(parent.childNoInverse)
        assertEquals(parent.childNoInverse!!.identifier, child.identifier)

        parent.childNoInverse = null

        parent.cascadeChild = null
        save(parent)
        find(parent)

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

        Assert.assertFalse(exceptionThrown)
        // Ensure this bad boy was deleted
        Assert.assertEquals(child.correlation.toLong(), 112)

    }

    @Test
    fun testInverseCascadeDelete() {
        // Save The Parent
        var parent = OneToOneParent()
        parent.correlation = 10
        parent.identifier = "Z"
        save(parent)

        // Save the child
        var child = OneToOneChild()
        child.correlation = 11
        child.identifier = "X"
        save(child)

        parent.cascadeChild = child
        save(parent)

        parent = OneToOneParent()
        parent.identifier = "Z"
        find(parent)

        Assert.assertNotNull(parent.cascadeChild)

        find(child)

        assertEquals(parent.cascadeChild!!.identifier, child.identifier)

        save(parent)
        find(parent)
        delete(parent)

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

        Assert.assertTrue(exceptionThrown)

    }


    @Test
    fun testRecursiveOneToOne() {
        val parent = OneToOneRecursive()
        parent.id = 1
        parent.child = OneToOneRecursiveChild()
        parent.child!!.id = 2
        parent.child!!.third = OneToOneThreeDeep()
        parent.child!!.third!!.id = 3


        save(parent)

        val newParent = OneToOneRecursive()
        newParent.id = 1
        find(newParent)

        assertNotNull(parent)
        assertNotNull(parent.child)
        assertNotNull(parent.child!!.third)

        assertNotNull(parent.child!!.parent)
        assertNotNull(parent.child!!.third!!.parent)

    }
}
