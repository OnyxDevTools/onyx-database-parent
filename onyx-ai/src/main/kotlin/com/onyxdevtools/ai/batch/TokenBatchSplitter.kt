package com.onyxdevtools.ai.batch

import com.onyxdevtools.ai.Tensor

/**
 * Batch splitter implementation for sparse token targets.
 *
 * This splitter handles language modeling and tokenized sequence learning scenarios where
 * input features are dense vectors but targets are sparse token sequences represented as
 * integer arrays. It's optimized for memory efficiency when dealing with large vocabularies,
 * avoiding the need to create dense one-hot encoded target vectors.
 *
 * Memory usage: O(n * s) where n = number of samples, s = average sequence length.
 * This is dramatically more efficient than dense representations for large vocabularies.
 *
 * Example usage:
 * ```kotlin
 * val splitter = TokenBatchSplitter()
 * val (xTrain, yTrain, xTest, yTest) = splitter.splitBatch(features, tokenSequences, testFraction = 0.2)
 * ```
 */
class TokenBatchSplitter : BatchSplitter<Array<IntArray>, List<IntArray>> {
    
    /**
     * Splits feature vectors and sparse token sequence data into training and test subsets.
     *
     * The method randomly shuffles sample indices (if enabled) and splits them according
     * to the specified test fraction. Special handling is provided for very small datasets
     * to ensure at least one training sample remains.
     *
     * @param x Input feature matrix where each row represents a sample vector.
     * @param y Sparse token sequence data where each element is an array of token IDs for the corresponding sample.
     * @param testFraction Fraction of data to reserve for testing (0.0 to 1.0). Default is 0.1.
     * @param shuffle Whether to randomly shuffle samples before splitting. Default is true.
     * @return A [Quad] containing (training features, training token sequences, test features, test token sequences).
     * @throws IllegalArgumentException if testFraction is not in [0.0, 1.0] or if x and y have different numbers of samples.
     */
    override fun splitBatch(
        x: Tensor,
        y: Array<IntArray>,
        testFraction: Float,
        shuffle: Boolean
    ): Quad<Tensor, List<IntArray>, Tensor, List<IntArray>> {
        require(testFraction in 0.0..1.0) { "Test fraction must be between 0.0 and 1.0" }
        require(x.size == y.size) { "Features and targets must have the same number of samples" }
        
        // Handle very small datasets by ensuring at least one training sample (all go to train, none to test)
        if (x.size <= 2) {
            return Quad(
                x,
                y.toList(),
                Tensor(0, x.columnSize),
                emptyList()
            )
        }

        val idx = x.indices.toMutableList().apply { if (shuffle) shuffle() }
        val requestedTest = (idx.size * testFraction).toInt().coerceAtLeast(1)
        val testSize = minOf(requestedTest, idx.size - 1) // ensure >=1 train sample

        val testIdx = idx.take(testSize)
        val trainIdx = idx.drop(testSize)

        fun subsetInputs(src: Tensor, ids: List<Int>): Tensor {
            val cols = src.columnSize
            return Tensor(ids.size, cols) { r, c -> src[ids[r]][c] }
        }
        fun subsetSparse(src: Array<IntArray>, ids: List<Int>) =
            ids.map { src[it] }

        return Quad(
            subsetInputs(x, trainIdx), subsetSparse(y, trainIdx),
            subsetInputs(x, testIdx),  subsetSparse(y, testIdx)
        )
    }
}
