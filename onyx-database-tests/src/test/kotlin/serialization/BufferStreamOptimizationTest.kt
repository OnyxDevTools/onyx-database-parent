package serialization

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferObjectType
import com.onyx.buffer.BufferStream
import com.onyx.buffer.ExpandableByteBuffer
import com.onyx.exception.BufferUnderflowException
import com.onyx.exception.BufferingException
import org.junit.Test
import pojo.Simple
import java.nio.ByteBuffer
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BufferStreamOptimizationTest {

    @Test
    fun defaultStreamUsesTwoKilobyteBuffer() {
        val stream = BufferStream()
        assertEquals(2 * 1024, BufferPool.SMALL_BUFFER_SIZE)
        assertEquals(2 * 1024, stream.byteBuffer.capacity())
        stream.recycle()
    }

    @Test
    fun expandableBufferGrowsThroughPoolBucketsThenGeometrically() {
        val expandable = ExpandableByteBuffer(ByteBuffer.allocate(BufferPool.SMALL_BUFFER_SIZE))
        expandable.buffer.position(expandable.buffer.capacity())

        expandable.ensureSize(1)
        assertEquals(BufferPool.MEDIUM_BUFFER_SIZE, expandable.buffer.capacity())

        expandable.buffer.position(expandable.buffer.capacity())
        expandable.ensureSize(1)
        assertEquals(18 * 1024, expandable.buffer.capacity())

        expandable.buffer.position(expandable.buffer.capacity())
        expandable.ensureSize(1)
        assertEquals(36 * 1024, expandable.buffer.capacity())

        BufferPool.recycle(expandable.buffer)
    }

    @Test
    fun appendingBufferUsesOnlyItsRemainingBytes() {
        val destination = ByteBuffer.allocate(6)
        val expandable = ExpandableByteBuffer(destination)
        val source = ByteBuffer.wrap(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
        source.position(3)

        expandable.put(source)

        assertSame(destination, expandable.buffer)
        assertEquals(5, expandable.buffer.position())
        expandable.buffer.flip()
        val actual = ByteArray(expandable.buffer.remaining())
        expandable.buffer.get(actual)
        assertContentEquals(byteArrayOf(3, 4, 5, 6, 7), actual)
    }

    @Test
    fun bulkArrayWriteRetainsItsVoidJvmSignature() {
        val method = BufferStream::class.java.getMethod("putArray", Any::class.java)

        assertEquals(java.lang.Void.TYPE, method.returnType)
    }

    @Test
    fun directByteBufferReaderRetainsAliases() {
        val shared = Simple().apply { hiya = 42 }
        val writer = BufferStream()
        writer.putObject(arrayOf<Any?>(shared, shared))
        writer.flip()

        val decoded = BufferStream(writer.byteBuffer).value as Array<*>

        assertSame(decoded[0], decoded[1])
        assertEquals(42, (decoded[0] as Simple).hiya)
        writer.recycle()
    }

    @Test
    fun recursiveObjectsStillResolveToTheHydratedInstance() {
        val original = ReferenceNode().apply {
            name = "root"
            next = this
        }

        val buffer = BufferStream.toBuffer(original)
        val decoded = BufferStream.fromBuffer(buffer) as ReferenceNode

        assertEquals("root", decoded.name)
        assertSame(decoded, decoded.next)
        BufferPool.recycle(buffer)
    }

    @Test
    fun pairAliasesKeepWriterReferenceOrder() {
        val pair = Pair(Simple().apply { hiya = 7 }, "value")

        val buffer = BufferStream.toBuffer(arrayOf(pair, pair))
        val decoded = BufferStream.fromBuffer(buffer) as Array<*>

        assertTrue(decoded[0] is Pair<*, *>)
        assertSame(decoded[0], decoded[1])
        assertEquals(7, ((decoded[0] as Pair<*, *>).first as Simple).hiya)
        BufferPool.recycle(buffer)
    }

    @Test
    fun repeatedClassReferencesResolveFromReaderList() {
        val values = arrayOf<Any?>(Simple::class.java, Simple::class.java)

        val buffer = BufferStream.toBuffer(values)
        val decoded = BufferStream.fromBuffer(buffer) as Array<*>
        assertSame(Simple::class.java, decoded[0])
        assertSame(decoded[0], decoded[1])
        BufferPool.recycle(buffer)
    }

    @Test
    fun highestEncodableReferenceIndexStillResolves() {
        val pairs = arrayOfNulls<Any>(Short.MAX_VALUE.toInt() + 1)
        for (index in 0 until Short.MAX_VALUE.toInt())
            pairs[index] = Pair(index, index)
        pairs[pairs.lastIndex] = pairs[pairs.lastIndex - 1]

        val buffer = BufferStream.toBuffer(pairs)
        val decoded = BufferStream.fromBuffer(buffer) as Array<*>

        assertSame(decoded[decoded.lastIndex - 1], decoded.last())
        BufferPool.recycle(buffer)
    }

    @Test
    fun framedReadAdvancesFromANonZeroStartingPosition() {
        val serialized = BufferStream.toBuffer(doubleArrayOf(1.25, -2.5, 4.0))
        val serializedSize = serialized.remaining()
        val container = ByteBuffer.allocate(serializedSize + 5)
        container.put(byteArrayOf(9, 8, 7))
        container.put(serialized)
        container.put(byteArrayOf(6, 5))
        container.flip()
        container.position(3)

        val decoded = BufferStream.fromBuffer(container) as DoubleArray

        assertContentEquals(doubleArrayOf(1.25, -2.5, 4.0), decoded)
        assertEquals(3 + serializedSize, container.position())
        BufferPool.recycle(serialized)
    }

    @Test
    fun framedReaderRejectsLegacyFormat() {
        val legacyFrame = ByteBuffer.wrap(
            byteArrayOf(0, 0, 0, 9, BufferObjectType.INT.ordinal.toByte(), 0, 0, 0, 1)
        )

        assertFailsWith<BufferingException> {
            BufferStream.fromBuffer(legacyFrame)
        }
    }

    @Test
    fun compactReaderRejectsStringLengthBeyondFrameBeforeAllocating() {
        val frame = ByteBuffer.allocate(16)
        frame.position(Integer.BYTES)
        frame.put(2.toByte())
        frame.put(BufferObjectType.STRING.ordinal.toByte())
        putUnsignedInt(frame, (1_000_000 shl 1) or 1)
        val frameSize = frame.position()
        frame.putInt(0, frameSize or Int.MIN_VALUE)
        frame.flip()

        assertFailsWith<BufferUnderflowException> {
            BufferStream.fromBuffer(frame)
        }
    }

    private fun putUnsignedInt(buffer: ByteBuffer, value: Int) {
        var remaining = value
        while (remaining and -0x80 != 0) {
            buffer.put(((remaining and 0x7f) or 0x80).toByte())
            remaining = remaining ushr 7
        }
        buffer.put(remaining.toByte())
    }
}

class ReferenceNode {
    var name: String? = null
    var next: ReferenceNode? = null
}
