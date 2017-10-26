package embedded.list

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import embedded.base.BaseTest
import entities.AllAttributeForFetch
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.util.Date

/**
 * Created by timothy.osborn on 11/8/14.
 */
@Category(EmbeddedDatabaseTests::class)
class LessThanEqualTest : BaseTest() {
    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(OnyxException::class)
    fun seedData() {
        initialize()

        val deleteQuery = Query(AllAttributeForFetch::class.java)
        manager.executeDelete(deleteQuery)

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
    fun testStringIDLessThanEqual() {
        val criteriaList = QueryCriteria("id", QueryCriteriaOperator.LESS_THAN_EQUAL, "FIRST ONE3")
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(4, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testStringStringLessThanEqual() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.LESS_THAN_EQUAL, "Some test strin2")
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(5, results.size.toLong())
    }


    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testLongLessThanEqual() {
        val criteriaList = QueryCriteria("longValue", QueryCriteriaOperator.LESS_THAN_EQUAL, 323L)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(6, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testPrimitiveLongLessThanEqual() {
        val criteriaList = QueryCriteria("longPrimitive", QueryCriteriaOperator.LESS_THAN_EQUAL, 1000L)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(3, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testIntegerLessThanEqual() {
        val criteriaList = QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN_EQUAL, 3)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(5, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testPrimitiveIntegerLessThanEqual() {
        val criteriaList = QueryCriteria("intPrimitive", QueryCriteriaOperator.LESS_THAN_EQUAL, 4)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(6, results.size.toLong())
    }


    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testDoubleLessThanEqual() {
        val criteriaList = QueryCriteria("doubleValue", QueryCriteriaOperator.LESS_THAN_EQUAL, 1.11)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(5, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testPrimitiveDoubleLessThanEqual() {
        val criteriaList = QueryCriteria("doublePrimitive", QueryCriteriaOperator.LESS_THAN_EQUAL, 3.32)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(5, results.size.toLong())
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testDateLessThanEqual() {
        val criteriaList = QueryCriteria("dateValue", QueryCriteriaOperator.LESS_THAN_EQUAL, Date(1001))
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        Assert.assertEquals(5, results.size.toLong())
    }

}
