package database

import category.EmbeddedDatabaseTests
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.QueryCriteriaOperator
import database.database.base.DatabaseBaseTest
import entities.AllAttributeV2Entity
import entities.EnumEntity
import entities.InheritedAttributeEntity
import entities.SimpleEntity
import org.junit.*
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import pojo.SimpleEnum
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class AttributeTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    companion object {
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            deleteAllDatabases()
            startServers()
        }
    }

    @Before
    fun before() {
        initialize()
    }

    @After
    fun after() {
        shutdown()
    }

    /**
     * Tests all possible populated values in order to test a round trip serialization
     */
    @Test
    fun testPopulatedEntity() {
        val entity = AllAttributeV2Entity()

        entity.id = "A"
        entity.longValue = 4L
        entity.longPrimitive = 3L
        entity.stringValue = "STring key"
        entity.dateValue = Date(1483736263743L)
        entity.doublePrimitive = 342.23
        entity.doubleValue = 232.2
        entity.booleanPrimitive = true
        entity.booleanValue = false
        entity.mutableFloat = 23.234f
        entity.floatValue = 666.3453f
        entity.mutableByte = 5.toByte()
        entity.byteValue = 7.toByte()
        entity.mutableShort = 65
        entity.shortValue = 44
        entity.mutableChar = 'C'
        entity.charValue = 'D'
        entity.entity = AllAttributeV2Entity()
        entity.entity?.shortValue = 49
        entity.aMutableBytes = arrayOf(2.toByte(), 8.toByte(), 7.toByte())
        entity.bytes = byteArrayOf(4.toByte(), 4.toByte(), 2.toByte())
        entity.shorts = shortArrayOf(4, 4, 2)
        entity.mutableShorts = arrayOf(4, 4, 2)
        entity.strings = arrayOf("A", "V")
        entity.operator = QueryCriteriaOperator.CONTAINS
        entity.entitySet = HashSet()
        entity.entitySet?.add(entity.entity!!)
        entity.entityList = ArrayList()
        entity.entityList?.add(entity.entity!!)

        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = AllAttributeV2Entity()
        entity2.id = "A"
        entity2 = manager.find(entity2)

        val message = "Entity failed hydrate attribute: "
        assertEquals("A", entity2.id, message + "id")
        assertEquals(4L, entity2.longValue, message + "longValue")
        assertEquals(3L, entity2.longPrimitive, message + "longPrimitive")
        assertEquals("STring key", entity2.stringValue, message + "stringValue")
        assertEquals(entity.dateValue, entity2.dateValue, message + "dateValue")
        assertEquals(342.23, entity2.doublePrimitive, message + "doublePrimitive")
        assertEquals(232.2, entity2.doubleValue, message + "doubleValue")
        assertEquals(true, entity2.booleanPrimitive, message + "booleanPrimitive")
        assertEquals(false, entity2.booleanValue, message + "booleanValue")
        assertEquals(23.234f, entity2.mutableFloat, message + "mutableFloat")
        assertEquals(666.3453f, entity2.floatValue, message + "floatValue")
        assertEquals(5.toByte(),  entity2.mutableByte, message + "mutableByte")
        assertEquals(7.toByte(), entity2.byteValue, message + "byteValue")
        assertEquals(65.toShort(),  entity2.mutableShort, message + "mutableShort")
        assertEquals(44,  entity2.shortValue.toInt(), message + "shortValue")
        assertEquals('C',  entity2.mutableChar, message + "mutableChar")
        assertEquals('D',  entity2.charValue, message + "charValue")
        assertEquals(QueryCriteriaOperator.CONTAINS, entity2.operator, message + "operator")
        assertEquals(49, entity2.entity?.shortValue?.toInt(), message + "shortValue")
        assertEquals(3, entity2.shorts?.size, message + "shorts")
        assertEquals(2, entity2.shorts!![2].toInt(), message + "shorts")
        assertEquals(3, entity2.mutableShorts!!.size, message + "mutableShorts")
        assertEquals(2, entity2.mutableShorts!![2], message + "mutableShorts")
        assertEquals(2, entity2.strings!!.size, message + "strings")
        assertEquals("V", entity2.strings!![1], message + "strings")
        assertEquals(1, entity2.entityList?.size, message + "entityList")
        assertEquals(1, entity2.entitySet?.size, message + "entitySet")
        org.junit.Assert.assertArrayEquals(byteArrayOf(4.toByte(), 4.toByte(), 2.toByte()), entity2.bytes)
        org.junit.Assert.assertArrayEquals(arrayOf(2.toByte(), 8.toByte(), 7.toByte()), entity2.aMutableBytes)
    }

    /**
     * Test null values for properties that are mutable
     */
    @Test
    fun testNullPopulatedEntity() {
        val entity = AllAttributeV2Entity()

        entity.id = "B"
        manager.saveEntity<IManagedEntity>(entity)

        val entity2 = AllAttributeV2Entity()
        entity2.id = "B"
        manager.find<IManagedEntity>(entity2)

        val message = "Entity failed hydrate attribute: "

        assertEquals("B", entity2.id, message + "id")
        assertNull(entity2.longValue, message + "longValue")
        assertEquals(0, entity2.longPrimitive, message + "longPrimitive")
        assertNull(entity2.stringValue, message + "stringValue")
        assertNull(entity2.dateValue, message + "dateValue")
        assertEquals (0.0, entity2.doublePrimitive, message + "doublePrimitive")
        assertNull(entity2.doubleValue, message + "doubleValue")
        assertEquals(false, entity2.booleanPrimitive, message + "booleanPrimitive")
        assertNull(entity2.booleanValue, message + "booleanValue")
        assertNull(entity2.mutableFloat, message + "mutableFloat")
        assertNull(entity2.mutableByte, message + "mutableByte")
        assertNull(entity2.mutableShort, message + "mutableShort")
        assertNull(entity2.mutableChar, message + "mutableChar")
        assertNull(entity2.entity, message + "entity")
        assertNull(entity2.aMutableBytes, message + "aMutableBytes")
        assertEquals(0.0f, entity2.floatValue, message + "floatValue")
        assertEquals(0, entity2.byteValue, message + "byteValue")
        assertEquals(0, entity2.shortValue.toInt(), message + "shortValue")
        assertEquals(' ', entity2.charValue, message + "charValue")

    }

    /**
     * Tests that inherited properties are persisted and hydrated properly
     */
    @Test
    fun testInheritedPopulatedEntity() {

        val entity = InheritedAttributeEntity()

        entity.id = "C"
        entity.longValue = 4L
        entity.longPrimitive = 3L
        entity.stringValue = "STring key"
        entity.dateValue = Date(343535)
        entity.doublePrimitive = 342.23
        entity.doubleValue = 232.2
        entity.booleanPrimitive = true
        entity.booleanValue = false

        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = InheritedAttributeEntity()
        entity2.id = "C"

        Assert.assertTrue(manager.exists(entity2))
        entity2 = manager.find(entity2)

        val message = "Entity failed hydrate attribute: "

        assertEquals("C", entity2.id, message + "id")
        assertEquals(4L, entity2.longValue, message + "longValue")
        assertEquals(3L, entity2.longPrimitive, message + "longPrimitive")
        assertEquals("STring key", entity2.stringValue, message + "stringValue")
        assertEquals(entity.dateValue, entity2.dateValue, message + "dateValue")
        assertEquals (342.23, entity2.doublePrimitive, message + "doublePrimitive")
        assertEquals(232.2, entity2.doubleValue, message + "doubleValue")
        assertEquals(true, entity2.booleanPrimitive, message + "booleanPrimitive")
        assertEquals(false, entity2.booleanValue, message + "booleanValue")
    }

    @Test
    fun simpleMultipleTest() {
        val simpleEntity2 = SimpleEntity()
        simpleEntity2.simpleId = "2"
        simpleEntity2.name = "Name2"

        val simpleEntity3 = SimpleEntity()
        simpleEntity3.simpleId = "3"
        simpleEntity3.name = "Name3"

        manager.saveEntities(arrayListOf(simpleEntity2, simpleEntity3))
    }

    @Test
    fun testFindById() {
        //Save entity
        val entity = SimpleEntity()
        entity.simpleId = "1"
        entity.name = "Chris"
        manager.saveEntity<IManagedEntity>(entity)

        val savedEntity = manager.findById<IManagedEntity>(entity.javaClass, "1") as SimpleEntity?

        assertEquals(entity.simpleId,  savedEntity!!.simpleId, "Failed to find simpleId")
        assertEquals(entity.name,  savedEntity.name, "Failed to find name")
    }

    @Test
    fun testEnum() {
        val enumEntity = EnumEntity()
        enumEntity.simpleId = "99HIYA"
        enumEntity.simpleEnum = SimpleEnum.SECOND
        manager.saveEntity<IManagedEntity>(enumEntity)
        manager.find<IManagedEntity>(enumEntity)
        assertEquals(enumEntity.simpleEnum, SimpleEnum.SECOND, "Failed to find Enum value")
    }
}