package com.onyx.vector

import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import java.util.zip.CRC32
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VectorCalibrationCodecTest {

    @Test
    fun `calibration round trips deterministically with a stable versioned header`() {
        val calibration = calibration(sampleCount = 48, seed = 19L)

        val firstEncoding = VectorCalibrationCodec.encode(calibration)
        val secondEncoding = VectorCalibrationCodec.encode(calibration)
        assertContentEquals(firstEncoding, secondEncoding)

        val header = ByteBuffer.wrap(firstEncoding).order(ByteOrder.BIG_ENDIAN)
        assertEquals(0x4f56434c, header.int)
        assertEquals(VectorCalibrationCodec.FORMAT_VERSION, header.int)

        val decoded = VectorCalibrationCodec.decode(firstEncoding)
        assertEquals(calibration, decoded)
        assertEquals(calibration.calibrationId, decoded.calibrationId)
        assertContentEquals(firstEncoding, VectorCalibrationCodec.encode(decoded))
    }

    @Test
    fun `encoded size depends on fitted model shape rather than dense sample count`() {
        val smallCorpus = calibration(sampleCount = 16, seed = 7L)
        val largeCorpus = calibration(sampleCount = 160, seed = 11L)

        assertEquals(
            VectorCalibrationCodec.encode(smallCorpus).size,
            VectorCalibrationCodec.encode(largeCorpus).size
        )
    }

    @Test
    fun `checksum detects payload corruption`() {
        val encoded = VectorCalibrationCodec.encode(calibration())
        val corrupted = encoded.copyOf()
        val payloadByte = 12 + 24 + Int.SIZE_BYTES + Double.SIZE_BYTES
        corrupted[payloadByte] = (corrupted[payloadByte].toInt() xor 0x40).toByte()

        val failure = assertFailsWith<IllegalArgumentException> {
            VectorCalibrationCodec.decode(corrupted)
        }
        assertTrue(failure.message.orEmpty().contains("checksum"))
    }

    @Test
    fun `truncated payloads fail without leaking buffer exceptions`() {
        val encoded = VectorCalibrationCodec.encode(calibration())
        for (length in listOf(0, 1, 11, 15, encoded.size / 2, encoded.size - 1)) {
            assertFailsWith<IllegalArgumentException>("truncation at $length bytes") {
                VectorCalibrationCodec.decode(encoded.copyOf(length))
            }
        }
    }

    @Test
    fun `unsupported versions and impossible declared sizes are rejected before allocation`() {
        val encoded = VectorCalibrationCodec.encode(calibration())

        val futureVersion = encoded.copyOf().also {
            ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).putInt(4, VectorCalibrationCodec.FORMAT_VERSION + 1)
        }
        assertFailsWith<IllegalArgumentException> { VectorCalibrationCodec.decode(futureVersion) }

        val oversizedPayload = encoded.copyOf().also {
            ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).putInt(8, Int.MAX_VALUE)
        }
        assertFailsWith<IllegalArgumentException> { VectorCalibrationCodec.decode(oversizedPayload) }

        // Payload begins after 12 header bytes. Its four Ints and Long consume 24 bytes,
        // followed by the mean-array length.
        val oversizedMean = encoded.copyOf().also {
            ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).putInt(12 + 24, Int.MAX_VALUE)
            refreshChecksum(it)
        }
        val failure = assertFailsWith<IllegalArgumentException> {
            VectorCalibrationCodec.decode(oversizedMean)
        }
        assertTrue(failure.message.orEmpty().contains("mean length"))
    }

    @Test
    fun `null helper distinguishes absent storage from malformed storage`() {
        assertNull(VectorCalibrationCodec.decodeOrNull(null))
        assertNull(VectorCalibrationCodec.decodeOrNull(byteArrayOf()))
        assertFailsWith<IllegalArgumentException> {
            VectorCalibrationCodec.decodeOrNull(byteArrayOf(1))
        }
    }

    private fun calibration(sampleCount: Int = 48, seed: Long = 23L): VectorCalibration =
        VectorCalibration.fit(
            samples = randomSamples(sampleCount, dimensions = 6, seed = seed),
            componentCount = 4,
            firstAxisCells = 4,
            otherAxisCells = 5,
            randomSeed = 817_263L,
        )

    private fun randomSamples(count: Int, dimensions: Int, seed: Long): List<FloatArray> {
        val random = Random(seed)
        return List(count) {
            FloatArray(dimensions) { (random.nextDouble() * 2.0 - 1.0).toFloat() }
        }
    }

    private fun refreshChecksum(bytes: ByteArray) {
        val checksumOffset = bytes.size - Int.SIZE_BYTES
        val checksum = CRC32().apply { update(bytes, 0, checksumOffset) }.value.toInt()
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(checksumOffset, checksum)
    }
}
