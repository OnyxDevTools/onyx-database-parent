package remote.list

import category.RemoteServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.AllAttributeEntity
import entities.AllAttributeForFetch
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import remote.base.RemoteBaseTest

import java.io.IOException
import java.util.ArrayList
import java.util.Date

/**
 * Created by timothy.osborn on 11/8/14.
 */
@Category(RemoteServerTests::class)
class NotInTest : RemoteBaseTest() {
    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(InitializationException::class)
    fun seedData() {
        initialize()

        var entity = AllAttributeForFetch()
        entity.id = "FIRST ONE"
        entity.stringValue = "Some test strin"
        entity.dateValue = Date(1000)
        entity.doublePrimitive = 3.3
        entity.doubleValue = 1.1
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1000L
        entity.longValue = 323L
        entity.intValue = 3
        entity.intPrimitive = 3
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE1"
        entity.stringValue = "Some test strin1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        entity.intValue = 2
        entity.intPrimitive = 4
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE2"
        entity.stringValue = "Some test strin1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        entity.intValue = 2
        entity.intPrimitive = 4
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE3"
        entity.stringValue = "Some test strin2"
        entity.dateValue = Date(1002)
        entity.doublePrimitive = 3.32
        entity.doubleValue = 1.12
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1001L
        entity.longValue = 321L
        entity.intValue = 5
        entity.intPrimitive = 6
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE3"
        entity.stringValue = "Some test strin3"
        entity.dateValue = Date(1022)
        entity.doublePrimitive = 3.35
        entity.doubleValue = 1.126
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1301L
        entity.longValue = 322L
        entity.intValue = 6
        entity.intPrimitive = 3
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE4"
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE5"
        save(entity)
        find(entity)
    }

    @Test
    @Throws(OnyxException::class)
    fun testNotInString() {
        val stringArray = ArrayList<Any>()
        stringArray.add("Some test strin1")
        stringArray.add("Some test strin3")
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_IN, stringArray)
        val results = manager!!.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(5, results.size.toLong())
        Assert.assertNotEquals(results[0].stringValue, "Some test strin1")
        Assert.assertNotEquals(results[0].stringValue, "Some test strin1")
    }

    @Test
    @Throws(OnyxException::class)
    fun testNumberNotIn() {
        val stringArray = ArrayList<Any>()
        stringArray.add(322L)
        stringArray.add(321L)
        val criteriaList = QueryCriteria("longValue", QueryCriteriaOperator.NOT_IN, stringArray)
        val results = manager!!.list<AllAttributeEntity>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(5, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class)
    fun testDateNotIn() {
        val stringArray = ArrayList<Any>()
        stringArray.add(Date(1000))
        stringArray.add(Date(1001))
        val criteriaList = QueryCriteria("dateValue", QueryCriteriaOperator.NOT_IN, stringArray)
        val results = manager!!.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(6, results.size.toLong())
        Assert.assertNotEquals(results[0].dateValue, Date(1000))
        Assert.assertNotEquals(results[0].dateValue, Date(1001))
    }

    @Test
    @Throws(OnyxException::class)
    fun testDoubleNotIn() {
        val stringArray = ArrayList<Any>()
        stringArray.add(1.126)
        stringArray.add(1.11)
        val criteriaList = QueryCriteria("doubleValue", QueryCriteriaOperator.NOT_IN, stringArray)
        val results = manager!!.list<AllAttributeEntity>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(5, results.size.toLong())

    }
}
