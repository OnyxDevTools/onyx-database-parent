package com.onyx.vector

import java.security.MessageDigest
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Immutable calibration used to turn ordinary dense embeddings into routing data.
 *
 * A calibration contains only corpus-level statistics: the normalized-sample mean,
 * orthonormal PCA components, quantile thresholds, and projection bounds. Neither the
 * input samples nor vectors passed to [encode] are retained.
 */
class VectorCalibration(
    val dimensions: Int,
    val componentCount: Int,
    val firstAxisCells: Int,
    val otherAxisCells: Int,
    val randomSeed: Long,
    mean: DoubleArray,
    components: Array<DoubleArray>,
    quantileThresholds: Array<DoubleArray>,
    projectionMinimums: DoubleArray,
    projectionMaximums: DoubleArray,
) {

    private val meanContent = mean.copyOf()
    private val componentContent = copy(components)
    private val thresholdContent = copy(quantileThresholds)
    private val minimumContent = projectionMinimums.copyOf()
    private val maximumContent = projectionMaximums.copyOf()
    private val cellCountContent = IntArray(componentCount.coerceAtLeast(0)) { axisCellCount(it) }

    /** Stable content-derived identifier suitable for persisted routing metadata. */
    val calibrationId: Long

    /** Number of real cells in the mixed-radix product space. */
    val totalBucketCount: Int

    val mean: DoubleArray
        get() = meanContent.copyOf()

    /** Orthonormal PCA axes in component-major order. */
    val components: Array<DoubleArray>
        get() = copy(componentContent)

    /** Alias that makes the role of [components] explicit to metadata codecs. */
    val pcaAxes: Array<DoubleArray>
        get() = copy(componentContent)

    /** One sorted array of `cellCount - 1` quantile boundaries per component. */
    val quantileThresholds: Array<DoubleArray>
        get() = copy(thresholdContent)

    val projectionMinimums: DoubleArray
        get() = minimumContent.copyOf()

    val projectionMaximums: DoubleArray
        get() = maximumContent.copyOf()

    val cellCounts: IntArray
        get() = cellCountContent.copyOf()

    init {
        require(dimensions > 0) { "dimensions must be greater than zero" }
        require(componentCount in 1..dimensions) {
            "componentCount must be between one and dimensions"
        }
        require(firstAxisCells >= MIN_CELLS_PER_AXIS) {
            "firstAxisCells must be at least $MIN_CELLS_PER_AXIS"
        }
        require(otherAxisCells >= MIN_CELLS_PER_AXIS) {
            "otherAxisCells must be at least $MIN_CELLS_PER_AXIS"
        }
        require(meanContent.size == dimensions && meanContent.all(Double::isFinite)) {
            "mean must contain one finite value per dimension"
        }
        require(componentContent.size == componentCount) {
            "components must contain componentCount axes"
        }
        componentContent.forEachIndexed { index, component ->
            require(component.size == dimensions && component.all(Double::isFinite)) {
                "Component $index must contain one finite value per dimension"
            }
        }
        validateOrthonormal(componentContent)

        require(thresholdContent.size == componentCount) {
            "quantileThresholds must contain one array per component"
        }
        thresholdContent.forEachIndexed { axis, thresholds ->
            require(thresholds.size == cellCountContent[axis] - 1) {
                "Component $axis requires ${cellCountContent[axis] - 1} quantile thresholds"
            }
            require(thresholds.all(Double::isFinite)) {
                "Quantile thresholds must be finite"
            }
            require(thresholds.indices.drop(1).all { thresholds[it - 1] <= thresholds[it] }) {
                "Quantile thresholds must be sorted"
            }
        }
        require(minimumContent.size == componentCount && maximumContent.size == componentCount) {
            "Projection bounds must contain one value per component"
        }
        for (axis in 0 until componentCount) {
            require(minimumContent[axis].isFinite() && maximumContent[axis].isFinite()) {
                "Projection bounds must be finite"
            }
            require(minimumContent[axis] <= maximumContent[axis]) {
                "Projection minimum must not exceed its maximum"
            }
        }

        var buckets = 1L
        for (count in cellCountContent) {
            buckets = Math.multiplyExact(buckets, count.toLong())
            require(buckets <= Int.MAX_VALUE.toLong()) {
                "Product-cell space exceeds the supported Int bucket domain"
            }
        }
        totalBucketCount = buckets.toInt()
        calibrationId = calculateCalibrationId()
    }

    /** Encodes [vector] without retaining it or its normalized dense representation. */
    fun encode(vector: FloatArray, entropy: VectorEntropy): SemanticVectorSignature {
        require(vector.size == dimensions) {
            "Vector has ${vector.size} dimensions; expected $dimensions"
        }
        return encodeNormalized(normalize(vector)).toSignature(entropy)
    }

    /** Double-precision overload for embedding providers that do not emit floats. */
    fun encode(vector: DoubleArray, entropy: VectorEntropy): SemanticVectorSignature {
        require(vector.size == dimensions) {
            "Vector has ${vector.size} dimensions; expected $dimensions"
        }
        return encodeNormalized(normalize(vector)).toSignature(entropy)
    }

    /** Packs bounded PCA coordinates into their real mixed-radix product bucket. */
    fun packCells(cells: IntArray): Int {
        validateCells(cells)
        return packCellsUnchecked(cells)
    }

    /** Restores every PCA coordinate from [bucketId]. */
    fun unpackBucket(bucketId: Int): IntArray {
        require(bucketId in 0 until totalBucketCount) {
            "bucketId must be between zero and ${totalBucketCount - 1}"
        }
        var remaining = bucketId
        val cells = IntArray(componentCount)
        for (axis in componentCount - 1 downTo 0) {
            val radix = cellCountContent[axis]
            cells[axis] = remaining % radix
            remaining /= radix
        }
        return cells
    }

    /**
     * Returns the origin and then bounded neighboring cells up to [radius] Manhattan
     * steps away. At most [maxProbes] unique cells are returned.
     */
    @JvmOverloads
    fun nearbyProductCells(
        cells: IntArray,
        radius: Int = 1,
        maxProbes: Int = DEFAULT_MAX_PROBES,
    ): Array<IntArray> {
        validateCells(cells)
        require(radius >= 0) { "radius must be non-negative" }
        require(maxProbes > 0) { "maxProbes must be greater than zero" }

        val origin = cells.copyOf()
        val queue = ArrayDeque<CellProbe>()
        val visited = HashSet<Int>()
        val results = ArrayList<IntArray>(min(maxProbes, totalBucketCount))
        queue.addLast(CellProbe(origin, 0))
        visited.add(packCellsUnchecked(origin))

        while (queue.isNotEmpty() && results.size < maxProbes) {
            val probe = queue.removeFirst()
            results.add(probe.cells.copyOf())
            if (probe.distance == radius) continue

            for (axis in 0 until componentCount) {
                for (direction in DIRECTIONS) {
                    val nextValue = probe.cells[axis] + direction
                    if (nextValue !in 0 until cellCountContent[axis]) continue
                    val neighbor = probe.cells.copyOf()
                    neighbor[axis] = nextValue
                    val bucket = packCellsUnchecked(neighbor)
                    if (visited.add(bucket)) {
                        queue.addLast(CellProbe(neighbor, probe.distance + 1))
                    }
                }
            }
        }
        return results.toTypedArray()
    }

    @JvmOverloads
    fun nearbyBuckets(
        bucketId: Int,
        radius: Int = 1,
        maxProbes: Int = DEFAULT_MAX_PROBES,
    ): IntArray = nearbyProductCells(unpackBucket(bucketId), radius, maxProbes)
        .mapToIntArray(::packCellsUnchecked)

    @JvmOverloads
    fun nearbyBuckets(
        cells: IntArray,
        radius: Int = 1,
        maxProbes: Int = DEFAULT_MAX_PROBES,
    ): IntArray = nearbyProductCells(cells, radius, maxProbes)
        .mapToIntArray(::packCellsUnchecked)

    /** Normalized product-cell similarity using this calibration's axis bounds. */
    fun normalizedBucketSimilarity(
        first: SemanticVectorSignature,
        second: SemanticVectorSignature,
    ): Double {
        require(first.calibrationId == calibrationId && second.calibrationId == calibrationId) {
            "Both signatures must belong to this calibration"
        }
        require(first.cellCounts.contentEquals(cellCountContent) && second.cellCounts.contentEquals(cellCountContent)) {
            "Both signatures must use this calibration's product-cell cardinalities"
        }
        return first.normalizedBucketSimilarity(second)
    }

    /** Normalized similarity for two packed product bucket identifiers. */
    fun normalizedBucketSimilarity(firstBucketId: Int, secondBucketId: Int): Double {
        val firstCells = unpackBucket(firstBucketId)
        val secondCells = unpackBucket(secondBucketId)
        var normalizedDistance = 0.0
        for (axis in 0 until componentCount) {
            normalizedDistance += abs(firstCells[axis] - secondCells[axis]).toDouble() /
                (cellCountContent[axis] - 1).toDouble()
        }
        return (1.0 - normalizedDistance / componentCount.toDouble()).coerceIn(0.0, 1.0)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is VectorCalibration &&
            dimensions == other.dimensions &&
            componentCount == other.componentCount &&
            firstAxisCells == other.firstAxisCells &&
            otherAxisCells == other.otherAxisCells &&
            randomSeed == other.randomSeed &&
            meanContent.contentEquals(other.meanContent) &&
            contentDeepEquals(componentContent, other.componentContent) &&
            contentDeepEquals(thresholdContent, other.thresholdContent) &&
            minimumContent.contentEquals(other.minimumContent) &&
            maximumContent.contentEquals(other.maximumContent)

    override fun hashCode(): Int {
        var result = dimensions
        result = 31 * result + componentCount
        result = 31 * result + firstAxisCells
        result = 31 * result + otherAxisCells
        result = 31 * result + randomSeed.hashCode()
        result = 31 * result + meanContent.contentHashCode()
        componentContent.forEach { result = 31 * result + it.contentHashCode() }
        thresholdContent.forEach { result = 31 * result + it.contentHashCode() }
        result = 31 * result + minimumContent.contentHashCode()
        result = 31 * result + maximumContent.contentHashCode()
        return result
    }

    override fun toString(): String =
        "VectorCalibration(calibrationId=$calibrationId, dimensions=$dimensions, " +
            "componentCount=$componentCount, totalBucketCount=$totalBucketCount, " +
            "randomSeed=$randomSeed)"

    private fun encodeNormalized(normalized: DoubleArray): ProjectedVector {
        val centered = DoubleArray(dimensions) { normalized[it] - meanContent[it] }
        val projections = DoubleArray(componentCount)
        val cells = IntArray(componentCount)
        for (axis in 0 until componentCount) {
            projections[axis] = dot(centered, componentContent[axis])
            cells[axis] = quantize(projections[axis], thresholdContent[axis])
        }
        return ProjectedVector(normalized, projections, cells)
    }

    private fun ProjectedVector.toSignature(entropy: VectorEntropy): SemanticVectorSignature {
        require(entropy.bitCount in MIN_SIMHASH_BITS..MAX_SIMHASH_BITS &&
            entropy.bitCount % Long.SIZE_BITS == 0) {
            "Semantic SimHash entropy must resolve to 64, 128, 192, or 256 bits"
        }
        val fingerprint = simHash(normalized, entropy.bitCount)
        return SemanticVectorSignature(
            calibrationId = calibrationId,
            bucketId = packCellsUnchecked(cells),
            cells = cells,
            cellCounts = cellCountContent,
            fingerprint = fingerprint,
            bands = SemanticVectorSignature.splitIntoFourBands(fingerprint),
            boundaryConfidence = boundaryConfidence(projections, cells),
        )
    }

    private fun simHash(normalized: DoubleArray, bitCount: Int): LongArray {
        val words = LongArray(bitCount / Long.SIZE_BITS)
        for (bit in 0 until bitCount) {
            val bitSeed = randomSeed xor SIMHASH_SEED_DOMAIN xor
                (bit.toLong() * SIMHASH_BIT_STRIDE) xor dimensions.toLong()
            val random = DeterministicRandom(bitSeed)
            var projection = 0.0
            for (dimension in 0 until dimensions) {
                projection += normalized[dimension] * random.nextSignedDouble()
            }
            if (projection >= 0.0) {
                words[bit / Long.SIZE_BITS] = words[bit / Long.SIZE_BITS] or
                    (1L shl (bit % Long.SIZE_BITS))
            }
        }
        return words
    }

    private fun boundaryConfidence(projections: DoubleArray, cells: IntArray): Float {
        var confidence = 1.0
        for (axis in 0 until componentCount) {
            val thresholds = thresholdContent[axis]
            val cell = cells[axis]
            val value = projections[axis]
            val globalSpan = maximumContent[axis] - minimumContent[axis]
            val axisConfidence = when (cell) {
                0 -> {
                    val boundary = thresholds.first()
                    val span = positiveSpan(boundary - minimumContent[axis], globalSpan)
                    ((boundary - value) / span).coerceIn(0.0, 1.0)
                }
                cellCountContent[axis] - 1 -> {
                    val boundary = thresholds.last()
                    val span = positiveSpan(maximumContent[axis] - boundary, globalSpan)
                    ((value - boundary) / span).coerceIn(0.0, 1.0)
                }
                else -> {
                    val lower = thresholds[cell - 1]
                    val upper = thresholds[cell]
                    val span = positiveSpan(upper - lower, globalSpan)
                    (2.0 * min(value - lower, upper - value) / span).coerceIn(0.0, 1.0)
                }
            }
            confidence = min(confidence, axisConfidence)
        }
        return confidence.toFloat()
    }

    private fun positiveSpan(localSpan: Double, globalSpan: Double): Double = when {
        localSpan > NUMERICAL_EPSILON -> localSpan
        globalSpan > NUMERICAL_EPSILON -> globalSpan
        else -> 1.0
    }

    private fun validateCells(cells: IntArray) {
        require(cells.size == componentCount) {
            "cells must contain one coordinate per component"
        }
        for (axis in cells.indices) {
            require(cells[axis] in 0 until cellCountContent[axis]) {
                "Cell ${cells[axis]} is outside axis $axis bounds 0..${cellCountContent[axis] - 1}"
            }
        }
    }

    private fun packCellsUnchecked(cells: IntArray): Int {
        var bucket = 0
        for (axis in cells.indices) {
            bucket = bucket * cellCountContent[axis] + cells[axis]
        }
        return bucket
    }

    private fun axisCellCount(axis: Int): Int = if (axis == 0) firstAxisCells else otherAxisCells

    private fun calculateCalibrationId(): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(CALIBRATION_ID_DOMAIN)
        updateInt(digest, CALIBRATION_FORMAT_VERSION)
        updateInt(digest, dimensions)
        updateInt(digest, componentCount)
        updateInt(digest, firstAxisCells)
        updateInt(digest, otherAxisCells)
        updateLong(digest, randomSeed)
        meanContent.forEach { updateLong(digest, it.toBits()) }
        componentContent.forEach { axis -> axis.forEach { updateLong(digest, it.toBits()) } }
        thresholdContent.forEach { axis -> axis.forEach { updateLong(digest, it.toBits()) } }
        minimumContent.forEach { updateLong(digest, it.toBits()) }
        maximumContent.forEach { updateLong(digest, it.toBits()) }
        val bytes = digest.digest()
        var id = 0L
        for (index in 0 until Long.SIZE_BYTES) {
            id = (id shl 8) or (bytes[index].toLong() and 0xffL)
        }
        return if (id == SemanticVectorSignature.NO_CALIBRATION) 1L else id
    }

    private data class ProjectedVector(
        val normalized: DoubleArray,
        val projections: DoubleArray,
        val cells: IntArray,
    )

    private data class CellProbe(val cells: IntArray, val distance: Int)

    companion object {
        const val DEFAULT_COMPONENT_COUNT: Int = 6
        const val DEFAULT_FIRST_AXIS_CELLS: Int = 5
        const val DEFAULT_OTHER_AXIS_CELLS: Int = 10
        const val DEFAULT_RANDOM_SEED: Long = 0x4f4e59585f53454dL
        const val DEFAULT_MAX_PROBES: Int = 256

        private const val MIN_CELLS_PER_AXIS: Int = 2
        private const val MIN_SIMHASH_BITS: Int = 64
        private const val MAX_SIMHASH_BITS: Int = 256
        private const val CALIBRATION_FORMAT_VERSION: Int = 1
        private const val POWER_ITERATIONS: Int = 512
        private const val POWER_RESTARTS: Int = 4
        private const val NUMERICAL_EPSILON: Double = 1e-12
        private const val CONVERGENCE_EPSILON: Double = 1e-13
        private const val PCA_SEED_DOMAIN: Long = 0x6a09e667f3bcc909L
        private const val PCA_AXIS_STRIDE: Long = 0x3c6ef372fe94f82bL
        private const val SIMHASH_SEED_DOMAIN: Long = 0x510e527fade682d1L
        private const val SIMHASH_BIT_STRIDE: Long = 0x1f83d9abfb41bd6bL
        private const val SPLIT_MIX_INCREMENT: Long = -7046029254386353131L
        private const val SPLIT_MIX_MULTIPLIER_1: Long = -4658895280553007687L
        private const val SPLIT_MIX_MULTIPLIER_2: Long = -7723592293110705685L
        private val DIRECTIONS = intArrayOf(-1, 1)
        private val CALIBRATION_ID_DOMAIN =
            "com.onyx.vector.semantic-calibration".toByteArray(Charsets.UTF_8)

        /**
         * Fits deterministic PCA and quantile routing metadata from dense embeddings.
         * Every sample is copied, checked, and L2-normalized before calibration.
         */
        @JvmStatic
        @JvmOverloads
        fun fit(
            samples: List<FloatArray>,
            componentCount: Int = DEFAULT_COMPONENT_COUNT,
            firstAxisCells: Int = DEFAULT_FIRST_AXIS_CELLS,
            otherAxisCells: Int = DEFAULT_OTHER_AXIS_CELLS,
            randomSeed: Long = DEFAULT_RANDOM_SEED,
        ): VectorCalibration {
            require(samples.isNotEmpty()) { "At least one calibration sample is required" }
            require(componentCount > 0) { "componentCount must be greater than zero" }
            require(firstAxisCells >= MIN_CELLS_PER_AXIS && otherAxisCells >= MIN_CELLS_PER_AXIS) {
                "Every PCA axis must contain at least $MIN_CELLS_PER_AXIS cells"
            }
            val dimensions = samples.first().size
            require(dimensions > 0) { "Calibration samples must not be empty" }
            require(componentCount <= dimensions) {
                "componentCount must not exceed sample dimensions"
            }
            val requiredSamples = max(componentCount + 1, max(firstAxisCells, otherAxisCells))
            require(samples.size >= requiredSamples) {
                "At least $requiredSamples samples are required for $componentCount components and the configured quantiles"
            }

            val normalized = samples.mapIndexed { index, sample ->
                require(sample.size == dimensions) {
                    "Calibration sample $index has ${sample.size} dimensions; expected $dimensions"
                }
                normalize(sample, "Calibration sample $index")
            }.toMutableList()
            normalized.sortWith { left, right -> compareLexicographically(left, right) }

            val mean = DoubleArray(dimensions)
            normalized.forEach { sample ->
                for (dimension in 0 until dimensions) mean[dimension] += sample[dimension]
            }
            for (dimension in 0 until dimensions) mean[dimension] /= normalized.size.toDouble()

            val centered = Array(normalized.size) { sampleIndex ->
                DoubleArray(dimensions) { dimension ->
                    normalized[sampleIndex][dimension] - mean[dimension]
                }
            }
            val components = fitComponents(centered, componentCount, randomSeed)
            val thresholds = Array(componentCount) { DoubleArray(0) }
            val minimums = DoubleArray(componentCount)
            val maximums = DoubleArray(componentCount)
            for (axis in 0 until componentCount) {
                val projections = DoubleArray(centered.size) { dot(centered[it], components[axis]) }
                projections.sort()
                minimums[axis] = projections.first()
                maximums[axis] = projections.last()
                val cellCount = if (axis == 0) firstAxisCells else otherAxisCells
                thresholds[axis] = DoubleArray(cellCount - 1) { boundary ->
                    quantile(projections, (boundary + 1).toDouble() / cellCount.toDouble())
                }
            }

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

        private fun fitComponents(
            samples: Array<DoubleArray>,
            componentCount: Int,
            randomSeed: Long,
        ): Array<DoubleArray> {
            val dimensions = samples.first().size
            val axes = ArrayList<DoubleArray>(componentCount)
            var totalVariance = 0.0
            samples.forEach { row -> row.forEach { totalVariance += it * it } }
            require(totalVariance > NUMERICAL_EPSILON) {
                "Calibration samples contain no variance after normalization"
            }
            val minimumEigenvalue = max(NUMERICAL_EPSILON * NUMERICAL_EPSILON, totalVariance * 1e-14)

            for (axisIndex in 0 until componentCount) {
                var selected: DoubleArray? = null
                for (attempt in 0 until POWER_RESTARTS + dimensions) {
                    val candidate = if (attempt < POWER_RESTARTS) {
                        val seed = randomSeed xor PCA_SEED_DOMAIN xor
                            (axisIndex.toLong() * PCA_AXIS_STRIDE) xor attempt.toLong()
                        val random = DeterministicRandom(seed)
                        DoubleArray(dimensions) { random.nextSignedDouble() }
                    } else {
                        DoubleArray(dimensions).also { it[(attempt - POWER_RESTARTS) % dimensions] = 1.0 }
                    }
                    orthogonalize(candidate, axes)
                    if (!normalizeInPlace(candidate)) continue
                    val result = powerIteration(samples, candidate, axes) ?: continue
                    val covarianceProjection = covarianceTimes(samples, result)
                    val eigenvalue = dot(result, covarianceProjection)
                    if (eigenvalue > minimumEigenvalue) {
                        canonicalizeSign(result)
                        selected = result
                        break
                    }
                }
                requireNotNull(selected) {
                    "Calibration samples do not contain $componentCount independent PCA components"
                }
                axes.add(selected)
            }
            return axes.toTypedArray()
        }

        private fun powerIteration(
            samples: Array<DoubleArray>,
            initial: DoubleArray,
            previousAxes: List<DoubleArray>,
        ): DoubleArray? {
            var current = initial
            repeat(POWER_ITERATIONS) {
                val next = covarianceTimes(samples, current)
                orthogonalize(next, previousAxes)
                if (!normalizeInPlace(next)) return null
                var alignment = dot(current, next)
                if (alignment < 0.0) {
                    for (index in next.indices) next[index] = -next[index]
                    alignment = -alignment
                }
                current = next
                if (1.0 - alignment <= CONVERGENCE_EPSILON) return current
            }
            return current
        }

        private fun covarianceTimes(samples: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
            val result = DoubleArray(vector.size)
            for (sample in samples) {
                val projection = dot(sample, vector)
                for (dimension in result.indices) {
                    result[dimension] += sample[dimension] * projection
                }
            }
            return result
        }

        private fun orthogonalize(vector: DoubleArray, axes: List<DoubleArray>) {
            // A second pass keeps later components orthogonal when eigenvalues are close.
            repeat(2) {
                for (axis in axes) {
                    val projection = dot(vector, axis)
                    for (dimension in vector.indices) {
                        vector[dimension] -= projection * axis[dimension]
                    }
                }
            }
        }

        private fun normalizeInPlace(vector: DoubleArray): Boolean {
            var squaredNorm = 0.0
            vector.forEach { squaredNorm += it * it }
            if (!squaredNorm.isFinite() || squaredNorm <= NUMERICAL_EPSILON * NUMERICAL_EPSILON) {
                return false
            }
            val inverseNorm = 1.0 / sqrt(squaredNorm)
            for (index in vector.indices) vector[index] *= inverseNorm
            return true
        }

        private fun canonicalizeSign(vector: DoubleArray) {
            var pivot = 0
            for (index in 1 until vector.size) {
                if (abs(vector[index]) > abs(vector[pivot])) pivot = index
            }
            if (vector[pivot] < 0.0) {
                for (index in vector.indices) vector[index] = -vector[index]
            }
        }

        private fun quantile(sorted: DoubleArray, probability: Double): Double {
            val position = probability * (sorted.size - 1).toDouble()
            val lower = floor(position).toInt()
            val upper = min(lower + 1, sorted.lastIndex)
            val fraction = position - lower.toDouble()
            return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction
        }

        private fun quantize(value: Double, thresholds: DoubleArray): Int {
            var low = 0
            var high = thresholds.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (value > thresholds[middle]) low = middle + 1 else high = middle
            }
            return low
        }

        private fun normalize(vector: FloatArray, description: String = "Vector"): DoubleArray {
            val converted = DoubleArray(vector.size)
            var squaredNorm = 0.0
            for (index in vector.indices) {
                val value = vector[index].toDouble()
                require(value.isFinite()) { "$description must contain only finite values" }
                converted[index] = value
                squaredNorm += value * value
            }
            require(squaredNorm.isFinite() && squaredNorm > 0.0) {
                "$description must have a finite, non-zero L2 norm"
            }
            val inverseNorm = 1.0 / sqrt(squaredNorm)
            for (index in converted.indices) converted[index] *= inverseNorm
            return converted
        }

        private fun normalize(vector: DoubleArray, description: String = "Vector"): DoubleArray {
            val copy = vector.copyOf()
            var squaredNorm = 0.0
            for (value in copy) {
                require(value.isFinite()) { "$description must contain only finite values" }
                squaredNorm += value * value
            }
            require(squaredNorm.isFinite() && squaredNorm > 0.0) {
                "$description must have a finite, non-zero L2 norm"
            }
            val inverseNorm = 1.0 / sqrt(squaredNorm)
            for (index in copy.indices) copy[index] *= inverseNorm
            return copy
        }

        private fun compareLexicographically(left: DoubleArray, right: DoubleArray): Int {
            for (index in left.indices) {
                val comparison = java.lang.Double.compare(left[index], right[index])
                if (comparison != 0) return comparison
            }
            return 0
        }

        private fun dot(left: DoubleArray, right: DoubleArray): Double {
            var result = 0.0
            for (index in left.indices) result += left[index] * right[index]
            return result
        }

        private fun validateOrthonormal(components: Array<DoubleArray>) {
            components.forEachIndexed { leftIndex, left ->
                components.forEachIndexed { rightIndex, right ->
                    if (rightIndex > leftIndex) return@forEachIndexed
                    val expected = if (leftIndex == rightIndex) 1.0 else 0.0
                    require(abs(dot(left, right) - expected) <= ORTHONORMAL_TOLERANCE) {
                        "PCA components must be orthonormal"
                    }
                }
            }
        }

        private const val ORTHONORMAL_TOLERANCE: Double = 1e-7

        private fun copy(values: Array<DoubleArray>): Array<DoubleArray> =
            Array(values.size) { values[it].copyOf() }

        private fun contentDeepEquals(left: Array<DoubleArray>, right: Array<DoubleArray>): Boolean =
            left.size == right.size && left.indices.all { left[it].contentEquals(right[it]) }

        private fun Array<IntArray>.mapToIntArray(transform: (IntArray) -> Int): IntArray =
            IntArray(size) { transform(this[it]) }

        private fun updateInt(digest: MessageDigest, value: Int) {
            digest.update((value ushr 24).toByte())
            digest.update((value ushr 16).toByte())
            digest.update((value ushr 8).toByte())
            digest.update(value.toByte())
        }

        private fun updateLong(digest: MessageDigest, value: Long) {
            digest.update((value ushr 56).toByte())
            digest.update((value ushr 48).toByte())
            digest.update((value ushr 40).toByte())
            digest.update((value ushr 32).toByte())
            digest.update((value ushr 24).toByte())
            digest.update((value ushr 16).toByte())
            digest.update((value ushr 8).toByte())
            digest.update(value.toByte())
        }

        private class DeterministicRandom(seed: Long) {
            private var state = seed

            fun nextSignedDouble(): Double {
                val unit = (nextLong().ushr(11).toDouble()) / (1L shl 53).toDouble()
                return unit * 2.0 - 1.0
            }

            private fun nextLong(): Long {
                state += SPLIT_MIX_INCREMENT
                var value = state
                value = (value xor (value ushr 30)) * SPLIT_MIX_MULTIPLIER_1
                value = (value xor (value ushr 27)) * SPLIT_MIX_MULTIPLIER_2
                return value xor (value ushr 31)
            }
        }
    }
}
