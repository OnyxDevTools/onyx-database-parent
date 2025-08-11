package com.onyxdevtools.ai.batch

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
class SequentialBatchSplitter : BatchSplitter<Array<Array<FloatArray>>, List<Array<FloatArray>>> {
    
    /**
     * Splits feature vectors and sequential target data into training and test subsets.
     *
     * The method randomly shuffles sample indices (if enabled) and splits them according
     * to the specified test fraction. Each input feature vector corresponds to a sequence
     * of target vectors, and the splitting maintains this correspondence.
     *
     * @param x Input feature matrix where each row represents a single sample vector.
     * @param y Sequential target data where each element is an array of target vectors for the corresponding sample.
     * @param testFraction Fraction of data to reserve for testing (0.0 to 1.0). Default is 0.1.
     * @param shuffle Whether to randomly shuffle samples before splitting. Default is true.
     * @return A [Quad] containing (training features, training target sequences, test features, test target sequences).
     * @throws IllegalArgumentException if testFraction is not in [0.0, 1.0] or if x and y have different numbers of samples.
     */
    override fun splitBatch(
        x: Array<FloatArray>,
        y: Array<Array<FloatArray>>,
        testFraction: Float,
        shuffle: Boolean
    ): Quad<Array<FloatArray>, List<Array<FloatArray>>, Array<FloatArray>, List<Array<FloatArray>>> {

        val idx = x.indices.toMutableList().apply { if (shuffle) shuffle() }
        val testSize = (idx.size * testFraction).toInt().coerceAtLeast(1)
        val testIdx = idx.take(testSize)
        val trainIdx = idx.drop(testSize)

        fun subsetInputs(src: Array<FloatArray>, ids: List<Int>) =
            Array(ids.size) { i -> src[ids[i]] }
        fun subsetTargets(src: Array<Array<FloatArray>>, ids: List<Int>) =
            ids.map { src[it] }

        return Quad(
            subsetInputs(x, trainIdx), subsetTargets(y, trainIdx),
            subsetInputs(x, testIdx),  subsetTargets(y, testIdx)
        )
    }
}
