package embedded

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.QueryCriteriaOperator
import embedded.base.BaseTest
import entities.AllAttributeV2Entity
import entities.EnumEntity
import entities.InheritedAttributeEntity
import entities.SimpleEntity
import org.junit.*
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters
import pojo.SimpleEnum

import java.io.IOException
import java.util.*

import junit.framework.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(EmbeddedDatabaseTests::class)
class AttributeTest : BaseTest() {

    @Before
    @Throws(InitializationException::class, InterruptedException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    /**
     * Tests all possible populated values in order to test a round trip serialization
     *
     * @throws OnyxException
     * @throws InitializationException
     */
    @Test
    @Throws(OnyxException::class)
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
        entity.entity!!.shortValue = 49
        entity.amutableBytes = arrayOf(2.toByte(), 8.toByte(), 7.toByte())
        entity.bytes = byteArrayOf(4.toByte(), 4.toByte(), 2.toByte())
        entity.shorts = shortArrayOf(4, 4, 2)
        entity.mutableShorts = arrayOf(4, 4, 2)
        entity.strings = arrayOf("A", "V")
        entity.operator = QueryCriteriaOperator.CONTAINS
        entity.entitySet = HashSet()
        entity.entitySet!!.add(entity.entity!!)
        entity.entityList = ArrayList()
        entity.entityList!!.add(entity.entity!!)

        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = AllAttributeV2Entity()
        entity2.id = "A"
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        Assert.assertEquals("A", entity2.id)
        Assert.assertEquals(java.lang.Long.valueOf(4L), entity2.longValue)
        Assert.assertEquals(3L, entity2.longPrimitive)
        Assert.assertEquals("STring key", entity2.stringValue)
        Assert.assertEquals(entity.dateValue, entity2.dateValue)
        assert(342.23 == entity2.doublePrimitive)
        Assert.assertEquals(java.lang.Double.valueOf(232.2), entity2.doubleValue)
        Assert.assertEquals(true, entity2.booleanPrimitive)
        Assert.assertEquals(java.lang.Boolean.valueOf(false), entity2.booleanValue)
        Assert.assertTrue(23.234f == entity2.mutableFloat)
        Assert.assertTrue(666.3453f == entity2.floatValue)
        Assert.assertTrue(5.toByte() == entity2.mutableByte)
        Assert.assertTrue(7.toByte() == entity2.byteValue)
        Assert.assertTrue(65.toShort() == entity2.mutableShort)
        Assert.assertTrue(44 == entity2.shortValue.toInt())
        Assert.assertTrue('C' == entity2.mutableChar)
        Assert.assertTrue('D' == entity2.charValue)
        Assert.assertTrue(entity2.operator === QueryCriteriaOperator.CONTAINS)
        Assert.assertTrue(entity2.entity != null && entity2.entity!!.shortValue.toInt() == 49)
        Assert.assertArrayEquals(entity2.bytes, byteArrayOf(4.toByte(), 4.toByte(), 2.toByte()))
        Assert.assertTrue(entity2.shorts!!.size == 3 && entity2.shorts!![2].toInt() == 2)
        Assert.assertTrue(entity2.mutableShorts!!.size == 3 && entity2.mutableShorts!![2] === 2.toShort())
        Assert.assertTrue(entity2.strings!!.size == 2 && entity2.strings!![1] == "V")
        Assert.assertArrayEquals(entity2.amutableBytes, arrayOf(2.toByte(), 8.toByte(), 7.toByte()))
        Assert.assertTrue(entity2.entityList is List<*> && entity2.entityList!!.size == 1)
        Assert.assertTrue(entity2.entitySet is Set<*> && entity2.entitySet!!.size == 1)
    }

    /**
     * Test null values for properties that are mutable
     */
    @Test
    fun testNullPopulatedEntity() {
        val entity = AllAttributeV2Entity()

        entity.id = "B"

        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        val entity2 = AllAttributeV2Entity()
        entity2.id = "B"
        try {
            manager.find<IManagedEntity>(entity2)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        Assert.assertEquals("B", entity2.id)
        Assert.assertNull(entity2.longValue)
        Assert.assertEquals(0, entity2.longPrimitive)
        Assert.assertNull(entity2.stringValue)
        Assert.assertNull(entity2.dateValue)
        assert (0.0 == entity2.doublePrimitive)
        Assert.assertNull(entity2.doubleValue)
        Assert.assertEquals(false, entity2.booleanPrimitive)
        Assert.assertNull(entity2.booleanValue)

        Assert.assertNull(entity2.mutableFloat)
        Assert.assertNull(entity2.mutableByte)
        Assert.assertNull(entity2.mutableShort)
        Assert.assertNull(entity2.mutableChar)
        Assert.assertNull(entity2.entity)
        Assert.assertNull(entity2.amutableBytes)

        Assert.assertTrue(entity2.floatValue == 0.0f)
        Assert.assertTrue(entity2.byteValue == 0.toByte())
        Assert.assertTrue(entity2.shortValue.toInt() == 0)
        Assert.assertTrue(entity2.charValue == ' ')

    }

    /**
     * Tests that inherited properties are persisted and hydrated properly
     *
     * @throws OnyxException
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
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

        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }


        var entity2 = InheritedAttributeEntity()
        entity2.id = "C"

        assertTrue(manager.exists(entity2))
        try {
            entity2 = manager.find<InheritedAttributeEntity>(entity2)
        } catch (e: OnyxException) {
            fail("Error finding entity")
        }

        Assert.assertEquals("C", entity2.id)
        Assert.assertEquals(java.lang.Long.valueOf(4L), entity2.longValue)
        Assert.assertEquals(3L, entity2.longPrimitive)
        Assert.assertEquals("STring key", entity2.stringValue)
        Assert.assertEquals(entity.dateValue, entity2.dateValue)
        assert (342.23 == entity2.doublePrimitive)
        Assert.assertEquals(java.lang.Double.valueOf(232.2), entity2.doubleValue)
        Assert.assertEquals(true, entity2.booleanPrimitive)
        Assert.assertEquals(java.lang.Boolean.valueOf(false), entity2.booleanValue)

    }


    @Test
    @Throws(OnyxException::class)
    fun simpleMultipleTest() {
        val simpleEntity2 = SimpleEntity()
        simpleEntity2.simpleId = "2"
        simpleEntity2.name = "Name2"

        val simpleEntity3 = SimpleEntity()
        simpleEntity3.simpleId = "3"
        simpleEntity3.name = "Name3"

        manager.saveEntities(Arrays.asList(simpleEntity2, simpleEntity3))

        //@todo: retreive using manager.list()

    }

    @Test
    @Throws(OnyxException::class)
    fun testFindById() {
        //Save entity
        val entity = SimpleEntity()
        entity.simpleId = "1"
        entity.name = "Chris"
        manager.saveEntity<IManagedEntity>(entity)
        //Retreive entity using findById method
        val savedEntity = manager.findById<IManagedEntity>(entity.javaClass, "1") as SimpleEntity?

        //Assert
        Assert.assertTrue(entity.simpleId == savedEntity!!.simpleId)
        Assert.assertTrue(entity.name == savedEntity.name)
    }

    @Test
    @Throws(OnyxException::class)
    fun testEnum() {
        val enumEntity = EnumEntity()
        enumEntity.simpleId = "99HIYA"
        enumEntity.simpleEnum = SimpleEnum.SECOND
        save(enumEntity)

        find(enumEntity)
        assertEquals(enumEntity.simpleEnum, SimpleEnum.SECOND)
    }

    companion object {

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            BaseTest.deleteDatabase()
        }
    }


}
