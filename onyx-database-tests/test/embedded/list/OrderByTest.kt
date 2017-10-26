package embedded.list

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import embedded.base.BaseTest
import entities.AllAttributeEntity
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
class OrderByTest : BaseTest() {
    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(OnyxException::class)
    fun seedData() {
        initialize()

        val deleteQuery = Query(AllAttributeEntity::class.java)
        manager.executeDelete(deleteQuery)

        var entity = AllAttributeForFetch()
        entity.id = "FIRST ONE"
        entity.stringValue = "Some test strin"
        entity.dateValue = Date(1483737266383L)
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
        entity.dateValue = Date(1483737267383L)
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
        entity.dateValue = Date(1483737268383L)
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
        entity.dateValue = Date(1483737367383L)
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
        entity.dateValue = Date(1493737267383L)
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
    fun testOrderByString() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("longValue", false), QueryOrder("id", false))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        Assert.assertEquals(results[0].id, "FIRST ONE")
        Assert.assertEquals(results[1].id, "FIRST ONE3")
        Assert.assertEquals(results[2].id, "FIRST ONE2")
        Assert.assertEquals(results[3].id, "FIRST ONE1")
        Assert.assertEquals(results[4].id, "FIRST ONE5")
        Assert.assertEquals(results[5].id, "FIRST ONE4")
    }

    @Test
    @Throws(OnyxException::class)
    fun testOrderByNumber() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("longValue", false))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        Assert.assertEquals(results[0].id, "FIRST ONE")
    }

    @Test
    @Throws(OnyxException::class)
    fun testOrderByDoubleDesc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("doubleValue", false))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        Assert.assertEquals(results[0].id, "FIRST ONE3")
    }

    @Test
    @Throws(OnyxException::class)
    fun testOrderByDoubleAsc() {

        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("doubleValue", true), QueryOrder("id", true))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        Assert.assertEquals(results[0].id, "FIRST ONE4")
    }

    @Test
    @Throws(OnyxException::class)
    fun testOrderByIntDesc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("intValue", false))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        Assert.assertEquals(results[0].id, "FIRST ONE3")
    }

    @Test
    @Throws(OnyxException::class)
    fun testOrderByIntAsc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("intValue", true), QueryOrder("id", true))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        Assert.assertEquals(results[2].id, "FIRST ONE1")
    }

    @Test
    @Throws(OnyxException::class)
    fun testOrderByDateDesc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("dateValue", false))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        Assert.assertEquals(results[0].id, "FIRST ONE3")
    }

    @Test
    @Throws(OnyxException::class)
    fun testOrderByDateAsc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("dateValue", true), QueryOrder("id", true))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        Assert.assertEquals(results[0].id, "FIRST ONE4")
        Assert.assertEquals(results[2].id, "FIRST ONE")
    }
}
