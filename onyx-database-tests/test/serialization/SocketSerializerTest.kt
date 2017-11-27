package serialization

import com.onyx.buffer.BufferStream
import org.junit.Test
import pojo.*

import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Created by timothy.osborn on 4/14/15.
 */
class SocketSerializerTest {

    @Test
    fun testSimple() {
        val instance = Simple()
        instance.hiya = 4

        val buffer = BufferStream.toBuffer(instance)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as Simple?
        assertEquals(instance.hiya, instance2!!.hiya)
    }

    @Test
    fun testAllValues() {
        val instance = AllTypes()
        instance.intValue = 654
        instance.intValueM = 246
        instance.longValue = 998L
        instance.longValueM = 999L
        instance.booleanValue = false
        instance.booleanValueM = true
        instance.shortValue = 44
        instance.shortValueM = 45
        instance.doubleValue = 12.123
        instance.doubleValueM = 23.124
        instance.floatValue = 11.2f
        instance.floatValueM = 23.2f
        instance.byteValue = 2.toByte()
        instance.byteValueM = java.lang.Byte.valueOf(3.toByte())
        instance.dateValue = Date(3988)
        instance.stringValue = "Test String"
        instance.nullValue = null
        instance.charValue = 'A'

        val buffer = BufferStream.toBuffer(instance)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as AllTypes?

        assertEquals(instance.intValue, instance2!!.intValue)
        assertEquals(instance.intValueM, instance2.intValueM)
        assertEquals(instance.longValue, instance2.longValue)
        assertEquals(instance.longValueM, instance2.longValueM)
        assertEquals(instance.booleanValue, instance2.booleanValue)
        assertEquals(instance.booleanValueM, instance2.booleanValueM)
        assertEquals(instance.shortValueM, instance2.shortValueM)
        assertEquals(instance.doubleValue, instance2.doubleValue)
        assertEquals(instance.shortValue, instance2.shortValue)
        assertEquals(instance.doubleValueM, instance2.doubleValueM)
        assertEquals(instance.floatValue, instance2.floatValue)
        assertEquals(instance.floatValueM, instance2.floatValueM)
        assertEquals(instance.byteValue, instance2.byteValue)
        assertEquals(instance.byteValueM, instance2.byteValueM)
        assertEquals(instance.dateValue!!.time, instance2.dateValue!!.time)
        assertEquals(instance.stringValue, instance2.stringValue)
        assertEquals(instance.nullValue, instance2.nullValue)
        assertEquals(instance.charValue, instance2.charValue)
    }

    @Test
    fun testEnum() {
        val instance = EnumTypeObject()
        instance.intValue = 44
        instance.simpleEnum = SimpleEnum.SECOND
        instance.longValue = 234235245L

        val buffer = BufferStream.toBuffer(instance)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as EnumTypeObject?

        assertEquals(instance.intValue, instance2!!.intValue)
        assertEquals(instance.longValue, instance2.longValue)
        assertEquals(instance.simpleEnum, instance2.simpleEnum)
    }

    @Test
    fun testTransient() {
        val instance = TransientValue()
        instance.intValue = 44
        instance.longValue = 234L
        instance.zDateValue = Date(23423)

        val buffer = BufferStream.toBuffer(instance)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as TransientValue?

        assertEquals(instance.intValue, instance2!!.intValue)
        assertNotEquals(instance.longValue, instance2.longValue)
        assertEquals(instance.zDateValue!!.time, instance2.zDateValue!!.time)
    }


    @Test
    fun testArrayObject() {
        val instance = ArrayObject()
        instance.longArray = arrayOfNulls(3)
        instance.objectArray = arrayOfNulls(4)
        instance.simpleArray = arrayOfNulls(2)

        instance.longArray!![0] = 223L
        instance.longArray!![1] = 293L
        instance.longArray!![2] = 323L

        val obj = AllTypes()
        obj.intValue = 23

        instance.objectArray!![3] = obj

        instance.simpleArray!![1] = Simple()
        instance.simpleArray!![1]!!.hiya = 99


        val buffer = BufferStream.toBuffer(instance)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as ArrayObject?

        assertEquals(223L, instance2!!.longArray!![0])
        assertEquals(293L, instance2.longArray!![1])
        assertEquals(323L, instance2.longArray!![2])

        assertNull(instance2.objectArray!![0])
        assertNull(instance2.objectArray!![1])
        assertTrue(instance2.objectArray!![3] is AllTypes)
        assertEquals(23, (instance2.objectArray!![3] as AllTypes).intValue)

        assertNull(instance2.simpleArray!![0])
        assertTrue(instance2.simpleArray!![1] is Simple)
        assertEquals(99, instance2.simpleArray!![1]!!.hiya)
    }

    @Test
    fun testListObject() {
        val instance = ListObject()
        instance.longArray = ArrayList()
        instance.objectArray = ArrayList()
        instance.simpleArray = ArrayList()

        instance.longArray!!.add(223L)
        instance.longArray!!.add(293L)
        instance.longArray!!.add(323L)

        val obj = AllTypes()
        obj.intValue = 23

        instance.objectArray!!.add(obj)

        instance.simpleArray!!.add(Simple())
        instance.simpleArray!![0].hiya = 99


        val buffer = BufferStream.toBuffer(instance)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as ListObject?

        assertEquals(223L, instance2!!.longArray!![0])
        assertEquals(293L, instance2.longArray!![1])
        assertEquals(323L, instance2.longArray!![2])
        assertTrue(instance2.objectArray!![0] is AllTypes)
        assertEquals(23, (instance2.objectArray!![0] as AllTypes).intValue)
        assertEquals(99, instance2.simpleArray!![0].hiya)

    }

    @Test
    fun testMapSerialization() {
        val instance = MapObject()
        instance.simpleMap = HashMap()

        instance.simpleMap!!.put("NEW", Simple())
        instance.simpleMap!!.put("NEW2", Simple())
        instance.simpleMap!!.put("NEW3", Simple())
        instance.simpleMap!!.put("NEW4", null)

        instance.simpleMap!!["NEW"]!!.hiya = 2324
        instance.simpleMap!!["NEW3"]!!.hiya = 2924

        instance.objectMap = HashMap()
        instance.objectMap!!.put(23, 22)
        instance.objectMap!!.put(12, false)
        instance.objectMap!!.put(Simple(), AllTypes())

        val buffer = BufferStream.toBuffer(instance)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as MapObject?

        assertEquals(4, instance2!!.simpleMap!!.size)
        assertEquals(2324, instance2.simpleMap!!["NEW"]!!.hiya)
        assertEquals(2924, instance2.simpleMap!!["NEW3"]!!.hiya)
        assertEquals(3, instance2.simpleMap!!["NEW2"]!!.hiya)
        assertNull(instance2.simpleMap!!["NEW4"])

        assertEquals(3, instance2.objectMap!!.size)
        assertEquals(22, instance2.objectMap!![23])
        assertEquals(AllTypes(), instance2.objectMap!![Simple()])
        assertEquals(false, instance2.objectMap!![12])
    }

    @Test
    fun testComplexObject() {
        val `object` = ComplexObject()
        `object`.child = ComplexObjectChild()
        `object`.child!!.parent = `object`
        `object`.mine = `object`

        `object`.dateValue = Date(23423)
        `object`.child!!.longValue = 33L

        val buffer = BufferStream.toBuffer(`object`)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as ComplexObject?

        assertEquals(instance2!!.dateValue!!.time, `object`.dateValue!!.time)
        assertEquals(instance2.child!!.longValue, `object`.child!!.longValue)
        assertEquals(instance2.child!!.parent, instance2)
        assertEquals(instance2.mine, instance2)
    }

}

