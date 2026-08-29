package com.onyx.vector

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Deterministic, versioned binary storage for a shared [VectorCalibration].
 *
 * The format contains only the fitted model: configuration, normalized corpus mean,
 * PCA axes, quantile thresholds, and projection bounds. Calibration samples and document
 * embeddings are never part of the payload.
 */
object VectorCalibrationCodec {
    /** Current on-disk format. Readers reject versions they do not understand. */
    const val FORMAT_VERSION: Int = 1

    private const val MAGIC: Int = 0x4f56434c // OVCL
    private const val PREFIX_BYTES: Int = Int.SIZE_BYTES * 3
    private const val CHECKSUM_BYTES: Int = Int.SIZE_BYTES
    private const val FIXED_PAYLOAD_BYTES: Int = Int.SIZE_BYTES * 4 + Long.SIZE_BYTES
    private const val MAX_PAYLOAD_BYTES: Int = 64 * 1024 * 1024
    private const val MAX_DIMENSIONS: Int = 1_000_000
    private const val MAX_COMPONENTS: Int = 4_096
    private const val MAX_CELLS_PER_AXIS: Int = 1_000_000

    /** Encodes [calibration] without retaining any dense source samples. */
    @JvmStatic
    fun encode(calibration: VectorCalibration): ByteArray {
        val snapshot = Snapshot(
            dimensions = calibration.dimensions,
            componentCount = calibration.componentCount,
            firstAxisCells = calibration.firstAxisCells,
            otherAxisCells = calibration.otherAxisCells,
            randomSeed = calibration.randomSeed,
            mean = calibration.mean,
            components = calibration.components,
            thresholds = calibration.quantileThresholds,
            minimums = calibration.projectionMinimums,
            maximums = calibration.projectionMaximums,
        )
        validateConfiguration(
            snapshot.dimensions,
            snapshot.componentCount,
            snapshot.firstAxisCells,
            snapshot.otherAxisCells
        )
        val payloadSize = encodedPayloadSize(snapshot)
        val checksumOffset = Math.addExact(PREFIX_BYTES, payloadSize)
        val bytes = ByteArray(Math.addExact(checksumOffset, CHECKSUM_BYTES))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(MAGIC)
        buffer.putInt(FORMAT_VERSION)
        buffer.putInt(payloadSize)
        buffer.putInt(snapshot.dimensions)
        buffer.putInt(snapshot.componentCount)
        buffer.putInt(snapshot.firstAxisCells)
        buffer.putInt(snapshot.otherAxisCells)
        buffer.putLong(snapshot.randomSeed)
        putDoubleArray(buffer, snapshot.mean)
        putDoubleMatrix(buffer, snapshot.components)
        putDoubleMatrix(buffer, snapshot.thresholds)
        putDoubleArray(buffer, snapshot.minimums)
        putDoubleArray(buffer, snapshot.maximums)

        check(buffer.position() == checksumOffset) { "Vector calibration size calculation drifted" }
        val checksum = CRC32().apply { update(bytes, 0, checksumOffset) }.value.toInt()
        buffer.putInt(checksum)
        return bytes
    }

    /** Decodes and fully validates a calibration payload. */
    @JvmStatic
    fun decode(bytes: ByteArray): VectorCalibration {
        require(bytes.size >= PREFIX_BYTES + CHECKSUM_BYTES) { "Vector calibration is truncated" }
        require(bytes.size <= PREFIX_BYTES + MAX_PAYLOAD_BYTES + CHECKSUM_BYTES) {
            "Vector calibration exceeds the maximum encoded size"
        }

        val header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        require(header.int == MAGIC) { "Unrecognized vector calibration magic" }
        val version = header.int
        require(version == FORMAT_VERSION) { "Unsupported vector calibration format version $version" }
        val payloadSize = header.int
        require(payloadSize in FIXED_PAYLOAD_BYTES..MAX_PAYLOAD_BYTES) {
            "Invalid vector calibration payload size $payloadSize"
        }
        val expectedSize = PREFIX_BYTES.toLong() + payloadSize.toLong() + CHECKSUM_BYTES.toLong()
        require(expectedSize == bytes.size.toLong()) { "Vector calibration payload is truncated or has trailing data" }

        val checksumOffset = bytes.size - CHECKSUM_BYTES
        val expectedChecksum = ByteBuffer.wrap(bytes, checksumOffset, CHECKSUM_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .int
        val actualChecksum = CRC32().apply { update(bytes, 0, checksumOffset) }.value.toInt()
        require(expectedChecksum == actualChecksum) { "Vector calibration checksum mismatch" }

        val payload = ByteBuffer.wrap(bytes, PREFIX_BYTES, payloadSize)
            .slice()
            .order(ByteOrder.BIG_ENDIAN)
        requireRemaining(payload, FIXED_PAYLOAD_BYTES, "configuration")
        val dimensions = payload.int
        val componentCount = payload.int
        val firstAxisCells = payload.int
        val otherAxisCells = payload.int
        val randomSeed = payload.long

        validateConfiguration(dimensions, componentCount, firstAxisCells, otherAxisCells)

        val mean = readDoubleArray(payload, dimensions, "mean")
        val components = readDoubleMatrix(
            payload,
            componentCount,
            { dimensions },
            "PCA components"
        )
        val thresholds = readDoubleMatrix(
            payload,
            componentCount,
            { axis -> (if (axis == 0) firstAxisCells else otherAxisCells) - 1 },
            "quantile thresholds"
        )
        val minimums = readDoubleArray(payload, componentCount, "projection minimums")
        val maximums = readDoubleArray(payload, componentCount, "projection maximums")
        require(!payload.hasRemaining()) { "Unexpected trailing vector calibration payload data" }

        return VectorCalibration(
            dimensions = dimensions,
            componentCount = componentCount,
            firstAxisCells = firstAxisCells,
            otherAxisCells = otherAxisCells,
            randomSeed = randomSeed,
            mean = mean,
            components = components,
            quantileThresholds = thresholds,
            projectionMinimums = minimums,
            projectionMaximums = maximums,
        )
    }

    /** Treats null and empty storage as an absent calibration; malformed data still fails. */
    @JvmStatic
    fun decodeOrNull(bytes: ByteArray?): VectorCalibration? =
        bytes?.takeIf(ByteArray::isNotEmpty)?.let(::decode)

    private fun encodedPayloadSize(snapshot: Snapshot): Int {
        var size = FIXED_PAYLOAD_BYTES.toLong()
        size = addArraySize(size, snapshot.mean.size)
        size = addMatrixSize(size, snapshot.components)
        size = addMatrixSize(size, snapshot.thresholds)
        size = addArraySize(size, snapshot.minimums.size)
        size = addArraySize(size, snapshot.maximums.size)
        require(size <= MAX_PAYLOAD_BYTES.toLong()) { "Vector calibration exceeds the maximum encoded size" }
        return size.toInt()
    }

    private fun addArraySize(current: Long, itemCount: Int): Long =
        Math.addExact(current, Int.SIZE_BYTES.toLong() + Math.multiplyExact(itemCount.toLong(), Double.SIZE_BYTES.toLong()))

    private fun addMatrixSize(current: Long, values: Array<DoubleArray>): Long {
        var size = Math.addExact(current, Int.SIZE_BYTES.toLong())
        values.forEach { size = addArraySize(size, it.size) }
        return size
    }

    private fun putDoubleArray(buffer: ByteBuffer, values: DoubleArray) {
        buffer.putInt(values.size)
        values.forEach(buffer::putDouble)
    }

    private fun putDoubleMatrix(buffer: ByteBuffer, values: Array<DoubleArray>) {
        buffer.putInt(values.size)
        values.forEach { putDoubleArray(buffer, it) }
    }

    private fun readDoubleArray(buffer: ByteBuffer, expectedSize: Int, label: String): DoubleArray {
        requireRemaining(buffer, Int.SIZE_BYTES, "$label length")
        val size = buffer.int
        require(size == expectedSize) { "Invalid $label length $size; expected $expectedSize" }
        val requiredBytes = size.toLong() * Double.SIZE_BYTES.toLong()
        require(requiredBytes <= buffer.remaining().toLong()) { "Vector calibration $label is truncated" }
        return DoubleArray(size) { buffer.double }
    }

    private fun readDoubleMatrix(
        buffer: ByteBuffer,
        expectedRows: Int,
        expectedColumns: (Int) -> Int,
        label: String
    ): Array<DoubleArray> {
        requireRemaining(buffer, Int.SIZE_BYTES, "$label row count")
        val rows = buffer.int
        require(rows == expectedRows) { "Invalid $label row count $rows; expected $expectedRows" }
        return Array(rows) { row ->
            readDoubleArray(buffer, expectedColumns(row), "$label row $row")
        }
    }

    private fun requireRemaining(buffer: ByteBuffer, count: Int, label: String) {
        require(buffer.remaining() >= count) { "Vector calibration $label is truncated" }
    }

    private fun validateConfiguration(
        dimensions: Int,
        componentCount: Int,
        firstAxisCells: Int,
        otherAxisCells: Int
    ) {
        require(dimensions in 1..MAX_DIMENSIONS) { "Invalid vector calibration dimension count $dimensions" }
        require(componentCount in 1..minOf(dimensions, MAX_COMPONENTS)) {
            "Invalid vector calibration component count $componentCount"
        }
        require(firstAxisCells in 2..MAX_CELLS_PER_AXIS) {
            "Invalid first-axis cell count $firstAxisCells"
        }
        require(otherAxisCells in 2..MAX_CELLS_PER_AXIS) {
            "Invalid other-axis cell count $otherAxisCells"
        }
        validateBucketSpace(componentCount, firstAxisCells, otherAxisCells)
    }

    private fun validateBucketSpace(componentCount: Int, firstAxisCells: Int, otherAxisCells: Int) {
        var buckets = 1L
        repeat(componentCount) { axis ->
            val cells = if (axis == 0) firstAxisCells else otherAxisCells
            require(buckets <= Int.MAX_VALUE.toLong() / cells.toLong()) {
                "Vector calibration product-cell space exceeds the Int bucket domain"
            }
            buckets *= cells.toLong()
        }
    }

    private data class Snapshot(
        val dimensions: Int,
        val componentCount: Int,
        val firstAxisCells: Int,
        val otherAxisCells: Int,
        val randomSeed: Long,
        val mean: DoubleArray,
        val components: Array<DoubleArray>,
        val thresholds: Array<DoubleArray>,
        val minimums: DoubleArray,
        val maximums: DoubleArray,
    )
}
