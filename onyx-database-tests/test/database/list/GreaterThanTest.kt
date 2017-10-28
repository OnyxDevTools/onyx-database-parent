package database.list

import com.onyx.extension.delete
import com.onyx.extension.from
import com.onyx.extension.gt
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
class GreaterThanTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

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
    fun testStringIDGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("id" gt "FIRST ONE1"))
        assertEquals(4, results.size, "Expected 4 query results")
    }

    @Test
    fun testStringStringGreaterThan() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("stringValue" gt "Some test strin2"))
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
