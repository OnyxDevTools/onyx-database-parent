package com.onyxdevtools.ai.batch

import com.onyxdevtools.ai.FlexibleMatrix

/**
 * Batch splitter implementation for sequential dense targets.
 *
 * This splitter handles sequence-to-sequence learning scenarios where input features
 * are single vectors per sample but targets are sequences of dense vectors. It's suitable
 * for tasks like machine translation, text generation, or time series prediction where
 * each input sample corresponds to a variable-length sequence of output vectors.
 *
 * Memory usage: O(n * s * m) where n = number of samples, s = average sequence length, 
 * m = number of features per sequence element.
 *
 * Example usage:
 * ```kotlin
 * val splitter = SequentialBatchSplitter()
 * val (xTrain, yTrain, xTest, yTest) = splitter.splitBatch(features, sequenceTargets, testFraction = 0.2)
 * ```
 */
class SequentialBatchSplitter : BatchSplitter<Array<Array<FlexibleMatrix>>, Array<Array<FlexibleMatrix>>> {
    
    /**
     * Splits feature vectors and sequential target data into training and test subsets.
     *
     * The method randomly shuffles sample indices (if enabled) and splits them according
     * to the specified test fraction. Each input FlexibleMatrix corresponds to a sequence
     * of target FlexibleMatrix objects, and the splitting maintains this correspondence.
     *
     * @param x Input feature array where each element is a FlexibleMatrix representing a sample.
     * @param y Sequential target data where each element is an array of FlexibleMatrix for the corresponding sample.
     * @param testFraction Fraction of data to reserve for testing (0.0 to 1.0). Default is 0.1.
     * @param shuffle Whether to randomly shuffle samples before splitting. Default is true.
     * @return A [Quad] containing (training features, training target sequences, test features, test target sequences).
     * @throws IllegalArgumentException if testFraction is not in [0.0, 1.0] or if x and y have different numbers of samples.
     */
    override fun splitBatch(
        x: Array<FlexibleMatrix>,
        y: Array<Array<FlexibleMatrix>>,
        testFraction: Double,
        shuffle: Boolean
    ): Quad<Array<FlexibleMatrix>, Array<Array<FlexibleMatrix>>, Array<FlexibleMatrix>, Array<Array<FlexibleMatrix>>> {
        require(testFraction in 0.0..1.0) { "Test fraction must be between 0.0 and 1.0" }
        require(x.size == y.size) { "Features and targets must have the same number of samples" }

        val idx = x.indices.toMutableList().apply { if (shuffle) shuffle() }
        val testSize = (idx.size * testFraction).toInt().coerceAtLeast(1)
        val testIdx = idx.take(testSize)
        val trainIdx = idx.drop(testSize)

        fun subsetInputs(src: Array<FlexibleMatrix>, ids: List<Int>) =
            Array(ids.size) { i -> src[ids[i]] }
        fun subsetTargets(src: Array<Array<FlexibleMatrix>>, ids: List<Int>) =
            ids.map { src[it] }.toTypedArray()

        return Quad(
            subsetInputs(x, trainIdx), subsetTargets(y, trainIdx),
            subsetInputs(x, testIdx),  subsetTargets(y, testIdx)
        )
    }
}
