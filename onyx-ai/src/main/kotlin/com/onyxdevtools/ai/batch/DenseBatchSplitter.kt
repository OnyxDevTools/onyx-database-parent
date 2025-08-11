package com.onyxdevtools.ai.batch

import com.onyxdevtools.ai.extensions.Matrix

/**
 * Batch splitter implementation for dense matrix targets.
 *
 * This splitter handles standard supervised learning scenarios where both input features
 * and target labels are represented as dense matrices. It's suitable for classification
 * and regression tasks with one-hot encoded labels or continuous target values.
 *
 * Memory usage: O(n * m) where n = number of samples, m = number of features/targets.
 *
 * Example usage:
 * ```kotlin
 * val splitter = DenseBatchSplitter()
 * val (xTrain, yTrain, xTest, yTest) = splitter.splitBatch(features, targets, testFraction = 0.2)
 * ```
 */
class DenseBatchSplitter : LegacyBatchSplitter<Matrix, Matrix> {
    
    /**
     * Splits dense feature and target matrices into training and test subsets.
     *
     * The method randomly shuffles sample indices (if enabled) and splits them according
     * to the specified test fraction. Both features and targets are split consistently
     * to maintain the correspondence between input-output pairs.
     *
     * @param x Input feature matrix where each row represents a sample.
     * @param y Target matrix where each row corresponds to the target for the same sample in x.
     * @param testFraction Fraction of data to reserve for testing (0.0 to 1.0). Default is 0.1.
     * @param shuffle Whether to randomly shuffle samples before splitting. Default is true.
     * @return A [Quad] containing (training features, training targets, test features, test targets).
     * @throws IllegalArgumentException if testFraction is not in [0.0, 1.0] or if x and y have different numbers of samples.
     */
    override fun splitBatch(
        x: Array<DoubleArray>,
        y: Matrix,
        testFraction: Double,
        shuffle: Boolean
    ): Quad<Array<DoubleArray>, Matrix, Array<DoubleArray>, Matrix> {
        require(testFraction in 0.0..1.0) { "Test fraction must be between 0.0 and 1.0" }
        require(x.size == y.size) { "Features and targets must have the same number of samples" }
        
        val idx = x.indices.toMutableList().apply { if (shuffle) shuffle() }
        val testSize = (idx.size * testFraction).toInt().coerceAtLeast(1)
        val testIdx = idx.take(testSize)
        val trainIdx = idx.drop(testSize)

        fun subset(src: Matrix, ids: List<Int>) = Array(ids.size) { i -> src[ids[i]] }

        return Quad(
            subset(x, trainIdx), subset(y, trainIdx),
            subset(x, testIdx),  subset(y, testIdx)
        )
    }
}
