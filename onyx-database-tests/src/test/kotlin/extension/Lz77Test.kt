package extension

import com.onyx.extension.common.compressLz77
import com.onyx.extension.common.decompressLz77
import com.onyx.extension.common.decompressLz77ToString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Lz77Test {

    @Test
    fun `string round trip preserves utf8 exactly`() {
        val value = "Onyx 💎 — null:\u0000 — 日本語 — ".repeat(200)

        val compressed = value.compressLz77()

        assertEquals(value, compressed.decompressLz77ToString())
        assertTrue(compressed.size < value.toByteArray().size)
    }

    @Test
    fun `byte array round trips edge cases`() {
        val values = listOf(
            byteArrayOf(),
            byteArrayOf(1),
            byteArrayOf(1, 2, 3),
            byteArrayOf(1, 2, 3, 4),
            ByteArray(32) { 7 },
            ByteArray(4096) { (it % 251).toByte() },
            ByteArray(100_000) { (it and 0xff).toByte() }
        )

        values.forEach { value ->
            assertContentEquals(value, value.compressLz77().decompressLz77())
        }
    }

    @Test
    fun `random input is always lossless`() {
        val random = Random(0x77)
        val sizes = intArrayOf(0, 1, 2, 3, 4, 5, 14, 15, 16, 254, 255, 256, 1024, 8192)

        repeat(20) {
            sizes.forEach { size ->
                val value = random.nextBytes(size)
                assertContentEquals(value, value.compressLz77().decompressLz77())
            }
        }
    }

    @Test
    fun `randomized repetitive input is always lossless`() {
        val random = Random(77_77)

        repeat(250) {
            val value = ByteArray(random.nextInt(1, 32_768))
            value.indices.forEach { index ->
                value[index] = if (index > 0 && random.nextInt(4) != 0) {
                    value[random.nextInt(index)]
                } else {
                    random.nextInt(256).toByte()
                }
            }

            assertContentEquals(value, value.compressLz77().decompressLz77())
        }
    }

    @Test
    fun `incompressible data uses bounded raw frame`() {
        val value = Random(42).nextBytes(8192)

        val compressed = value.compressLz77()

        assertTrue(compressed.size <= value.size + 10)
        assertContentEquals(value, compressed.decompressLz77())
    }

    @Test
    fun `repeated data uses overlapping matches`() {
        val value = ByteArray(1_000_000) { 'a'.code.toByte() }

        val compressed = value.compressLz77()

        assertTrue(compressed.size < 5_000)
        assertContentEquals(value, compressed.decompressLz77())
    }

    @Test
    fun `malformed frames are rejected`() {
        assertFailsWith<IllegalArgumentException> { byteArrayOf().decompressLz77() }
        assertFailsWith<IllegalArgumentException> { ByteArray(10).decompressLz77() }

        val truncated = "compress me compress me".repeat(20).compressLz77().dropLast(1).toByteArray()
        assertFailsWith<IllegalArgumentException> { truncated.decompressLz77() }

        val invalidDistance = "abcdabcdabcdabcd".compressLz77().also {
            // First sequence has four literals, then its two-byte match distance.
            it[15] = 0
            it[16] = 0
        }
        assertFailsWith<IllegalArgumentException> { invalidDistance.decompressLz77() }
    }
}
