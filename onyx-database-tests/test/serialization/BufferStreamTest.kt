package serialization

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.exception.BufferingException
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.AllAttributeEntity
import entities.SimpleEntity
import entities.index.StringIdentifierEntityIndex
import entities.relationship.OneToOneChild
import entities.relationship.OneToOneParent
import org.junit.Test
import pojo.AllTypes
import pojo.BufferStreamableObject
import pojo.Simple

import java.nio.ByteBuffer
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Created by Tim Osborn on 7/28/16.
 */
class BufferStreamTest {

    @Test
    fun testSerializePrimitive() {
        // int
        var buffer = serialize(1)
        val value = deserialize(buffer) as Int
        assertEquals(1, value)
        BufferPool.recycle(buffer)

        // long
        buffer = serialize(2L)
        assertEquals(2L, deserialize(buffer))
        BufferPool.recycle(buffer)

        // boolean
        buffer = serialize(true)
        assertEquals(true, deserialize(buffer))
        BufferPool.recycle(buffer)

        // short
        buffer = serialize(34.toShort())
        assertEquals(34.toShort(), deserialize(buffer))
        BufferPool.recycle(buffer)

        // byte
        buffer = serialize(3.toByte())
        assertEquals(3.toByte(), deserialize(buffer))
        BufferPool.recycle(buffer)

        // float
        buffer = serialize(3.3f)
        assertEquals(3.3f, deserialize(buffer))
        BufferPool.recycle(buffer)

        // double
        buffer = serialize(3.33)
        assertEquals(3.33, deserialize(buffer))
        BufferPool.recycle(buffer)

        // char
        buffer = serialize('C')
        assertEquals('C', deserialize(buffer))
        BufferPool.recycle(buffer)

    }

    @Test
    fun testPrimitiveArrays() {
        var ints = intArrayOf(1, 2, 3, 4)
        var buffer = serialize(ints)
        ints = deserialize(buffer) as IntArray
        assertTrue(ints[0] == 1 && ints[1] == 2 && ints[2] == 3 && ints[3] == 4)
        BufferPool.recycle(buffer)

        var longs = longArrayOf(1L, 2L, 3L, 4L)
        buffer = serialize(longs)
        longs = deserialize(buffer) as LongArray
        assertTrue(longs[0] == 1L && longs[1] == 2L && longs[2] == 3L && longs[3] == 4L)
        BufferPool.recycle(buffer)

        var bytes = byteArrayOf(1, 2, 3, 4)
        buffer = serialize(bytes)
        bytes = deserialize(buffer) as ByteArray
        assertTrue(bytes[0].toInt() == 1 && bytes[1].toInt() == 2 && bytes[2].toInt() == 3 && bytes[3].toInt() == 4)
        BufferPool.recycle(buffer)

        var booleans = booleanArrayOf(true, false, true, false)
        buffer = serialize(booleans)
        booleans = deserialize(buffer) as BooleanArray
        assertTrue(booleans[0] && !booleans[1] && booleans[2] && !booleans[3])
        BufferPool.recycle(buffer)

        var floats = floatArrayOf(1.1f, 2.2f, 3.3f, 4.4f)
        buffer = serialize(floats)
        floats = deserialize(buffer) as FloatArray
        assertTrue(floats[0] == 1.1f && floats[1] == 2.2f && floats[2] == 3.3f && floats[3] == 4.4f)
        BufferPool.recycle(buffer)

        var doubles = doubleArrayOf(1.1, 2.2, 3.3, 4.4)
        buffer = serialize(doubles)
        doubles = deserialize(buffer) as DoubleArray
        assertTrue(doubles[0] == 1.1 && doubles[1] == 2.2 && doubles[2] == 3.3 && doubles[3] == 4.4)
        BufferPool.recycle(buffer)

        var shorts = shortArrayOf(1, 2, 3, 4)
        buffer = serialize(shorts)
        shorts = deserialize(buffer) as ShortArray
        assertTrue(shorts[0].toInt() == 1 && shorts[1].toInt() == 2 && shorts[2].toInt() == 3 && shorts[3].toInt() == 4)
        BufferPool.recycle(buffer)

        var chars = charArrayOf('1', '2', '3', '4')
        buffer = serialize(chars)
        chars = deserialize(buffer) as CharArray
        assertTrue(chars[0] == '1' && chars[1] == '2' && chars[2] == '3' && chars[3] == '4')
        BufferPool.recycle(buffer)
    }

    @Test
    fun testString() {
        val value = "_ASDF@#$@#\$ASDF_ASDF_"
        val buffer = serialize(value)
        val otherValue = deserialize(buffer) as String?
        assertEquals(value, otherValue)
        BufferPool.recycle(buffer)
    }

    @Test
    fun testDate() {
        val value = Date(3737373)
        val buffer = serialize(value)
        val otherValue = deserialize(buffer) as Date?
        assertEquals(value, otherValue)
        BufferPool.recycle(buffer)
    }

    @Test
    fun testNamedObject() {

        val entity = AllAttributeEntity()
        entity.booleanPrimitive = false
        entity.booleanValue = true
        entity.longValue = 4L
        entity.longPrimitive = 3L
        entity.intValue = 23
        entity.intPrimitive = 22
        entity.stringValue = "234234234"
        entity.dateValue = Date(333333)
        entity.doublePrimitive = 23.33
        entity.doubleValue = 22.2

        val buffer = serialize(entity)
        val otherValue = deserialize(buffer) as AllAttributeEntity?
        BufferPool.recycle(buffer)

        assertEquals(false, otherValue!!.booleanPrimitive)
        assertEquals(true, otherValue.booleanValue)
        assertEquals(4L, otherValue.longValue)
        assertEquals(3L, otherValue.longPrimitive)
        assertEquals(23, otherValue.intValue)
        assertEquals(22, otherValue.intPrimitive)
        assertEquals("234234234", otherValue.stringValue)
        assertEquals(Date(333333), otherValue.dateValue)
        assertEquals(23.33, otherValue.doublePrimitive)
        assertEquals(22.2, otherValue.doubleValue)
    }

    @Test
    fun testNonNamedObject() {
        val entity = AllTypes()
        entity.booleanValueM = false
        entity.booleanValue = true
        entity.longValue = 4L
        entity.longValueM = 3L
        entity.intValue = 23
        entity.intValueM = 22
        entity.stringValue = "234234234"
        entity.dateValue = Date(333333)
        entity.shortValue = 26
        entity.shortValueM = 95
        entity.doubleValue = 32.32
        entity.doubleValueM = 22.54
        entity.floatValue = 32.321f
        entity.floatValueM = 22.542f
        entity.byteValue = 4.toByte()
        entity.byteValueM = 9.toByte()
        entity.nullValue = null
        entity.charValue = 'K'
        entity.charValueM = 'U'


        val buffer = serialize(entity)
        val entity2 = deserialize(buffer) as AllTypes?
        BufferPool.recycle(buffer)

        assertEquals(false, entity2?.booleanValueM)
        assertEquals(true, entity2!!.booleanValue)
        assertEquals(4L, entity2.longValue)
        assertEquals(3L, entity2.longValueM)
        assertEquals(23, entity2.intValue)
        assertEquals(22, entity2.intValueM)
        assertEquals("234234234", entity2.stringValue)
        assertEquals(Date(333333), entity2.dateValue)
        assertEquals(26, entity2.shortValue)
        assertEquals(95.toShort(), entity2.shortValueM)
        assertEquals(32.32, entity2.doubleValue)
        assertEquals(22.54, entity2.doubleValueM)
        assertEquals(32.321f, entity2.floatValue)
        assertEquals(22.542f, entity2.floatValueM)
        assertEquals(4, entity2.byteValue)
        assertEquals(9, entity2.byteValueM)
        assertNull(entity2.nullValue)
        assertEquals('K', entity2.charValue)
        assertEquals('U', entity2.charValueM)
    }

    @Test
    fun testMapPrimitives() {
        val map = HashMap<Int, Any?>()
        map.put(1, 3)
        map.put(4, 6L)
        map.put(6, 22.2)
        map.put(3, 23.3f)
        map.put(5, true)
        map.put(9, 23.toShort())
        map.put(99, 32.toByte())
        map.put(87, 'C')
        map.put(100, intArrayOf(3, 4, 6, 3))

        val buffer = serialize(map)
        val other = deserialize(buffer) as Map<*, *>?
        BufferPool.recycle(buffer)

        assertEquals(3, other!![1])
        assertEquals(6L, other[4])
        assertEquals(22.2, other[6])
        assertEquals(23.3f, other[3])
        assertEquals(true, other[5])
        assertEquals(23.toShort(), other[9])
        assertEquals(32.toByte(), other[99])
        assertEquals('C', other[87])
        assertEquals(3, (other[100] as IntArray)[0])
        assertEquals(4, (other[100] as IntArray)[1])
        assertEquals(6, (other[100] as IntArray)[2])
        assertEquals(3, (other[100] as IntArray)[3])

    }

    @Test
    fun testCollection() {
        val myCollection = ArrayList<AllTypes>()
        for (i in 0..9999) {
            val entity = AllTypes()
            entity.booleanValueM = false
            entity.booleanValue = true
            entity.longValue = 4L
            entity.longValueM = 3L
            entity.intValue = i
            entity.intValueM = 22
            entity.stringValue = "234234234"
            entity.dateValue = Date(333333)
            entity.shortValue = 26
            entity.shortValueM = 95
            entity.doubleValue = 32.32
            entity.doubleValueM = 22.54
            entity.floatValue = 32.321f
            entity.floatValueM = 22.542f
            entity.byteValue = 4.toByte()
            entity.byteValueM = 9.toByte()
            entity.nullValue = null
            entity.charValue = 'K'
            entity.charValueM = 'U'
            myCollection.add(entity)
        }

        val buffer = serialize(myCollection)
        val collection2 = deserialize(buffer) as Collection<*>?
        BufferPool.recycle(buffer)

        collection2!!.forEachIndexed { i, obj ->
            val entity = obj as AllTypes

            assertEquals(false, entity.booleanValueM)
            assertEquals(true, entity.booleanValue)
            assertEquals(4L, entity.longValue)
            assertEquals(3L, entity.longValueM)
            assertEquals(i, entity.intValue)
            assertEquals(22, entity.intValueM)
            assertEquals("234234234", entity.stringValue)
            assertEquals(Date(333333), entity.dateValue)
            assertEquals(26, entity.shortValue)
            assertEquals(95, entity.shortValueM)
            assertEquals(32.32, entity.doubleValue)
            assertEquals(22.54, entity.doubleValueM)
            assertEquals(32.321f, entity.floatValue)
            assertEquals(22.542f, entity.floatValueM)
            assertEquals(4, entity.byteValue)
            assertEquals(9, entity.byteValueM)
            assertNull(entity.nullValue)
            assertEquals('K', entity.charValue)
            assertEquals('U', entity.charValueM)
        }
    }

    @Test
    fun testMap() {
        val myCollection = ArrayList<AllTypes>()
        for (i in 0..9999) {
            val entity = AllTypes()
            entity.booleanValueM = false
            entity.booleanValue = true
            entity.longValue = 4L
            entity.longValueM = 3L
            entity.intValue = i
            entity.intValueM = 22
            entity.stringValue = "234234234"
            entity.dateValue = Date(333333)
            entity.shortValue = 26
            entity.shortValueM = 95
            entity.doubleValue = 32.32
            entity.doubleValueM = 22.54
            entity.floatValue = 32.321f
            entity.floatValueM = 22.542f
            entity.byteValue = 4.toByte()
            entity.byteValueM = 9.toByte()
            entity.nullValue = null
            entity.charValue = 'K'
            entity.charValueM = 'U'
            myCollection.add(entity)
        }

        val buffer = serialize(myCollection)
        val collection2 = deserialize(buffer) as Collection<*>?
        BufferPool.recycle(buffer)

        collection2!!.forEachIndexed { i, obj ->
            val entity = obj as AllTypes
            assertEquals(false, entity.booleanValueM)
            assertEquals(true, entity.booleanValue)
            assertEquals(4L, entity.longValue)
            assertEquals(3L, entity.longValueM)
            assertEquals(i, entity.intValue)
            assertEquals(22, entity.intValueM)
            assertEquals("234234234", entity.stringValue)
            assertEquals(Date(333333), entity.dateValue)
            assertEquals(26, entity.shortValue)
            assertEquals(95, entity.shortValueM)
            assertEquals(32.32, entity.doubleValue)
            assertEquals(22.54, entity.doubleValueM)
            assertEquals(32.321f, entity.floatValue)
            assertEquals(22.542f, entity.floatValueM)
            assertEquals(4, entity.byteValue)
            assertEquals(9, entity.byteValueM)
            assertNull(entity.nullValue)
            assertEquals('K', entity.charValue)
            assertEquals('U', entity.charValueM)
        }
    }

    @Test
    fun testRandomObjectArray() {
        val objects = ArrayList<Any?>()
        objects.add(1)
        objects.add(1L)

        var entity = AllTypes()
        entity.booleanValueM = false
        entity.booleanValue = true
        entity.longValue = 4L
        entity.longValueM = 3L
        entity.intValue = 55
        entity.intValueM = 22
        entity.stringValue = "234234234"
        entity.dateValue = Date(333333)
        entity.shortValue = 26
        entity.shortValueM = 95
        entity.doubleValue = 32.32
        entity.doubleValueM = 22.54
        entity.floatValue = 32.321f
        entity.floatValueM = 22.542f
        entity.byteValue = 4.toByte()
        entity.byteValueM = 9.toByte()
        entity.nullValue = null
        entity.charValue = 'K'
        entity.charValueM = 'U'

        objects.add(entity)
        objects.add(true)


        val buffer = serialize(objects)
        val collection2 = deserialize(buffer) as ArrayList<*>?
        BufferPool.recycle(buffer)


        assertEquals(1, collection2!![0])
        assertEquals(1L, collection2[1])
        assertEquals(true, collection2[3])

        entity = collection2[2] as AllTypes

        assertEquals(false, entity.booleanValueM)
        assertEquals(true, entity.booleanValue)
        assertEquals(4L, entity.longValue)
        assertEquals(3L, entity.longValueM)
        assertEquals(55, entity.intValue)
        assertEquals(22, entity.intValueM)
        assertEquals("234234234", entity.stringValue)
        assertEquals(Date(333333), entity.dateValue)
        assertEquals(26, entity.shortValue)
        assertEquals(95, entity.shortValueM)
        assertEquals(32.32, entity.doubleValue)
        assertEquals(22.54, entity.doubleValueM)
        assertEquals(32.321f, entity.floatValue)
        assertEquals(22.542f, entity.floatValueM)
        assertEquals(4, entity.byteValue)
        assertEquals(9, entity.byteValueM)
        assertNull(entity.nullValue)
        assertEquals('K', entity.charValue)
        assertEquals('U', entity.charValueM)
    }

    @Test
    fun testObjectWithEnum() {
        val query = Query(SimpleEntity::class.java, QueryCriteria("attribute", QueryCriteriaOperator.EQUAL, 33L))

        val buffer = serialize(query)
        val query1 = deserialize(buffer) as Query?
        BufferPool.recycle(buffer)

        assertEquals(query.entityType, query1!!.entityType)
        assertEquals(query.criteria!!.attribute, query1.criteria!!.attribute)
        assertEquals(query.criteria!!.value, query1.criteria!!.value)
    }

    @Test
    fun testBufferable() {
        val bufferableObject = BufferStreamableObject()
        bufferableObject.myInt = 33
        bufferableObject.myString = "This"
        bufferableObject.simple = Simple()
        bufferableObject.simple!!.hiya = 2

        val buffer = serialize(bufferableObject)
        val bufferableObject1 = deserialize(buffer) as BufferStreamableObject?
        BufferPool.recycle(buffer)

        assertNotNull(bufferableObject1)
        assertEquals(33, bufferableObject1!!.myInt)
        assertEquals("This", bufferableObject1.myString)
        assertEquals(2, bufferableObject1.simple!!.hiya)

    }

    @Test
    fun testNestedCollection() {
        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("correlation", QueryCriteriaOperator.NOT_EQUAL, 99).and("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        val buffer = serialize(findQuery)
        val query = deserialize(buffer) as Query?
        BufferPool.recycle(buffer)

        assertEquals(findQuery.entityType, query!!.entityType)
        assertEquals(findQuery.criteria!!.value, query.criteria!!.value)
        assertEquals(findQuery.criteria!!.subCriteria[0].attribute, query.criteria!!.subCriteria[0].attribute)
        assertEquals(findQuery.criteria!!.subCriteria[0].value, query.criteria!!.subCriteria[0].value)
        assertEquals(findQuery.criteria!!.operator, query.criteria!!.operator)
        assertEquals(findQuery.maxResults, query.maxResults)
    }

    @Test
    fun testRecursiveEntry() {
        val parent = OneToOneParent()
        parent.child = OneToOneChild()
        parent.child!!.parent = parent
        parent.correlation = 322
        parent.identifier = "THIS IS AN ID"
        parent.child!!.correlation = 99
        parent.child!!.identifier = "CHILD ID"

        val buffer = serialize(parent)
        val parent1 = deserialize(buffer) as OneToOneParent?
        BufferPool.recycle(buffer)

        assertEquals(99, parent1!!.child!!.correlation)
        assertEquals("CHILD ID", parent1.child!!.identifier)
        assertEquals(322, parent1.correlation)
        assertEquals("THIS IS AN ID", parent1.identifier)

    }

    @Test
    fun testNamedObjectArray() {
        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "HIYA"
        simpleEntity.name = "NAME"
        val objects = arrayOfNulls<Any>(5)
        objects[0] = 1L
        objects[1] = 22.32f
        objects[2] = "This is a string"
        objects[3] = simpleEntity
        objects[4] = 3

        var buffer = serialize(objects)
        val objects1 = deserialize(buffer) as Array<*>?
        BufferPool.recycle(buffer)

        assertEquals(5, objects1!!.size)
        assertEquals(1L, objects1[0])
        assertEquals(22.32f, objects1[1])
        assertEquals("This is a string", objects1[2])
        assertEquals("HIYA", (objects1[3] as SimpleEntity).simpleId)
        assertEquals("NAME", (objects1[3] as SimpleEntity).name)
        assertEquals(3, objects[4])

        for (i in 0..99999) {
            buffer = serialize(objects)
            deserialize(buffer)
            BufferPool.recycle(buffer)
        }
    }

    companion object {
        @Throws(BufferingException::class)
        fun serialize(`object`: Any): ByteBuffer = BufferStream.toBuffer(`object`)

        @Throws(BufferingException::class)
        fun deserialize(buffer: ByteBuffer): Any? = BufferStream.fromBuffer(buffer)
    }

}
