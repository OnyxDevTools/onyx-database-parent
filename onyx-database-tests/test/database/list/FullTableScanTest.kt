package database.list

import com.onyx.extension.*
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.AllAttributeForFetch
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class FullTableScanTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

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

        entity.longValue = 23L
        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE4"
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE5"
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test
    fun testBasicAnd() {
        val criteria = ("stringValue" eq "Some test strin1")
                        .and ("stringValue" startsWith "Some test str")
                        .and ("id" eq "FIRST ONE1")
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        assertEquals(1, results.size, "Expected 1 result matching criteria")
        assertEquals("Some test strin1", results[0].stringValue, "stringValue was not correct")
    }

    @Test
    fun testBasicAndOr() {
        val criteria = ("stringValue" eq "Some test strin1")
                    .or("stringValue" eq "Some test strin3")

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        assertEquals(3, results.size, "Expected 3 entities matching criteria")
    }

    @Test
    fun testBasicOrsSub() {
        val criteria = ("stringValue" cont "Some tes") or ("stringValue" cont "Some test strin1")
        assertEquals(4, manager.list<IManagedEntity>(AllAttributeForFetch::class.java, criteria).size, "Expected 4 Results in query")
    }

    @Test
    fun testBasicAndSub() {
        val criteria =        ("stringValue" eq   "Some test strin1")
                        .and (("stringValue" cont "Some tes") or ("intValue" neq 2))
                        .or   ("stringValue" eq   "Some test strin2")
        assertEquals(2, manager.list<IManagedEntity>(AllAttributeForFetch::class.java, criteria).size, "Expected 2 results from query")
    }

    @Test
    fun testBasicOrSub() {
        val criteria =       ("stringValue"  eq   "Some test strin1")
                        .or (("stringValue"  cont "Some tes") or ("intValue" neq 2))
                        .or ( "stringValue"  eq   "Some test strin2")
        assertEquals(6, manager.list<IManagedEntity>(AllAttributeForFetch::class.java, criteria).size, "Expected 6 results")
    }
}