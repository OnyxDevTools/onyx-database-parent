package com.onyx.vector

import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VectorRepresentationCodecTest {

    @Test
    fun `semantic signature has a valid serializable no-arg form`() {
        val signature = SemanticVectorSignature::class.java.getDeclaredConstructor().newInstance()
        assertEquals(1L, signature.calibrationId)
        assertEquals(0, signature.bucketId)
        assertContentEquals(intArrayOf(0), signature.cells)
        assertContentEquals(intArrayOf(2), signature.cellCounts)
        assertContentEquals(longArrayOf(0L), signature.fingerprint)
        assertContentEquals(longArrayOf(0L, 0L, 0L, 0L), signature.bands)

        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(signature) }
            output.toByteArray()
        }
        val decoded = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as SemanticVectorSignature
        }
        assertEquals(signature, decoded)
    }

    @Test
    fun `non-default product radices survive representation persistence`() {
        val cells = intArrayOf(2, 6, 1)
        val cellCounts = intArrayOf(3, 7, 2)
        val fingerprint = longArrayOf(0x13579bdf2468ace0L, -0x123456789abcdefL)
        val signature = SemanticVectorSignature(
            calibrationId = 91L,
            bucketId = 41,
            cells = cells,
            cellCounts = cellCounts,
            fingerprint = fingerprint,
            bands = SemanticVectorSignature.splitIntoFourBands(fingerprint),
            boundaryConfidence = 0.75f,
        )
        val representation = VectorRepresentation(
            encodingVersion = 3,
            featureHashBits = 64,
            configurationId = 72L,
            calibrationId = signature.calibrationId,
            bucketId = signature.bucketId,
            boundaryConfidence = signature.boundaryConfidence,
            cells = signature.cells,
            cellCounts = signature.cellCounts,
            semanticFingerprint = signature.fingerprint,
            semanticBands = signature.bands,
            featureWords = longArrayOf(17L, 29L),
        )

        cells.fill(0)
        cellCounts.fill(10)
        fingerprint.fill(0L)
        representation.cells.fill(0)
        representation.cellCounts.fill(10)
        representation.semanticFingerprint.fill(0L)
        representation.semanticBands.fill(0L)
        representation.featureWords.fill(0L)

        assertContentEquals(intArrayOf(2, 6, 1), representation.cells)
        assertContentEquals(intArrayOf(3, 7, 2), representation.cellCounts)
        assertContentEquals(longArrayOf(0x13579bdf2468ace0L, -0x123456789abcdefL), representation.semanticFingerprint)
        assertContentEquals(longArrayOf(17L, 29L), representation.featureWords)

        val decoded = VectorRepresentationCodec.decode(VectorRepresentationCodec.encode(representation))
        assertEquals(representation, decoded)
        assertContentEquals(intArrayOf(2, 6, 1), decoded.cells)
        assertContentEquals(intArrayOf(3, 7, 2), decoded.cellCounts)
        assertEquals(41, decoded.bucketId)
        assertEquals(signature, SemanticVectorSignature(
            calibrationId = decoded.calibrationId,
            bucketId = decoded.bucketId,
            cells = decoded.cells,
            cellCounts = decoded.cellCounts,
            fingerprint = decoded.semanticFingerprint,
            bands = decoded.semanticBands,
            boundaryConfidence = decoded.boundaryConfidence,
        ))
    }

    @Test
    fun `independent HNSW calibration and quantized vector survive defensively`() {
        val hnsw = byteArrayOf(127, 0, -64, 32)
        val representation = VectorRepresentation(
            encodingVersion = 1,
            featureHashBits = 64,
            configurationId = 72L,
            hnswCalibrationId = 9_007_199_254_740_991L,
            hnswVector = hnsw,
        )

        hnsw.fill(0)
        representation.hnswVector.fill(0)
        assertContentEquals(byteArrayOf(127, 0, -64, 32), representation.hnswVector)
        assertEquals(true, representation.hasHnswVector)
        assertEquals(false, representation.hasSemanticSignature)

        val decoded = VectorRepresentationCodec.decode(VectorRepresentationCodec.encode(representation))
        assertEquals(representation, decoded)
        assertEquals(9_007_199_254_740_991L, decoded.hnswCalibrationId)
        assertContentEquals(byteArrayOf(127, 0, -64, 32), decoded.hnswVector)
    }

    @Test
    fun `codec v3 reads signature-only v2 representations as non-HNSW rows`() {
        val signature = SemanticVectorSignature(
            calibrationId = 91L,
            bucketId = 1,
            cells = intArrayOf(1),
            cellCounts = intArrayOf(2),
            fingerprint = longArrayOf(0x1357_2468_1357_2468L),
            bands = SemanticVectorSignature.splitIntoFourBands(longArrayOf(0x1357_2468_1357_2468L)),
        )
        val legacy = VectorRepresentation(
            encodingVersion = 1,
            featureHashBits = 64,
            configurationId = 72L,
            calibrationId = signature.calibrationId,
            bucketId = signature.bucketId,
            cells = signature.cells,
            cellCounts = signature.cellCounts,
            semanticFingerprint = signature.fingerprint,
            semanticBands = signature.bands,
            featureWords = longArrayOf(17L),
        )

        val decoded = VectorRepresentationCodec.decode(encodeLegacyV2(legacy))

        assertEquals(legacy, decoded)
        assertEquals(false, decoded.hasHnswVector)
        assertEquals(VectorRepresentation.NO_CALIBRATION, decoded.hnswCalibrationId)
        assertContentEquals(byteArrayOf(), decoded.hnswVector)
    }

    @Test
    fun `signature rejects missing mismatched and out-of-bounds radices`() {
        val fingerprint = longArrayOf(1L)
        val bands = SemanticVectorSignature.splitIntoFourBands(fingerprint)

        assertFailsWith<IllegalArgumentException> {
            SemanticVectorSignature(1L, 0, intArrayOf(0), intArrayOf(), fingerprint, bands, 1f)
        }
        assertFailsWith<IllegalArgumentException> {
            SemanticVectorSignature(1L, 0, intArrayOf(2), intArrayOf(2), fingerprint, bands, 1f)
        }
        assertFailsWith<IllegalArgumentException> {
            SemanticVectorSignature(1L, 1, intArrayOf(0), intArrayOf(2), fingerprint, bands, 1f)
        }
        assertFailsWith<IllegalArgumentException> {
            SemanticVectorSignature(
                1L,
                0,
                intArrayOf(0, 0),
                intArrayOf(Int.MAX_VALUE, Int.MAX_VALUE),
                fingerprint,
                bands,
                1f,
            )
        }
    }

    private fun encodeLegacyV2(value: VectorRepresentation): ByteArray {
        val cells = value.cells
        val cellCounts = value.cellCounts
        val fingerprint = value.semanticFingerprint
        val bands = value.semanticBands
        val features = value.featureWords
        val payloadSize = 56 +
            (cells.size + cellCounts.size + fingerprint.size + bands.size + features.size) * Long.SIZE_BYTES
        val bytes = ByteArray(payloadSize + Int.SIZE_BYTES)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(0x4f565250)
        buffer.putShort(2.toShort())
        buffer.putInt(value.encodingVersion)
        buffer.putShort(value.featureHashBits.toShort())
        buffer.putLong(value.configurationId)
        buffer.putLong(value.calibrationId)
        buffer.putInt(value.bucketId)
        buffer.putFloat(value.boundaryConfidence)
        putLegacyArray(buffer, LongArray(cells.size) { cells[it].toLong() })
        putLegacyArray(buffer, LongArray(cellCounts.size) { cellCounts[it].toLong() })
        putLegacyArray(buffer, fingerprint)
        putLegacyArray(buffer, bands)
        putLegacyArray(buffer, features)
        check(buffer.position() == payloadSize)
        buffer.putInt(CRC32().apply { update(bytes, 0, payloadSize) }.value.toInt())
        return bytes
    }

    private fun putLegacyArray(buffer: ByteBuffer, values: LongArray) {
        buffer.putInt(values.size)
        values.forEach(buffer::putLong)
    }
}
