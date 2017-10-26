package web.relationship

import category.WebServerTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.collections.LazyRelationshipCollection
import entities.relationship.ManyToManyChild
import entities.relationship.ManyToManyParent
import org.junit.*
import org.junit.experimental.categories.Category
import web.base.BaseTest

import java.io.IOException
import java.util.ArrayList

/**
 * Created by timothy.osborn on 2/10/15.
 */
@Ignore
@Category(WebServerTests::class)
class LazyCollectionTest : BaseTest() {
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
    fun testExists() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        save(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        find(parent)

        Assert.assertTrue(parent.childCascade is LazyRelationshipCollection<*>)
        Assert.assertTrue(parent.childCascade!!.contains(parent.childCascade!![0]))
    }

    @Test
    fun testSize() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        save(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        find(parent)

        Assert.assertTrue(parent.childCascade is LazyRelationshipCollection<*>)
        Assert.assertTrue(parent.childCascade!!.size == 1)
    }

    @Test
    fun testAdd() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        save(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        find(parent)

        val child2 = ManyToManyChild()
        child2.identifier = "C"
        child2.correlation = 2
        save(child2)

        parent.childCascade!!.add(child2)
        Assert.assertTrue(parent.childCascade is LazyRelationshipCollection<*>)
        Assert.assertTrue(parent.childCascade!!.size == 2)
        Assert.assertTrue(parent.childCascade!!.contains(child2))
    }

    @Test
    fun testEmpty() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        save(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        find(parent)

        Assert.assertFalse(parent.childCascade!!.isEmpty())
    }

    @Test
    fun testClear() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        save(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        find(parent)

        parent.childCascade!!.clear()

        Assert.assertTrue(parent.childCascade!!.isEmpty())
    }

    @Test
    fun testSet() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        save(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        find(parent)

        val child2 = ManyToManyChild()
        child2.identifier = "C"
        child2.correlation = 2
        save(child2)

        parent.childCascade!!.set(0, child2)
        Assert.assertTrue(parent.childCascade is LazyRelationshipCollection<*>)
        Assert.assertTrue(parent.childCascade!!.size == 1)
        Assert.assertTrue(parent.childCascade!!.contains(child2))
    }

    @Test
    fun testRemove() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        save(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        find(parent)


        parent.childCascade!!.removeAt(0)
        Assert.assertTrue(parent.childCascade is LazyRelationshipCollection<*>)
        Assert.assertTrue(parent.childCascade!!.size == 0)
    }

    @Test
    fun testRemoveByObject() {
        var parent = ManyToManyParent()
        parent.identifier = "A"
        parent.correlation = 1
        save(parent)

        val child = ManyToManyChild()
        child.identifier = "B"
        child.correlation = 2
        save(child)

        parent.childCascade = ArrayList()
        parent.childCascade!!.add(child)
        save(parent)

        parent = ManyToManyParent()
        parent.identifier = "A"
        find(parent)


        parent.childCascade!!.remove(parent.childCascade!![0])
        Assert.assertTrue(parent.childCascade is ArrayList<*>)
        Assert.assertTrue(parent.childCascade!!.size == 0)
    }
}
