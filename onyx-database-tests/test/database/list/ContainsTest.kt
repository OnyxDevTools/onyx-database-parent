package database.list

import com.onyx.persistence.query.cont
import com.onyx.persistence.query.startsWith
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeEntity
import entities.AllAttributeForFetch
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class ContainsTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testStringContains() {
        val results = manager.list<AllAttributeEntity>(AllAttributeForFetch::class.java, "stringValue" cont "Some test string")
        assertEquals(4, results.size, "Expected 4 results for 'stringValue' containing 'Some test string'")
    }

    @Test
    fun testContainsStringId() {
        val results = manager.list<AllAttributeEntity>(AllAttributeForFetch::class.java, "id" startsWith "FIRST ONE")
        assertEquals(6, results.size, "Expected 6 results for 'id' containing 'FIRST ONE'")
    }

    @Test
    fun testStringStartsWith() {
        val results = manager.list<AllAttributeEntity>(AllAttributeForFetch::class.java, "stringValue" cont "ome test string")
        assertEquals(4, results.size, "Expected 4 results for 'stringValue' containing 'ome test string'")
    }

    @Test
    fun testContainsStartsWith() {
        val results = manager.list<AllAttributeEntity>(AllAttributeForFetch::class.java, "id" cont "IRST ONE")
        assertEquals(6, results.size, "Expected 6 results for 'id' containing 'IRST ONE'")
    }
}