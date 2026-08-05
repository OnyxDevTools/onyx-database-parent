package serialization

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.buffer.ExpandableByteBuffer
import org.junit.Test
import pojo.Simple
import java.nio.ByteBuffer
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BufferStreamOptimizationTest {

    @Test
    fun defaultStreamUsesSmallBuffer() {
        val stream = BufferStream()
        assertEquals(BufferPool.SMALL_BUFFER_SIZE, stream.byteBuffer.capacity())
        stream.recycle()
    }

    @Test
    fun expandableBufferGrowsThroughPoolBucketsThenGeometrically() {
        val expandable = ExpandableByteBuffer(ByteBuffer.allocate(512))
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
    fun optimizedPrimitiveWritesKeepExistingWireBytes() {
        val intBuffer = BufferStream.toBuffer(1)
        assertContentEquals(
            byteArrayOf(0, 0, 0, 9, 22, 0, 0, 0, 1),
            intBuffer.copyRemainingBytes()
        )
        BufferPool.recycle(intBuffer)

        val bytesBuffer = BufferStream.toBuffer(byteArrayOf(1, 2, 3, 4))
        assertContentEquals(
            byteArrayOf(0, 0, 0, 13, 11, 0, 0, 0, 4, 1, 2, 3, 4),
            bytesBuffer.copyRemainingBytes()
        )
        BufferPool.recycle(bytesBuffer)
    }

    @Test
    fun bulkArrayWriteRetainsItsVoidJvmSignature() {
        val method = BufferStream::class.java.getMethod("putArray", Any::class.java)

        assertEquals(java.lang.Void.TYPE, method.returnType)
    }

    @Test
    fun optimizedDoubleArrayWritesKeepExistingWireBytes() {
        val buffer = BufferStream.toBuffer(doubleArrayOf(1.25, -2.5))

        assertContentEquals(
            """
                00000019
                10 00000002
                3ff4000000000000
                c004000000000000
            """.hexBytes(),
            buffer.copyRemainingBytes()
        )
        BufferPool.recycle(buffer)
    }

    @Test
    fun collectionWritesKeepExistingWireBytes() {
        val buffer = BufferStream.toBuffer(arrayListOf(1, 2))

        assertContentEquals(
            """
                0000002b
                21
                20 00000013 6a6176612e7574696c2e41727261794c697374
                00000002
                16 00000001
                16 00000002
            """.hexBytes(),
            buffer.copyRemainingBytes()
        )
        BufferPool.recycle(buffer)
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

        assertContentEquals(
            """
                00000031
                24
                20 0000001b 73657269616c697a6174696f6e2e5265666572656e63654e6f6465
                1f 00000004 726f6f74
                01 0002
            """.hexBytes(),
            buffer.copyRemainingBytes()
        )

        val decoded = BufferStream.fromBuffer(buffer) as ReferenceNode

        assertEquals("root", decoded.name)
        assertSame(decoded, decoded.next)
        BufferPool.recycle(buffer)
    }

    @Test
    fun pairAliasesKeepWriterReferenceOrder() {
        val pair = Pair(Simple().apply { hiya = 7 }, "value")
        val buffer = BufferStream.toBuffer(arrayOf(pair, pair))

        assertContentEquals(
            """
                0000003b
                14 0000000b 6b6f746c696e2e50616972
                00000002
                25
                24 20 0000000b 706f6a6f2e53696d706c65 00000007
                1f 00000005 76616c7565
                01 0002
            """.hexBytes(),
            buffer.copyRemainingBytes()
        )

        val decoded = BufferStream.fromBuffer(buffer) as Array<*>

        assertTrue(decoded[0] is Pair<*, *>)
        assertSame(decoded[0], decoded[1])
        assertEquals(7, ((decoded[0] as Pair<*, *>).first as Simple).hiya)
        BufferPool.recycle(buffer)
    }

    @Test
    fun repeatedClassReferencesResolveFromReaderList() {
        val buffer = BufferStream.toBuffer(arrayOf<Any?>(Simple::class.java, Simple::class.java))

        assertContentEquals(
            """
                0000001c
                13 00000002
                20 0000000b 706f6a6f2e53696d706c65
                01 0001
            """.hexBytes(),
            buffer.copyRemainingBytes()
        )

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

    private fun ByteBuffer.copyRemainingBytes(): ByteArray {
        val copy = duplicate()
        return ByteArray(copy.remaining()).also { copy.get(it) }
    }

    private fun String.hexBytes(): ByteArray =
        filterNot(Char::isWhitespace)
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
}

class ReferenceNode {
    var name: String? = null
    var next: ReferenceNode? = null
}
