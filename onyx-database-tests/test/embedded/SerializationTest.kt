package embedded

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.network.transport.data.RequestToken
import com.onyx.exception.OnyxException
import entities.AllAttributeEntity
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runners.MethodSorters
import remote.base.RemoteBaseTest

import java.io.Serializable
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.Date

/**
 * Created by timothy.osborn on 12/13/14.
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SerializationTest : RemoteBaseTest() {


    @Test
    @Ignore
    @Throws(OnyxException::class)
    fun testBasic() {

        val entity = AllAttributeEntity()
        entity.id = BigInteger(130, random).toString(32)
        entity.longValue = 4L
        entity.longPrimitive = 3L
        entity.stringValue = "STring key"
        entity.dateValue = Date(1483736263743L)
        entity.doublePrimitive = 342.23
        entity.doubleValue = 232.2
        entity.booleanPrimitive = true
        entity.booleanValue = false

        val token = RequestToken(java.lang.Short.MIN_VALUE, entity)


        val buf = BufferStream.toBuffer(token)
        val token2 = BufferStream.fromBuffer(buf) as RequestToken?
        BufferPool.recycle(buf)

        Assert.assertTrue(token2!!.token == token.token)


    }

    @Test
    @Ignore
    @Throws(Exception::class)
    fun testList() {
        val arrayList = ArrayList<AllAttributeEntity>()
        arrayList.add(AllAttributeEntity())

        val buffer = BufferStream.toBuffer(arrayList)
        buffer.rewind()

        val other = BufferStream.fromBuffer(buffer) as ArrayList<*>?
        BufferPool.recycle(buffer)

        assert(other!!.size == 1)
        assert(other[0] is AllAttributeEntity)
    }

    @Test
    @Ignore
    @Throws(OnyxException::class)
    fun testPerformance() {


        val time = System.currentTimeMillis()

        var entities: MutableList<AllAttributeEntity>? = null

        for (i in 0..39) {

            entities = ArrayList()
            for (k in 0..4999) {

                val entity = AllAttributeEntity()
                entity.id = BigInteger(130, random).toString(32)
                entity.longValue = 4L
                entity.longPrimitive = 3L
                entity.stringValue = "STring key"
                entity.dateValue = Date(1483736263743L)
                entity.doublePrimitive = 342.23
                entity.doubleValue = 232.2
                entity.booleanPrimitive = true
                entity.booleanValue = false

                entities.add(entity)
            }

            val token = RequestToken(java.lang.Short.MAX_VALUE, entities as Serializable?)

            val buffer = BufferStream.toBuffer(token)
            buffer.rewind()

            BufferStream.fromBuffer(buffer)
            BufferPool.recycle(buffer)

        }

        println("Done Serializing in " + (System.currentTimeMillis() - time))
    }


}
