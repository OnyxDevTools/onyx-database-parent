package database.list

import com.onyx.extension.IN
import com.onyx.extension.delete
import com.onyx.extension.from
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
class InTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

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
    fun testInEquals() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, ("stringValue" IN arrayListOf("Some test strin1","Some test strin3")))
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
