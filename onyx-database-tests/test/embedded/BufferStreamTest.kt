package embedded

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.exception.BufferingException
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.impl.DefaultSchemaContext
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.AllAttributeEntity
import entities.SimpleEntity
import entities.index.StringIdentifierEntityIndex
import entities.relationship.OneToOneChild
import entities.relationship.OneToOneParent
import org.junit.Ignore
import org.junit.Test
import pojo.AllTypes
import pojo.BufferStreamableObject
import pojo.Simple

import java.nio.ByteBuffer
import java.util.*

/**
 * Created by Tim Osborn on 7/28/16.
 */
class BufferStreamTest {

    @Test
    @Throws(BufferingException::class)
    fun testSerializePrimitive() {
        // int
        var buffer = serialize(1)
        val value = deserialize(buffer) as Int
        assert(value == 1)
        BufferPool.recycle(buffer)

        // long
        buffer = serialize(2L)
        assert(deserialize(buffer) as Long == 2L)
        BufferPool.recycle(buffer)

        // boolean
        buffer = serialize(true)
        assert(deserialize(buffer) as Boolean == true)
        BufferPool.recycle(buffer)

        // short
        buffer = serialize(34.toShort())
        assert(deserialize(buffer) as Short == 34.toShort())
        BufferPool.recycle(buffer)

        // byte
        buffer = serialize(3.toByte())
        assert(deserialize(buffer) as Byte == 3.toByte())
        BufferPool.recycle(buffer)

        // float
        buffer = serialize(3.3f)
        assert(deserialize(buffer) as Float == 3.3f)
        BufferPool.recycle(buffer)

        // double
        buffer = serialize(3.33)
        assert(deserialize(buffer) as Double == 3.33)
        BufferPool.recycle(buffer)

        // char
        buffer = serialize('C')
        assert(deserialize(buffer) as Char == 'C')
        BufferPool.recycle(buffer)

    }

    @Test
    @Throws(BufferingException::class)
    fun testPrimitiveArrays() {
        var ints = intArrayOf(1, 2, 3, 4)
        var buffer = serialize(ints)
        ints = deserialize(buffer) as IntArray
        assert(ints[0] == 1 && ints[1] == 2 && ints[2] == 3 && ints[3] == 4)
        BufferPool.recycle(buffer)

        var longs = longArrayOf(1L, 2L, 3L, 4L)
        buffer = serialize(longs)
        longs = deserialize(buffer) as LongArray
        assert(longs[0] == 1L && longs[1] == 2L && longs[2] == 3L && longs[3] == 4L)
        BufferPool.recycle(buffer)

        var bytes = byteArrayOf(1, 2, 3, 4)
        buffer = serialize(bytes)
        bytes = deserialize(buffer) as ByteArray
        assert(bytes[0].toInt() == 1 && bytes[1].toInt() == 2 && bytes[2].toInt() == 3 && bytes[3].toInt() == 4)
        BufferPool.recycle(buffer)

        var booleans = booleanArrayOf(true, false, true, false)
        buffer = serialize(booleans)
        booleans = deserialize(buffer) as BooleanArray
        assert(booleans[0] && !booleans[1] && booleans[2] && !booleans[3])
        BufferPool.recycle(buffer)

        var floats = floatArrayOf(1.1f, 2.2f, 3.3f, 4.4f)
        buffer = serialize(floats)
        floats = deserialize(buffer) as FloatArray
        assert(floats[0] == 1.1f && floats[1] == 2.2f && floats[2] == 3.3f && floats[3] == 4.4f)
        BufferPool.recycle(buffer)

        var doubles = doubleArrayOf(1.1, 2.2, 3.3, 4.4)
        buffer = serialize(doubles)
        doubles = deserialize(buffer) as DoubleArray
        assert(doubles[0] == 1.1 && doubles[1] == 2.2 && doubles[2] == 3.3 && doubles[3] == 4.4)
        BufferPool.recycle(buffer)

        var shorts = shortArrayOf(1, 2, 3, 4)
        buffer = serialize(shorts)
        shorts = deserialize(buffer) as ShortArray
        assert(shorts[0].toInt() == 1 && shorts[1].toInt() == 2 && shorts[2].toInt() == 3 && shorts[3].toInt() == 4)
        BufferPool.recycle(buffer)

        var chars = charArrayOf('1', '2', '3', '4')
        buffer = serialize(chars)
        chars = deserialize(buffer) as CharArray
        assert(chars[0] == '1' && chars[1] == '2' && chars[2] == '3' && chars[3] == '4')
        BufferPool.recycle(buffer)
    }

    @Test
    @Throws(BufferingException::class)
    fun testString() {
        val value = "ASDF@#$@#\$ASDFASDF"
        val buffer = serialize(value)
        val otherValue = deserialize(buffer) as String?
        assert(value == otherValue)
        BufferPool.recycle(buffer)
    }

    @Test
    @Throws(BufferingException::class)
    fun testDate() {
        val value = Date(3737373)
        val buffer = serialize(value)
        val otherValue = deserialize(buffer) as Date?
        assert(value == otherValue)
        BufferPool.recycle(buffer)
    }

    @Test
    @Ignore
    @Throws(BufferingException::class)
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

        assert(otherValue!!.booleanPrimitive == false)
        assert(otherValue.booleanValue === true)
        assert(otherValue.longValue === 4L)
        assert(otherValue.longPrimitive == 3L)
        assert(otherValue.intValue === 23)
        assert(otherValue.intPrimitive == 22)
        assert(otherValue.stringValue == "234234234")
        assert(otherValue.dateValue == Date(333333))
        assert(otherValue.doublePrimitive == 23.33)
        assert(otherValue.doubleValue === 22.2)
    }

    @Test
    @Throws(BufferingException::class)
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

        assert(entity2!!.booleanValueM == false)
        assert(entity2!!.booleanValue == true)
        assert(entity2.longValue == 4L)
        assert(entity2.longValueM == 3L)
        assert(entity2.intValue == 23)
        assert(entity2.intValueM == 22)
        assert(entity2.stringValue == "234234234")
        assert(entity2.dateValue == Date(333333))
        assert(entity2.shortValue.toInt() == 26)
        assert(entity2.shortValueM == 95.toShort())
        assert(entity2.doubleValue == 32.32)
        assert(entity2.doubleValueM == 22.54)
        assert(entity2.floatValue == 32.321f)
        assert(entity2.floatValueM == 22.542f)
        assert(entity2.byteValue == 4.toByte())
        assert(entity2.byteValueM == 9.toByte())
        assert(entity2.nullValue == null)
        assert(entity2.charValue == 'K')
        assert(entity2.charValueM == 'U')
    }

    @Test
    @Throws(BufferingException::class)
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

        assert(other!![1] as Int == 3)
        assert(other[4] as Long == 6L)
        assert(other[6] as Double == 22.2)
        assert(other[3] as Float == 23.3f)
        assert(other[5] as Boolean == true)
        assert(other[9] as Short == 23.toShort())
        assert(other[99] as Byte == 32.toByte())
        assert(other[87] as Char == 'C')
        assert((other[100] as IntArray)[0] == 3)
        assert((other[100] as IntArray)[1] == 4)
        assert((other[100] as IntArray)[2] == 6)
        assert((other[100] as IntArray)[3] == 3)

    }

    @Test
    @Throws(BufferingException::class)
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

        var i = 0
        for (obj in collection2!!) {
            val entity = obj as AllTypes

            assert(entity.booleanValueM == false)
            assert(entity.booleanValue == true)
            assert(entity.longValue == 4L)
            assert(entity.longValueM == 3L)
            assert(entity.intValue == i)
            assert(entity.intValueM == 22)
            assert(entity.stringValue == "234234234")
            assert(entity.dateValue == Date(333333))
            assert(entity.shortValue.toInt() == 26)
            assert(entity.shortValueM == 95.toShort())
            assert(entity.doubleValue == 32.32)
            assert(entity.doubleValueM == 22.54)
            assert(entity.floatValue == 32.321f)
            assert(entity.floatValueM == 22.542f)
            assert(entity.byteValue == 4.toByte())
            assert(entity.byteValueM == 9.toByte())
            assert(entity.nullValue == null)
            assert(entity.charValue == 'K')
            assert(entity.charValueM == 'U')

            i++

        }
    }

    @Test
    @Throws(BufferingException::class)
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

        var i = 0
        for (obj in collection2!!) {
            val entity = obj as AllTypes

            assert(entity.booleanValueM == false)
            assert(entity.booleanValue)
            assert(entity.longValue == 4L)
            assert(entity.longValueM == 3L)
            assert(entity.intValue == i)
            assert(entity.intValueM == 22)
            assert(entity.stringValue == "234234234")
            assert(entity.dateValue == Date(333333))
            assert(entity.shortValue.toInt() == 26)
            assert(entity.shortValueM == 95.toShort())
            assert(entity.doubleValue == 32.32)
            assert(entity.doubleValueM == 22.54)
            assert(entity.floatValue == 32.321f)
            assert(entity.floatValueM == 22.542f)
            assert(entity.byteValue == 4.toByte())
            assert(entity.byteValueM == 9.toByte())
            assert(entity.nullValue == null)
            assert(entity.charValue == 'K')
            assert(entity.charValueM == 'U')

            i++

        }
    }

    @Test
    @Throws(BufferingException::class)
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


        assert(collection2!![0] as Int == 1)
        assert(collection2[1] as Long == 1L)
        assert(collection2[3] as Boolean == true)

        entity = collection2[2] as AllTypes

        assert(entity.booleanValueM == false)
        assert(entity.booleanValue == true)
        assert(entity.longValue == 4L)
        assert(entity.longValueM == 3L)
        assert(entity.intValue == 55)
        assert(entity.intValueM == 22)
        assert(entity.stringValue == "234234234")
        assert(entity.dateValue == Date(333333))
        assert(entity.shortValue.toInt() == 26)
        assert(entity.shortValueM == 95.toShort())
        assert(entity.doubleValue == 32.32)
        assert(entity.doubleValueM == 22.54)
        assert(entity.floatValue == 32.321f)
        assert(entity.floatValueM == 22.542f)
        assert(entity.byteValue == 4.toByte())
        assert(entity.byteValueM == 9.toByte())
        assert(entity.nullValue == null)
        assert(entity.charValue == 'K')
        assert(entity.charValueM == 'U')
    }

    @Test
    @Ignore
    @Throws(BufferingException::class)
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

        assert(parent1!!.child!!.correlation == 99)
        assert(parent1.child!!.parent == parent1)
        assert(parent1.child!!.identifier == "CHILD ID")
        assert(parent1.correlation == 322)
        assert(parent1.identifier == "THIS IS AN ID")

    }

    @Test
    @Ignore
    @Throws(BufferingException::class)
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
        val objects1 = deserialize(buffer) as Array<Any>?
        BufferPool.recycle(buffer)

        assert(objects1!!.size == 5)
        assert(objects1[0] as Long == 1L)
        assert(objects1[1] as Float == 22.32f)
        assert(objects1[2] == "This is a string")
        assert((objects1[3] as SimpleEntity).simpleId == "HIYA")
        assert((objects1[3] as SimpleEntity).name == "NAME")
        assert(objects[4] as Int == 3)

        val time = System.currentTimeMillis()

        for (i in 0..99999) {
            buffer = serialize(objects)
            deserialize(buffer)
            BufferPool.recycle(buffer)
        }

        val after = System.currentTimeMillis()

        println("Took " + (after - time))
    }

    @Test
    @Throws(BufferingException::class)
    fun testObjectWithEnum() {
        val query = Query(SimpleEntity::class.java, QueryCriteria("attribute", QueryCriteriaOperator.EQUAL, 33L))

        val buffer = serialize(query)
        val query1 = deserialize(buffer) as Query?
        BufferPool.recycle(buffer)

        assert(query.entityType == query1!!.entityType)
        assert(query.criteria!!.type === query1.criteria!!.type)
        assert(query.criteria!!.attribute == query1.criteria!!.attribute)
        assert(query.criteria!!.longValue == query1.criteria!!.longValue)
    }

    @Test
    @Throws(BufferingException::class)
    fun testBufferable() {
        val bufferableObject = BufferStreamableObject()
        bufferableObject.myInt = 33
        bufferableObject.myString = "This"
        bufferableObject.simple = Simple()
        bufferableObject.simple!!.hiya = 2

        val buffer = serialize(bufferableObject)
        val bufferableObject1 = deserialize(buffer) as BufferStreamableObject?
        BufferPool.recycle(buffer)

        assert(bufferableObject1 != null)
        assert(bufferableObject1!!.myInt == 33)
        assert(bufferableObject1.myString == "This")
        assert(bufferableObject1.simple!!.hiya == 2)

    }

    @Test
    @Throws(BufferingException::class)
    fun testNestedCollection() {
        val findQuery = Query(StringIdentifierEntityIndex::class.java, QueryCriteria("correlation", QueryCriteriaOperator.NOT_EQUAL, 99).and("indexValue", QueryCriteriaOperator.EQUAL, "INDEX VALUE"))
        val buffer = serialize(findQuery)
        val query = deserialize(buffer) as Query?
        BufferPool.recycle(buffer)

        assert(findQuery.entityType == query!!.entityType)
        assert(findQuery.criteria!!.integerValue == query.criteria!!.integerValue)
        assert(findQuery.criteria!!.type == query.criteria!!.type)
        assert(findQuery.criteria!!.subCriteria[0].attribute == query.criteria!!.subCriteria[0].attribute)
        assert(findQuery.criteria!!.subCriteria[0].stringValue == query.criteria!!.subCriteria[0].stringValue)

        assert(findQuery.criteria!!.operator == query.criteria!!.operator)
        assert(findQuery.maxResults == query.maxResults)
    }

    companion object {

        @Throws(BufferingException::class)
        fun serialize(`object`: Any): ByteBuffer {
            return BufferStream.toBuffer(`object`)
        }

        @Throws(BufferingException::class)
        fun deserialize(buffer: ByteBuffer): Any? {
            return BufferStream.fromBuffer(buffer)
        }
    }

}
