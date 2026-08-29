package com.onyx.vector

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** Versioned binary codec for [VectorRepresentation]. */
object VectorRepresentationCodec {
    private const val MAGIC = 0x4f565250 // OVRP
    private const val FORMAT_VERSION: Short = 3
    private const val LEGACY_FORMAT_VERSION: Short = 2
    private const val LEGACY_FIXED_BYTES = 56
    private const val FIXED_BYTES = 68
    private const val MAX_ARRAY_ITEMS = 16_000_000

    fun encode(value: VectorRepresentation): ByteArray {
        val cells = value.cells
        val cellCounts = value.cellCounts
        val semanticFingerprint = value.semanticFingerprint
        val semanticBands = value.semanticBands
        val featureWords = value.featureWords
        val payloadSize = Math.addExact(
            Math.addExact(
                FIXED_BYTES,
                Math.multiplyExact(
                    cells.size + cellCounts.size + semanticFingerprint.size +
                        semanticBands.size + featureWords.size,
                    Long.SIZE_BYTES
                )
            ),
            value.hnswVector.size
        )
        val bytes = ByteArray(payloadSize + Int.SIZE_BYTES)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.putShort(FORMAT_VERSION)
        buffer.putInt(value.encodingVersion)
        buffer.putShort(value.featureHashBits.toShort())
        buffer.putLong(value.configurationId)
        buffer.putLong(value.calibrationId)
        buffer.putLong(value.hnswCalibrationId)
        buffer.putInt(value.bucketId)
        buffer.putFloat(value.boundaryConfidence)
        putIntArrayAsLongs(buffer, cells)
        putIntArrayAsLongs(buffer, cellCounts)
        putLongArray(buffer, semanticFingerprint)
        putLongArray(buffer, semanticBands)
        putByteArray(buffer, value.hnswVector)
        putLongArray(buffer, featureWords)

        val checksumOffset = buffer.position()
        check(checksumOffset == payloadSize) { "Vector representation size calculation drifted" }
        val crc = CRC32().apply { update(bytes, 0, checksumOffset) }.value.toInt()
        buffer.putInt(crc)
        return bytes
    }

    fun decode(bytes: ByteArray): VectorRepresentation {
        require(bytes.size >= LEGACY_FIXED_BYTES + Int.SIZE_BYTES) { "Vector representation is truncated" }
        val expectedChecksum = ByteBuffer.wrap(bytes, bytes.size - Int.SIZE_BYTES, Int.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .int
        val actualChecksum = CRC32().apply { update(bytes, 0, bytes.size - Int.SIZE_BYTES) }.value.toInt()
        require(expectedChecksum == actualChecksum) { "Vector representation checksum mismatch" }

        val buffer = ByteBuffer.wrap(bytes, 0, bytes.size - Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
        require(buffer.int == MAGIC) { "Unrecognized vector representation magic" }
        val formatVersion = buffer.short
        require(formatVersion == FORMAT_VERSION || formatVersion == LEGACY_FORMAT_VERSION) {
            "Unsupported vector representation format"
        }
        val encodingVersion = buffer.int
        val featureHashBits = buffer.short.toInt() and 0xffff
        val configurationId = buffer.long
        val calibrationId = buffer.long
        val hnswCalibrationId = if (formatVersion >= FORMAT_VERSION) buffer.long else VectorRepresentation.NO_CALIBRATION
        val bucketId = buffer.int
        val boundaryConfidence = buffer.float
        val cells = readIntArrayFromLongs(buffer)
        val cellCounts = readIntArrayFromLongs(buffer)
        val fingerprint = readLongArray(buffer)
        val bands = readLongArray(buffer)
        val hnswVector = if (formatVersion >= FORMAT_VERSION) readByteArray(buffer) else byteArrayOf()
        val featureWords = readLongArray(buffer)
        require(!buffer.hasRemaining()) { "Unexpected trailing vector representation data" }

        return VectorRepresentation(
            encodingVersion = encodingVersion,
            featureHashBits = featureHashBits,
            configurationId = configurationId,
            calibrationId = calibrationId,
            hnswCalibrationId = hnswCalibrationId,
            bucketId = bucketId,
            boundaryConfidence = boundaryConfidence,
            cells = cells,
            cellCounts = cellCounts,
            semanticFingerprint = fingerprint,
            semanticBands = bands,
            hnswVector = hnswVector,
            featureWords = featureWords,
        )
    }

    fun decodeOrNull(bytes: ByteArray?): VectorRepresentation? =
        bytes?.takeIf { it.isNotEmpty() }?.let(::decode)

    private fun putIntArrayAsLongs(buffer: ByteBuffer, values: IntArray) {
        buffer.putInt(values.size)
        values.forEach { buffer.putLong(it.toLong()) }
    }

    private fun putLongArray(buffer: ByteBuffer, values: LongArray) {
        buffer.putInt(values.size)
        values.forEach(buffer::putLong)
    }

    private fun putByteArray(buffer: ByteBuffer, values: ByteArray) {
        buffer.putInt(values.size)
        buffer.put(values)
    }

    private fun readIntArrayFromLongs(buffer: ByteBuffer): IntArray {
        val size = readSize(buffer)
        require(buffer.remaining() >= size * Long.SIZE_BYTES) { "Vector representation array is truncated" }
        return IntArray(size) {
            val value = buffer.long
            require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Cell value is outside the Int domain" }
            value.toInt()
        }
    }

    private fun readLongArray(buffer: ByteBuffer): LongArray {
        val size = readSize(buffer)
        require(buffer.remaining() >= size * Long.SIZE_BYTES) { "Vector representation array is truncated" }
        return LongArray(size) { buffer.long }
    }

    private fun readByteArray(buffer: ByteBuffer): ByteArray {
        val size = readSize(buffer)
        require(buffer.remaining() >= size) { "Vector representation byte array is truncated" }
        return ByteArray(size).also(buffer::get)
    }

    private fun readSize(buffer: ByteBuffer): Int {
        require(buffer.remaining() >= Int.SIZE_BYTES) { "Vector representation array length is missing" }
        val size = buffer.int
        require(size in 0..MAX_ARRAY_ITEMS) { "Invalid vector representation array length $size" }
        return size
    }
}
