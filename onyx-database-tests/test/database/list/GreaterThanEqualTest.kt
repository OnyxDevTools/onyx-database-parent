package database.list

import com.onyx.extension.delete
import com.onyx.extension.from
import com.onyx.extension.gte
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.AllAttributeForFetch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class GreaterThanEqualTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun seedData() {

        manager.from(AllAttributeForFetch::class).delete()

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
        manager.saveEntity<IManagedEntity>(entity)

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
        manager.saveEntity<IManagedEntity>(entity)

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
        manager.saveEntity<IManagedEntity>(entity)

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
        manager.saveEntity<IManagedEntity>(entity)

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
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE4"
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE5"
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test
    fun testStringIDGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("id" gte "FIRST ONE1"))
        assertEquals(5, results.size, "Expected 5 results from query")
    }

    @Test
    fun testStringStringGreaterThanEqual() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("stringValue" gte  "Some test strin2"))
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
        assertEquals(2, results.size, "Expected 2 results from query")
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