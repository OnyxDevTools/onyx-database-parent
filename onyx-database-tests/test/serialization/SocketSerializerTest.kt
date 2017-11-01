package serialization

import com.onyx.buffer.BufferStream
import com.onyx.exception.BufferingException
import org.junit.Assert
import org.junit.Test
import pojo.*

import java.util.ArrayList
import java.util.Date
import java.util.HashMap

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

        Assert.assertTrue(instance.hiya == instance2!!.hiya)
    }

    @Test
    @Throws(BufferingException::class)
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

        Assert.assertTrue(instance.intValue == instance2!!.intValue)
        Assert.assertTrue(instance.intValueM == instance2.intValueM)
        Assert.assertTrue(instance.longValue == instance2.longValue)
        Assert.assertTrue(instance.longValueM == instance2.longValueM)
        Assert.assertTrue(instance.booleanValue == instance2.booleanValue)
        Assert.assertTrue(instance.booleanValueM == instance2.booleanValueM)
        Assert.assertTrue(instance.shortValueM == instance2.shortValueM)
        Assert.assertTrue(instance.doubleValue == instance2.doubleValue)
        Assert.assertTrue(instance.shortValue == instance2.shortValue)
        Assert.assertTrue(instance.doubleValueM == instance2.doubleValueM)
        Assert.assertTrue(instance.floatValue == instance2.floatValue)
        Assert.assertTrue(instance.floatValueM == instance2.floatValueM)
        Assert.assertTrue(instance.byteValue == instance2.byteValue)
        Assert.assertTrue(instance.byteValueM == instance2.byteValueM)
        Assert.assertTrue(instance.dateValue!!.time == instance2.dateValue!!.time)
        Assert.assertTrue(instance.stringValue == instance2.stringValue)
        Assert.assertTrue(instance.nullValue === instance2.nullValue)
        Assert.assertTrue(instance.charValue == instance2.charValue)
    }

    @Test
    @Throws(BufferingException::class)
    fun testEnum() {
        val instance = EnumTypeObject()
        instance.intValue = 44
        instance.simpleEnum = SimpleEnum.SECOND
        instance.longValue = 234235245L

        val buffer = BufferStream.toBuffer(instance)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as EnumTypeObject?

        Assert.assertTrue(instance.intValue == instance2!!.intValue)
        Assert.assertTrue(instance.longValue == instance2.longValue)
        Assert.assertTrue(instance.simpleEnum === instance2.simpleEnum)
    }

    @Test
    @Throws(BufferingException::class)
    fun testTransient() {
        val instance = TransientValue()
        instance.intValue = 44
        instance.longValue = 234L
        instance.zDateValue = Date(23423)

        val buffer = BufferStream.toBuffer(instance)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as TransientValue?

        Assert.assertTrue(instance.intValue == instance2!!.intValue)
        Assert.assertTrue(instance.longValue != instance2.longValue)
        Assert.assertTrue(instance.zDateValue!!.time == instance2.zDateValue!!.time)
    }


    @Test
    @Throws(BufferingException::class)
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

        Assert.assertTrue(instance2!!.longArray!![0] == 223L)
        Assert.assertTrue(instance2!!.longArray!![1] == 293L)
        Assert.assertTrue(instance2!!.longArray!![2] == 323L)

        Assert.assertTrue(instance2!!.objectArray!![0] == null)
        Assert.assertTrue(instance2.objectArray!![1] == null)
        Assert.assertTrue(instance2.objectArray!![3] is AllTypes)
        Assert.assertTrue((instance2.objectArray!![3] as AllTypes).intValue == 23)

        Assert.assertTrue(instance2.simpleArray!![0] == null)
        Assert.assertTrue(instance2.simpleArray!![1] is Simple)
        Assert.assertTrue(instance2.simpleArray!![1]!!.hiya == 99)

    }

    @Test
    @Throws(BufferingException::class)
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

        Assert.assertTrue(instance2!!.longArray!![0] === 223L)
        Assert.assertTrue(instance2!!.longArray!![1] === 293L)
        Assert.assertTrue(instance2!!.longArray!![2] === 323L)

        Assert.assertTrue(instance2!!.objectArray!![0] is AllTypes)
        Assert.assertTrue((instance2.objectArray!![0] as AllTypes).intValue == 23)

        Assert.assertTrue(instance2.simpleArray!![0] is Simple)
        Assert.assertTrue(instance2.simpleArray!![0].hiya == 99)

    }

    @Test
    @Throws(BufferingException::class)
    fun testMapSerialization() {
        val instance = MapObject()
        instance.simpleMap = HashMap()

        instance.simpleMap!!.put("NEW", Simple())
        instance.simpleMap!!.put("NEW2", Simple())
        instance.simpleMap!!.put("NEW3", Simple())
        instance.simpleMap!!.put("NEW4", null)

        instance.simpleMap!!["NEW"]!!.hiya = 2324
        instance.simpleMap!!["NEW3"]!!.hiya = 2924

        instance.objectMap = HashMap<Any, Any?>()
        instance.objectMap!!.put(23, 22)
        instance.objectMap!!.put(12, false)
        instance.objectMap!!.put(Simple(), AllTypes())

        val buffer = BufferStream.toBuffer(instance)
        buffer.rewind()

        val instance2 = BufferStream.fromBuffer(buffer) as MapObject?

        Assert.assertTrue(instance2!!.simpleMap!!.size == 4)
        Assert.assertTrue(instance2.simpleMap!!["NEW"]!!.hiya == 2324)
        Assert.assertTrue(instance2.simpleMap!!["NEW3"]!!.hiya == 2924)
        Assert.assertTrue(instance2.simpleMap!!["NEW2"]!!.hiya == 3)
        Assert.assertTrue(instance2.simpleMap!!["NEW4"] == null)

        Assert.assertTrue(instance2.objectMap!!.size == 3)
        Assert.assertTrue(instance2.objectMap!![23] == 22)
        Assert.assertTrue(instance2.objectMap!![Simple()] == AllTypes())
        Assert.assertTrue(instance2.objectMap!![12] as Boolean == false)
    }

    @Test
    @Throws(BufferingException::class)
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

        Assert.assertTrue(instance2!!.dateValue!!.time == `object`.dateValue!!.time)
        Assert.assertTrue(instance2.child!!.longValue == `object`.child!!.longValue)
        Assert.assertTrue(instance2.child!!.parent == instance2)
        Assert.assertTrue(instance2.mine == instance2)
    }

}

