package web.list

import category.WebServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.AllAttributeForFetch
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import web.base.BaseTest

import java.io.IOException
import java.util.Date

/**
 * Created by timothy.osborn on 11/6/14.
 */
@Category(WebServerTests::class)
class MutableEqualsNullTest : BaseTest() {
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
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testStringEqualsNull() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(2, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIntEqualsNull() {
        val criteriaList = QueryCriteria("intValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(2, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testLongEqualsNull() {
        val criteriaList = QueryCriteria("longValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(2, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testDateEqualsNull() {
        val criteriaList = QueryCriteria("dateValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(2, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testDoubleEqualsNull() {
        val criteriaList = QueryCriteria("doubleValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(2, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testBooleanEqualsNull() {
        val criteriaList = QueryCriteria("booleanValue", QueryCriteriaOperator.IS_NULL)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(2, results.size.toLong())
    }
}