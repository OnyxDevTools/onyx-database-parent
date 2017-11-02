package database.list

import com.onyx.extension.gt
import com.onyx.extension.gte
import com.onyx.extension.lt
import com.onyx.extension.lte
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.AllAttributeForFetchSequenceGen
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class IdentifierListTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun seedData() {

        var entity = AllAttributeForFetchSequenceGen()
        entity.stringValue = "Some test string"
        entity.dateValue = Date(1000)
        entity.doublePrimitive = 3.3
        entity.doubleValue = 1.1
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1000L
        entity.longValue = 323L
        entity.intValue = 3
        entity.intPrimitive = 3
        entity.id = 1L
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.stringValue = "Some test string1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        entity.intValue = 2
        entity.intPrimitive = 4
        entity.id = 2L
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.stringValue = "Some test string1"
        entity.dateValue = Date(1001)
        entity.doublePrimitive = 3.31
        entity.doubleValue = 1.11
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1002L
        entity.longValue = 322L
        entity.intValue = 2
        entity.intPrimitive = 4
        entity.id = 3L
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.stringValue = "Some test string2"
        entity.dateValue = Date(1002)
        entity.doublePrimitive = 3.32
        entity.doubleValue = 1.12
        entity.booleanValue = true
        entity.booleanPrimitive = false
        entity.longPrimitive = 1001L
        entity.longValue = 321L
        entity.intValue = 5
        entity.intPrimitive = 6
        entity.id = 4L
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.stringValue = "Some test string3"
        entity.dateValue = Date(1022)
        entity.doublePrimitive = 3.35
        entity.doubleValue = 1.126
        entity.booleanValue = false
        entity.booleanPrimitive = true
        entity.longPrimitive = 1301L
        entity.longValue = 322L
        entity.intValue = 6
        entity.intPrimitive = 3
        entity.id = 5L
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.id = 6L
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetchSequenceGen()
        entity.id = 7L
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test
    fun testIdentifierRange() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, (("id" gt 2) and ("id" lt 4)))
        assertEquals(1, results.size, "Expected 1 Result")
    }

    @Test
    fun testIdentifierRangeLTEqual() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("id" gt 2) and ("id" lte 4))
        assertEquals(2, results.size, "Expected 2 Results")
    }

    @Test
    fun testIdentifierRangeEqual() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("id" gte 2) and ("id" lte 4))
        assertEquals(3, results.size, "Expected 3 Results")
    }

    @Test
    fun testIdentifierGreaterThan() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("id" gt 5))
        assertEquals(2, results.size, "Expected 2 Results")
    }

    @Test
    fun testIdentifierLessThanNoResults() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("id" lt 1))
        assertEquals(0, results.size, "Expected No Results")
    }

    @Test
    fun testIdentifierGreaterThanNoResults() {
        val results = manager.list<AllAttributeForFetchSequenceGen>(AllAttributeForFetchSequenceGen::class.java, ("id" gt 7))
        assertEquals(0, results.size, "Expected No Results")
    }
}