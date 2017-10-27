package database.list

import com.onyx.extension.delete
import com.onyx.extension.eq
import com.onyx.extension.from
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.WebPersistenceManagerFactory
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.DatabaseBaseTest
import entities.AllAttributeForFetch
import entities.AllAttributeV2Entity
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class EqualsTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

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
        entity.mutableFloat = 34.3f
        entity.floatValue = 55.3f
        entity.mutableByte = 43.toByte()
        entity.byteValue = 99.toByte()
        entity.mutableShort = 828
        entity.shortValue = 882
        entity.mutableChar = 'A'
        entity.charValue = 'C'
        entity.entity = AllAttributeV2Entity()
        entity.entity!!.id = "ASDF"
        entity.operator = QueryCriteriaOperator.CONTAINS
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
        entity.mutableFloat = 34.2f
        entity.floatValue = 55.2f
        entity.mutableByte = 42.toByte()
        entity.byteValue = 98.toByte()
        entity.mutableShort = 827
        entity.shortValue = 881
        entity.mutableChar = 'P'
        entity.charValue = 'F'
        entity.entity = AllAttributeV2Entity()
        entity.entity!!.id = "ASDFL"
        entity.operator = QueryCriteriaOperator.EQUAL
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
        entity.mutableFloat = 34.1f
        entity.floatValue = 55.1f
        entity.mutableByte = 41.toByte()
        entity.byteValue = 91.toByte()
        entity.mutableShort = 821
        entity.shortValue = 881
        entity.mutableChar = '1'
        entity.charValue = '2'
        entity.entity = AllAttributeV2Entity()
        entity.entity!!.id = "ASDF1"
        entity.operator = QueryCriteriaOperator.GREATER_THAN_EQUAL
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
        entity.mutableFloat = 31.3f
        entity.floatValue = 51.3f
        entity.mutableByte = 13.toByte()
        entity.byteValue = 19.toByte()
        entity.mutableShort = 818
        entity.shortValue = 812
        entity.mutableChar = '9'
        entity.charValue = 'O'
        entity.entity = AllAttributeV2Entity()
        entity.entity!!.id = "ASDAAF"
        entity.operator = QueryCriteriaOperator.CONTAINS
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
        entity.mutableFloat = 34.3f
        entity.floatValue = 55.3f
        entity.mutableByte = 43.toByte()
        entity.byteValue = 99.toByte()
        entity.mutableShort = 828
        entity.shortValue = 882
        entity.mutableChar = 'A'
        entity.charValue = 'C'
        entity.entity = AllAttributeV2Entity()
        entity.entity!!.id = "ASDF"
        entity.operator = QueryCriteriaOperator.CONTAINS
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE4"
        manager.saveEntity<IManagedEntity>(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE5"
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test
    fun testStringEquals() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "stringValue" eq "Some test strin1")
        assertEquals(2, results.size, "Expected 2 entities with 'stringValue' equal to 'Some test strin1'")
        assertEquals(results[0].stringValue, "Some test strin1", "stringValue was incorrect")
        assertEquals(results[1].stringValue, "Some test strin1", "stringValue was incorrect")
    }

    @Test
    fun testEqualsStringId() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "id" eq "FIRST ONE3")
        assertEquals(1, results.size, "Expected 1 entity with id equal to FIRST ONE3")
        assertEquals(results[0].id, "FIRST ONE3", "ID Value was incorrect")
    }

    @Test
    fun testNumberEquals() {
        var results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "longValue" eq 322L)
        assertEquals(3, results.size, "Expected 3 entities with longValue equal to 322")
        assertEquals(322L, results[0].longValue, "longValue does not match")

        results = manager.list(AllAttributeForFetch::class.java, "longPrimitive" eq 1301L)
        assertEquals(1, results.size, "Expected 1 entity with longPrimitive equal to 1301")
        assertEquals(1301L, results[0].longPrimitive, "longPrimitive value does not match")
    }

    @Test
    fun testDateEquals() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "dateValue" eq Date(1000L))
        assertEquals(1, results.size, "There should be one entity with date value eq Date(1000)")
        assertEquals(Date(1000L), results[0].dateValue, "Date value does not match")
    }

    @Test
    fun testIntegerEquals() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "intValue" eq 2)
        assertEquals(2, results.size, "There should be 2 entities with intValue of 2")
    }

    @Test
    fun testDoubleEquals() {
        var results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "doubleValue" eq 1.126)
        assertEquals(1, results.size, "There should be 1 entity with doubleValue equal to 1.126")
        assertEquals(1.126, results[0].doubleValue, "doubleValue does not match")

        results = manager.list(AllAttributeForFetch::class.java, "doublePrimitive" eq 3.35)
        assertEquals(1, results.size, "There should be 1 entity with doubleValue equal to 3.35")
        assertEquals(3.35, results[0].doublePrimitive, "doublePrimitive does not match")
    }

    @Test
    fun testBooleanEquals() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "booleanValue" eq false)
        assertEquals(2, results.size, "There should be 2 entities with booleanValue = false")
        assertEquals(false, results[0].booleanValue, "booleanValue does not match")

        val results2 = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "booleanPrimitive" eq true)
        assertEquals(2, results2.size, "There should be 2 entities with booleanPrimitive = true")
        assertEquals(true, results2[0].booleanPrimitive, "booleanPrimitive does not match")
    }

    @Test
    fun testFloatEquals() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "floatValue" eq 55.3f)
        assertEquals(2, results.size, "There should be 2 entities with floatValue = 55.3")
        assertEquals(55.3f, results[0].floatValue, "floatValue does not match")

        val results2 = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "mutableFloat" eq 34.3f)
        assertEquals(2, results2.size, "There should be 2 entities with mutableFloat = 34.3f")
        assertEquals(34.3f, results2[0].mutableFloat, "mutableFloat does not match")
    }

    @Test
    fun testByteEquals() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "mutableByte" eq 43.toByte())
        assertEquals(2, results.size, "There should be 2 entities with mutableByte = 43")
        assertEquals(43.toByte(), results[0].mutableByte, "mutableByte does not match")

        val results2 = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "byteValue" eq 99.toByte())
        assertEquals(2, results2.size, "There should be 2 entities with byteValue = 99")
        assertEquals(99.toByte(), results2[0].byteValue, "byteValue does not match")
    }

    @Test
    fun testShortEquals() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "mutableShort" eq 828)
        assertEquals(2, results.size, "There should be 2 entities with mutableShort = 828")
        assertEquals(828.toShort(), results[0].mutableShort, "mutableShort does not match")

        val results2 = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "shortValue" eq 882)
        assertEquals(2, results2.size, "There should be 2 entities with shortValue = 882")
        assertEquals(882, results2[0].shortValue, "shortValue does not match")
    }

    @Test
    fun testCharEquals() {
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "mutableChar" eq 'A')
        assertEquals(2, results.size, "There should be 2 entities with mutableChar = A")
        assertEquals('A', results[0].mutableChar, "mutableChar does not match")

        val results2 = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "charValue" eq 'C')
        assertEquals(2, results2.size, "There should be 2 entities with charValue = A")
        assertEquals('C', results2[0].charValue, "charValue does not match")
    }

    @Test
    fun testEntityEquals() {
        Assume.assumeFalse("Ignore test for WebPersistenceManagerFactory", factoryClass == WebPersistenceManagerFactory::class)
        val entity = AllAttributeV2Entity()
        entity.id = "ASDF"
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "entity" eq  entity)
        assertEquals(2, results.size, "There should be 2 entities with entity = $entity")
        assertEquals(entity, results[0].entity, "entity does not match")
    }

    @Test
    fun testEnumEquals() {
        Assume.assumeFalse("Ignore test for WebPersistenceManagerFactory", factoryClass == WebPersistenceManagerFactory::class)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, "operator" eq QueryCriteriaOperator.CONTAINS)
        assertEquals(2, results.size, "There should be 2 entities with operator = QueryCriteriaOperator.CONTAINS")
        assertEquals(QueryCriteriaOperator.CONTAINS, results[0].operator, "operator does not match")
    }
}
