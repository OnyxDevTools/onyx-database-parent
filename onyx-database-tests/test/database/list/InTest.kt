package database.list

import com.onyx.extension.IN
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class InTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testInEquals() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("stringValue" IN arrayListOf("Some test string1","Some test string3")))
        assertEquals(3, results.size, "Expected 3 results")
    }

    @Test
    fun testInString() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("id" IN arrayListOf("FIRST ONE3", "FIRST ONE1")))
        assertEquals(2, results.size, "Expected 2 results")
    }

    @Test
    fun testNumberIn() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "longValue" IN arrayListOf(322L,321L))
        assertEquals(3, results.size, "Expected 3 results")
        assertEquals(322L, results[0].longValue!!, "longValue has wrong value")
    }

    @Test
    fun testDateIn() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("dateValue" IN arrayListOf(Date(1000),Date(1001))))
        assertEquals(3, results.size, "Expected 3 results")
    }

    @Test
    fun testDoubleIn() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("doubleValue" IN arrayListOf(1.126,1.11)))
        assertEquals(3, results.size, "Expected 3 results")
    }
}
