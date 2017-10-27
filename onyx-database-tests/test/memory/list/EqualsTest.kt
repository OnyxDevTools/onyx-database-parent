package memory.list

import category.InMemoryDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.AllAttributeV2Entity
import entities.AllAttributeForFetch
import memory.base.BaseTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.util.Date

/**
 * Created by timothy.osborn on 11/6/14.
 */
@Category(InMemoryDatabaseTests::class)
class EqualsTest : BaseTest() {

    @Before
    @Throws(InitializationException::class)
    fun seedData() {
        initialize()


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

        save(entity)
        find(entity)

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
        save(entity)
        find(entity)

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
        save(entity)
        find(entity)

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
        save(entity)
        find(entity)

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

        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE4"
        save(entity)
        find(entity)

        entity = AllAttributeForFetch()
        entity.id = "FIRST ONE5"
        save(entity)
        find(entity)
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Test
    @Throws(OnyxException::class, InstantiationException::class, IllegalAccessException::class)
    fun testStringEquals() {
        val criteria = QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")

        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(2, results.size.toLong())
        Assert.assertEquals(results[0].stringValue, "Some test strin1")
        Assert.assertEquals(results[1].stringValue, "Some test strin1")
    }


    @Test
    @Throws(Exception::class)
    fun testEqualsStringId() {
        val criteria = QueryCriteria("id", QueryCriteriaOperator.EQUAL, "FIRST ONE3")
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(1, results.size.toLong())
        Assert.assertEquals(results[0].id, "FIRST ONE3")
    }

    @Test
    @Throws(Exception::class)
    fun testNumberEquals() {
        val criteria = QueryCriteria("longValue", QueryCriteriaOperator.EQUAL, 322L)

        var results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(3, results.size.toLong())
        Assert.assertEquals(results[0].longValue, java.lang.Long.valueOf(322L))

        val criteria2 = QueryCriteria("longPrimitive", QueryCriteriaOperator.EQUAL, 1301L)
        results = manager.list(AllAttributeForFetch::class.java, criteria2)
        Assert.assertEquals(1, results.size.toLong())
        Assert.assertEquals(results[0].longPrimitive, 1301L)
    }

    @Test
    @Throws(Exception::class)
    fun testDateEquals() {

        val criteria = QueryCriteria("dateValue", QueryCriteriaOperator.EQUAL, Date(1000))
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(1, results.size.toLong())
        Assert.assertEquals(results[0].dateValue, Date(1000))
    }

    @Test
    @Throws(Exception::class)
    fun testIntegerEquals() {
        val criteria = QueryCriteria("intValue", QueryCriteriaOperator.EQUAL, 2)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(2, results.size.toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testDoubleEquals() {
        val criteria = QueryCriteria("doubleValue", QueryCriteriaOperator.EQUAL, 1.126)
        var results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(1, results.size.toLong())
        Assert.assertEquals(results[0].doubleValue, java.lang.Double.valueOf(1.126))

        val criteria2 = QueryCriteria("doublePrimitive", QueryCriteriaOperator.EQUAL, 3.35)
        results = manager.list(AllAttributeForFetch::class.java, criteria2)
        Assert.assertEquals(1, results.size.toLong())
        assert(java.lang.Double.valueOf(results[0].doublePrimitive) == java.lang.Double.valueOf(3.35))
    }

    @Test
    @Throws(Exception::class)
    fun testBooleanEquals() {
        val criteria = QueryCriteria("booleanValue", QueryCriteriaOperator.EQUAL, false)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(2, results.size.toLong())
        Assert.assertEquals(results[0].booleanValue, java.lang.Boolean.valueOf(false))

        val criteria2 = QueryCriteria("booleanPrimitive", QueryCriteriaOperator.EQUAL, true)
        val results2 = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria2)
        Assert.assertEquals(2, results2.size.toLong())
        Assert.assertEquals(results2[0].booleanPrimitive, true)
    }


    @Test
    @Throws(OnyxException::class)
    fun testFloatEquals() {
        val criteria = QueryCriteria("floatValue", QueryCriteriaOperator.EQUAL, 55.3f)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(2, results.size.toLong())
        Assert.assertTrue(results[0].floatValue == 55.3f)

        val criteria2 = QueryCriteria("mutableFloat", QueryCriteriaOperator.EQUAL, 34.3f)
        val results2 = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria2)
        Assert.assertEquals(2, results2.size.toLong())
        Assert.assertTrue(results2[0].mutableFloat == 34.3f)
    }


    @Test
    @Throws(OnyxException::class)
    fun testByteEquals() {
        val criteria = QueryCriteria("mutableByte", QueryCriteriaOperator.EQUAL, 43.toByte())
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(2, results.size.toLong())
        Assert.assertTrue(results[0].mutableByte == 43.toByte())

        val criteria2 = QueryCriteria("byteValue", QueryCriteriaOperator.EQUAL, 99.toByte())
        val results2 = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria2)
        Assert.assertEquals(2, results2.size.toLong())
        Assert.assertTrue(results2[0].byteValue == 99.toByte())
    }

    @Test
    @Throws(OnyxException::class)
    fun testShortEquals() {
        val criteria = QueryCriteria("mutableShort", QueryCriteriaOperator.EQUAL, 828.toShort())
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(2, results.size.toLong())
        Assert.assertTrue(results[0].mutableShort == 828.toShort())

        val criteria2 = QueryCriteria("shortValue", QueryCriteriaOperator.EQUAL, 882.toShort())
        val results2 = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria2)
        Assert.assertEquals(2, results2.size.toLong())
        Assert.assertTrue(results2[0].shortValue.toInt() == 882)
    }

    @Test
    @Throws(OnyxException::class)
    fun testCharEquals() {
        val criteria = QueryCriteria("mutableChar", QueryCriteriaOperator.EQUAL, 'A')
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(2, results.size.toLong())
        Assert.assertTrue(results[0].mutableChar == 'A')

        val criteria2 = QueryCriteria("charValue", QueryCriteriaOperator.EQUAL, 'C')
        val results2 = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria2)
        Assert.assertEquals(2, results2.size.toLong())
        Assert.assertTrue(results2[0].charValue == 'C')
    }


    @Test
    @Throws(OnyxException::class)
    fun testEntityEquals() {
        val entity = AllAttributeV2Entity()
        entity.id = "ASDF"
        val criteria = QueryCriteria("entity", QueryCriteriaOperator.EQUAL, entity)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(2, results.size.toLong())
        Assert.assertTrue(results[0].entity == entity)

    }

    @Test
    @Throws(OnyxException::class)
    fun testEnumEquals() {
        val criteria = QueryCriteria("operator", QueryCriteriaOperator.EQUAL, QueryCriteriaOperator.CONTAINS)
        val results = manager.list<AllAttributeForFetch>(AllAttributeForFetch::class.java, criteria)
        Assert.assertEquals(2, results.size.toLong())
        Assert.assertTrue(results[0].operator === QueryCriteriaOperator.CONTAINS)
    }
}
