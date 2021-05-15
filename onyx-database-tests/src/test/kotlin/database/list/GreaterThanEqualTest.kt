package database.list

import com.onyx.persistence.query.gte
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class GreaterThanEqualTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testStringIDGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("id" gte "FIRST ONE1"))
        assertEquals(5, results.size, "Expected 5 results from query")
    }

    @Test
    fun testStringStringGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("stringValue" gte  "Some test string2"))
        assertEquals(1, results.size, "Expected 1 results from query")
    }

    @Test
    fun testLongGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("longValue" gte  322L))
        assertEquals(4, results.size, "Expected 4 results from query")
    }

    @Test
    fun testPrimitiveLongGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("longPrimitive" gte  1002L))
        assertEquals(3, results.size, "Expected 3 results from query")
    }

    @Test
    fun testIntegerGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("intValue" gte  3))
        assertEquals(2, results.size, "Expected 2 results from query")
    }

    @Test
    fun testPrimitiveIntegerGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("intPrimitive" gte  4))
        assertEquals(1, results.size, "Expected 1 result from query")
    }

    @Test
    fun testDoubleGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("doubleValue" gte  1.11))
        assertEquals(3, results.size, "Expected 3 results from query")
    }

    @Test
    fun testPrimitiveDoubleGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("doublePrimitive" gte  3.31))
        assertEquals(3, results.size, "Expected 3 results from query")
    }

    @Test
    fun testDateGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("dateValue" gte  Date(1001)))
        assertEquals(3, results.size, "Expected 3 results from query")
    }
}