package com.onyxdevtools.ai.batch

import com.onyxdevtools.ai.FlexibleMatrix

/**
 * High-performance batch splitter for FlexibleMatrix features and sparse integer targets.
 *
 * This splitter is optimized for sequence-to-sequence training scenarios where:
 * - Features are represented as FlexibleMatrix (precision-consistent)
 * - Targets are sparse integer arrays (token IDs)
 * - Memory efficiency is critical for large vocabulary models
 *
 * Performance benefits:
 * - **No Precision Conversions**: Works directly with FlexibleMatrix
 * - **Memory Efficient**: Sparse targets reduce memory usage by ~1000x vs one-hot
 * - **Batch Consistency**: Maintains precision throughout the training pipeline
 *
 * Example usage:
 * ```kotlin
 * val splitter = TokenBatchSplitter()
 * val (xTrain, yTrain, xTest, yTest) = splitter.splitBatch(features, targets, testFraction = 0.2)
 * ```
 */
class TokenBatchSplitter : BatchSplitter<Array<IntArray>, Array<IntArray>> {
    
    /**
     * Splits FlexibleMatrix features and sparse target arrays into training and test subsets.
     *
     * The method randomly shuffles sample indices (if enabled) and splits them according
     * to the specified test fraction. Both features and targets are split consistently
     * to maintain the correspondence between input-output pairs.
     *
     * This implementation is optimized for:
     * - Language model training with sparse categorical targets
     * - Large vocabulary scenarios where dense targets would be memory-prohibitive
     * - Precision-consistent data pipelines
     *
     * @param x Input feature array where each element is a FlexibleMatrix representing a sample.
     * @param y Target array where each element is an IntArray of token IDs for the corresponding sample.
     * @param testFraction Fraction of data to reserve for testing (0.0 to 1.0). Default is 0.1.
     * @param shuffle Whether to randomly shuffle samples before splitting. Default is true.
     * @return A [Quad] containing (training features, training targets, test features, test targets).
     * @throws IllegalArgumentException if testFraction is not in [0.0, 1.0] or if x and y have different numbers of samples.
     */
    override fun splitBatch(
        x: Array<FlexibleMatrix>,
        y: Array<IntArray>,
        testFraction: Double,
        shuffle: Boolean
    ): Quad<Array<FlexibleMatrix>, Array<IntArray>, Array<FlexibleMatrix>, Array<IntArray>> {
        require(testFraction in 0.0..1.0) { "Test fraction must be between 0.0 and 1.0" }
        require(x.size == y.size) { "Features and targets must have the same number of samples" }
        
        val idx = x.indices.toMutableList().apply { if (shuffle) shuffle() }
        val testSize = (idx.size * testFraction).toInt().coerceAtLeast(1)
        val testIdx = idx.take(testSize)
        val trainIdx = idx.drop(testSize)

        fun subsetX(src: Array<FlexibleMatrix>, ids: List<Int>) = Array(ids.size) { i -> src[ids[i]] }
        fun subsetY(src: Array<IntArray>, ids: List<Int>) = Array(ids.size) { i -> src[ids[i]] }

        return Quad(
            subsetX(x, trainIdx), subsetY(y, trainIdx),
            subsetX(x, testIdx),  subsetY(y, testIdx)
        )
    }
}
