package remote.relationship

import category.RemoteServerTests
import com.onyx.exception.OnyxException
import entities.relationship.ManyToManyChild
import entities.relationship.ManyToManyParent
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import remote.base.RemoteBaseTest

import java.io.IOException
import java.util.ArrayList

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category(RemoteServerTests::class)
class ManyToManyTest : RemoteBaseTest() {

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
    fun testManyToManyBasic() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        save(parent)

        var child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        save(child)

        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        find(parent)

        Assert.assertEquals(1, parent.correlation.toLong())
        Assert.assertEquals(1, parent.childNoCascade!!.size.toLong())
        Assert.assertEquals(child.identifier, parent.childNoCascade!![0].identifier)

        child = ManyToManyChild()
        child.identifier = "B"
        find(child)

        Assert.assertEquals(2, child.correlation.toLong())
        Assert.assertEquals(1, child.parentNoCascade!!.size.toLong())
        Assert.assertEquals(parent.identifier, child.parentNoCascade!![0].identifier)
    }

    @Test
    fun testManyToManyMultiple() {
        var parent = ManyToManyParent()
        parent.identifier = "C"
        parent.correlation = 1
        save(parent)

        var child = ManyToManyChild()
        child.identifier = "D"
        child.correlation = 2
        save(child)

        val child2 = ManyToManyChild()
        child2.identifier = "E"
        child2.correlation = 3
        save(child2)

        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)
        parent.childNoCascade!!.add(child2)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "C"
        find(parent)

        Assert.assertEquals(1, parent.correlation.toLong())
        Assert.assertEquals(2, parent.childNoCascade!!.size.toLong())
        Assert.assertEquals(child.identifier, parent.childNoCascade!![0].identifier)

        child = ManyToManyChild()
        child.identifier = "D"
        find(child)

        Assert.assertEquals(2, child.correlation.toLong())
        Assert.assertEquals(1, child.parentNoCascade!!.size.toLong())
        Assert.assertEquals(parent.identifier, child.parentNoCascade!![0].identifier)
    }

    @Test
    fun testManyToManyNoCascadeRemove() {
        var parent = ManyToManyParent()
        parent.identifier = "F"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "G"
        child.correlation = 2
        save(child)

        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)
        save(parent)

        parent.childNoCascade!!.remove(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "F"
        find(parent)

        Assert.assertEquals(1, parent.correlation.toLong())
        Assert.assertEquals(1, parent.childNoCascade!!.size.toLong())

    }

    @Test
    fun testManyToManyCascadeRemove() {
        var parent = ManyToManyParent()
        parent.identifier = "H"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "I"
        child.correlation = 2
        save(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        save(parent)

        parent.childCascade!!.remove(parent.childCascade!![0])
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "H"
        find(parent)

        Assert.assertEquals(1, parent.correlation.toLong())
        Assert.assertEquals(0, parent.childCascade!!.size.toLong())

    }

    @Test
    fun testManyToManyCascadeDelete() {
        var parent = ManyToManyParent()
        parent.identifier = "J"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "K"
        child.correlation = 2
        save(child)

        parent.childNoCascade = ArrayList()
        parent.childNoCascade!!.add(child)
        save(parent)

        delete(child)

        parent = ManyToManyParent()
        parent.identifier = "J"
        find(parent)

        Assert.assertEquals(1, parent.correlation.toLong())
        Assert.assertEquals(0, parent.childNoCascade!!.size.toLong())
    }

    @Test
    fun testManyToManyNoInverse() {
        var parent = ManyToManyParent()
        parent.identifier = "J"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "K"
        child.correlation = 2
        save(child)

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "J"
        find(parent)

        Assert.assertEquals(1, parent.correlation.toLong())
        Assert.assertEquals(1, parent.childNoInverseCascade!!.size.toLong())
    }

    @Test
    fun testManyToManyNoInverseDelete() {
        var parent = ManyToManyParent()
        parent.identifier = "Z"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "P"
        child.correlation = 2
        save(child)

        parent.childNoInverseCascade = ArrayList()
        parent.childNoInverseCascade!!.add(child)
        save(parent)

        parent.childNoInverseCascade!!.remove(parent.childNoInverseCascade!![0])
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "Z"
        find(parent)

        Assert.assertEquals(1, parent.correlation.toLong())
        Assert.assertEquals(0, parent.childNoInverseCascade!!.size.toLong())
    }
}
