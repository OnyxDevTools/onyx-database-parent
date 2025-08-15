package com.onyxdevtools.ai.batch

import com.onyxdevtools.ai.Tensor

/**
 * A tuple of four values representing the result of batch splitting operations.
 *
 * This data class is used to return the four components from batch splitting:
 * training features, training targets, test features, and test targets.
 *
 * @param A Type of the first element (training features).
 * @param B Type of the second element (training targets).
 * @param C Type of the third element (test features).
 * @param D Type of the fourth element (test targets).
 * @property a First value (training features).
 * @property b Second value (training targets).
 * @property c Third value (test features).
 * @property d Fourth value (test targets).
 */
data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/**
 * Interface for batch splitting strategies used in neural network training.
 *
 * Batch splitters are responsible for dividing training data into training and test subsets
 * while handling different target data formats (dense matrices, sequential arrays, sparse tokens).
 *
 * @param YIn Input target data type (e.g., Matrix for dense, Array<IntArray> for sparse).
 * @param YOut Output target data type after splitting (e.g., Matrix for dense, List<IntArray> for sparse).
 */
interface BatchSplitter<YIn, YOut> {
    /**
     * Splits a batch of feature and target data into training and test subsets.
     *
     * @param x Input feature matrix where each row represents a sample.
     * @param y Input target data in the format specific to the splitter implementation.
     * @param testFraction Fraction of data to reserve for testing (0.0 to 1.0). Default is 0.1.
     * @param shuffle Whether to shuffle the data before splitting. Default is true.
     * @return A [Quad] containing (training features, training targets, test features, test targets).
     */
    fun splitBatch(
        x: Tensor,
        y: YIn,
        testFraction: Float = 0.1f,
        shuffle: Boolean = true
    ): Quad<Tensor, YOut, Tensor, YOut>
}

/**
 * Enumeration of available batch splitting strategies.
 *
 * @property DENSE For dense matrix targets (standard classification/regression).
 * @property SEQUENTIAL For sequential dense targets (sequence-to-sequence models).
 * @property TOKEN For sparse token targets (language modeling, tokenized sequences).
 */
enum class SplitType { DENSE, SEQUENTIAL, TOKEN }
