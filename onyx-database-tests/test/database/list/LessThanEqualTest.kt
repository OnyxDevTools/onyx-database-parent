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
class LessThanEqualTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testStringIDLessThanEqual() {
        val criteriaList = QueryCriteria("id", QueryCriteriaOperator.LESS_THAN_EQUAL, "FIRST ONE3")
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(4, results.size, "Expected 4 results")
    }

    @Test
    fun testStringStringLessThanEqual() {
        val criteriaList = QueryCriteria("stringValue", QueryCriteriaOperator.LESS_THAN_EQUAL, "Some test string2")
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(5, results.size, "Expected 5 results")
    }

    @Test
    fun testLongLessThanEqual() {
        val criteriaList = QueryCriteria("longValue", QueryCriteriaOperator.LESS_THAN_EQUAL, 323L)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(6, results.size, "Expected 6 results")
    }

    @Test
    fun testPrimitiveLongLessThanEqual() {
        val criteriaList = QueryCriteria("longPrimitive", QueryCriteriaOperator.LESS_THAN_EQUAL, 1000L)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(3, results.size, "Expected 3 results")
    }

    @Test
    fun testIntegerLessThanEqual() {
        val criteriaList = QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN_EQUAL, 3)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(5, results.size, "Expected 5 results")
    }

    @Test
    fun testPrimitiveIntegerLessThanEqual() {
        val criteriaList = QueryCriteria("intPrimitive", QueryCriteriaOperator.LESS_THAN_EQUAL, 4)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(6, results.size, "Expected 6 results")
    }

    @Test
    fun testDoubleLessThanEqual() {
        val criteriaList = QueryCriteria("doubleValue", QueryCriteriaOperator.LESS_THAN_EQUAL, 1.11)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(5, results.size, "Expected 5 results")
    }

    @Test
    fun testPrimitiveDoubleLessThanEqual() {
        val criteriaList = QueryCriteria("doublePrimitive", QueryCriteriaOperator.LESS_THAN_EQUAL, 3.32)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(5, results.size, "Expected 5 results")
    }

    @Test
    fun testDateLessThanEqual() {
        val criteriaList = QueryCriteria("dateValue", QueryCriteriaOperator.LESS_THAN_EQUAL, Date(1001))
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteriaList)
        assertEquals(5, results.size, "Expected 5 results")
    }
}
