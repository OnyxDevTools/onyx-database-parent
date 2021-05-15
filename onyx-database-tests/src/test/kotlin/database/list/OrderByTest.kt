package database.list

import com.onyx.exception.OnyxException
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class OrderByTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testOrderByString() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("longValue", false), QueryOrder("id", false))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        assertEquals("FIRST ONE", results[0].id, "Sort order is invalid")
        assertEquals("FIRST ONE3", results[1].id, "Sort order is invalid")
        assertEquals("FIRST ONE2", results[2].id, "Sort order is invalid")
        assertEquals("FIRST ONE1", results[3].id, "Sort order is invalid")
        assertEquals("FIRST ONE5", results[4].id, "Sort order is invalid")
        assertEquals("FIRST ONE4", results[5].id, "Sort order is invalid")
    }

    @Test
    @Throws(OnyxException::class)
    fun testOrderByNumber() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("longValue", false))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        assertEquals("FIRST ONE", results[0].id, "Sort order is invalid")
    }

    @Test
    fun testOrderByDoubleDesc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("doubleValue", false))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        assertEquals("FIRST ONE3", results[0].id, "Sort order is invalid")
    }

    @Test
    fun testOrderByDoubleAsc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("doubleValue", true), QueryOrder("id", true))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        assertEquals("FIRST ONE4", results[0].id, "Sort order is invalid")
    }

    @Test
    fun testOrderByIntDesc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("intValue", false))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        assertEquals("FIRST ONE3", results[0].id, "Sort order is invalid")
    }

    @Test
    fun testOrderByIntAsc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("intValue", true), QueryOrder("id", true))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        assertEquals("FIRST ONE1", results[2].id, "Sort order is invalid")
    }

    @Test
    fun testOrderByDateDesc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("dateValue", false))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        assertEquals("FIRST ONE3", results[0].id, "Sort order is invalid")
    }

    @Test
    fun testOrderByDateAsc() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ")
        val orderList = arrayOf(QueryOrder("dateValue", true), QueryOrder("id", true))

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList, orderList)
        assertEquals("FIRST ONE4", results[0].id, "Sort order is invalid")
        assertEquals("FIRST ONE", results[2].id, "Sort order is invalid")
    }
}
