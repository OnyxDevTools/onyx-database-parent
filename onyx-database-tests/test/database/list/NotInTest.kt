package database.list

import com.onyx.extension.notIn
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeEntity
import entities.AllAttributeForFetch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(Parameterized::class)
class NotInTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testNotInString() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("stringValue" notIn arrayListOf("Some test string1","Some test string3")))
        assertEquals(3, results.size, "Expected 3 results")
        assertNotEquals("Some test string1", results[0].stringValue)
        assertNotEquals("Some test string3", results[0].stringValue)
    }

    @Test
    fun testNumberNotIn() {
        val results = manager.list<AllAttributeEntity>(AllAttributeForFetch::class.java, "longValue" notIn arrayListOf(322L,321L))
        assertEquals(3, results.size, "Expected 3 results")
    }

    @Test
    fun testDateNotIn() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "dateValue" notIn arrayListOf(Date(1000), Date(1001)))
        assertEquals(3, results.size, "Expected 3 results")
        assertNotEquals(results[0].dateValue, Date(1000), "Incorrect record returned")
        assertNotEquals(results[0].dateValue, Date(1001), "Incorrect record returned")
    }

    @Test
    fun testDoubleNotIn() {
        val results = manager.list<AllAttributeEntity>(AllAttributeForFetch::class.java, "doubleValue" notIn arrayListOf(1.126,1.11))
        assertEquals(3, results.size, "Expected 3 results")
    }
}
