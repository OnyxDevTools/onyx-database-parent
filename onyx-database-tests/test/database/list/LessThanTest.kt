package database.list

import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class LessThanTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testStringIDLessThan() {
        val criteriaList = QueryCriteria("id", QueryCriteriaOperator.LESS_THAN, "FIRST ONE3")
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(3, results.size, "Expected 3 results")
    }

    @Test
    fun testStringStringLessThan() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.LESS_THAN, "Some test strin2")
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(5, results.size, "Expected 5 results")
    }

    @Test
    fun testLongLessThan() {
        val criteriaList = QueryCriteria("longValue", QueryCriteriaOperator.LESS_THAN, 323L)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(5, results.size, "Expected 5 results")
    }

    @Test
    fun testPrimitiveLongLessThan() {
        val criteriaList = QueryCriteria("longPrimitive", QueryCriteriaOperator.LESS_THAN, 3L)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(2, results.size, "Expected 2 results")
    }

    @Test
    fun testIntegerLessThan() {
        val criteriaList = QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 3)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(4, results.size, "Expected 4 results")
    }

    @Test
    fun testPrimitiveIntegerLessThan() {
        val criteriaList = QueryCriteria("intPrimitive", QueryCriteriaOperator.LESS_THAN, 4)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(4, results.size, "Expected 4 results")
    }

    @Test
    fun testDoubleLessThan() {
        val criteriaList = QueryCriteria("doubleValue", QueryCriteriaOperator.LESS_THAN, 1.11)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(3, results.size, "Expected 3 results")
    }

    @Test
    fun testPrimitiveDoubleLessThan() {
        val criteriaList = QueryCriteria("doublePrimitive", QueryCriteriaOperator.LESS_THAN, 3.32)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(5, results.size, "Expected 5 results")
    }

    @Test
    fun testDateLessThan() {
        val criteriaList = QueryCriteria("dateValue", QueryCriteriaOperator.LESS_THAN, Date(1001))
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(3, results.size, "Expected 3 results")
    }
}
