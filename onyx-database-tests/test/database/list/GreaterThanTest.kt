package database.list

import com.onyx.persistence.query.gt
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class GreaterThanTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testStringIDGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("id" gt "FIRST ONE1"))
        assertEquals(4, results.size, "Expected 4 query results")
    }

    @Test
    fun testStringStringGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("stringValue" gt "Some test string2"))
        assertEquals(1, results.size, "Expected 1 query result")
    }

    @Test
    fun testLongGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("longValue" gt 322L))
        assertEquals(1, results.size, "Expected 1 query result")
    }

    @Test
    fun testPrimitiveLongGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("longPrimitive" gt 1002L))
        assertEquals(1, results.size, "Expected 1 query result")
    }

    @Test
    fun testIntegerGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("intValue" gt 3))
        assertEquals(1, results.size, "Expected 1 query result")
    }

    @Test
    fun testPrimitiveIntegerGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("intPrimitive" gt 4))
        assertEquals(0, results.size, "Expected no results")
    }

    @Test
    fun testDoubleGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("doubleValue" gt 1.11))
        assertEquals(1, results.size, "Expected 1 query result")
    }

    @Test
    fun testPrimitiveDoubleGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("doublePrimitive" gt 3.32))
        assertEquals(1, results.size, "Expected 1 query result")
    }

    @Test
    fun testDateGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("dateValue" gt Date(1001)))
        assertEquals(1, results.size, "Expected 1 query result")
    }
}
