package com.onyxdevtools.ai.batch

import com.onyxdevtools.ai.FlexibleMatrix

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
 * High-performance interface for batch splitting with FlexibleMatrix support.
 *
 * This interface eliminates performance bottlenecks by:
 * - **FlexibleMatrix Integration**: Works directly with precision-aware matrices
 * - **No Conversions**: Avoids DoubleArray ↔ FlexibleMatrix conversions
 * - **Type Safety**: Ensures consistent precision throughout data pipeline
 *
 * Batch splitters are responsible for dividing training data into training and test subsets
 * while handling different target data formats (dense matrices, sequential arrays, sparse tokens).
 * The FlexibleMatrix approach ensures the data precision matches the neural network requirements.
 *
 * @param YIn Input target data type (e.g., Array<IntArray> for sparse tokens, Array<FlexibleMatrix> for dense).
 * @param YOut Output target data type after splitting (matches YIn for consistency).
 */
interface BatchSplitter<YIn, YOut> {
    /**
     * Splits a batch of FlexibleMatrix features and target data into training and test subsets.
     *
     * This method maintains precision consistency by working directly with FlexibleMatrix
     * throughout the splitting process, eliminating conversion overhead.
     *
     * @param x Input feature array where each element is a FlexibleMatrix representing a sample.
     * @param y Input target data in the format specific to the splitter implementation.
     * @param testFraction Fraction of data to reserve for testing (0.0 to 1.0). Default is 0.1.
     * @param shuffle Whether to shuffle the data before splitting. Default is true.
     * @return A [Quad] containing (training features, training targets, test features, test targets).
     */
    fun splitBatch(
        x: Array<FlexibleMatrix>,
        y: YIn,
        testFraction: Double = 0.1,
        shuffle: Boolean = true
    ): Quad<Array<FlexibleMatrix>, YOut, Array<FlexibleMatrix>, YOut>
}

/**
 * Legacy batch splitter interface for backward compatibility with DoubleArray-based code.
 *
 * @deprecated Use BatchSplitter with FlexibleMatrix for better performance and precision control.
 */
interface LegacyBatchSplitter<YIn, YOut> {
    /**
     * Legacy method using DoubleArray format.
     * @deprecated Use FlexibleMatrix-based splitting for better performance.
     */
    fun splitBatch(
        x: Array<DoubleArray>,
        y: YIn,
        testFraction: Double = 0.1,
        shuffle: Boolean = true
    ): Quad<Array<DoubleArray>, YOut, Array<DoubleArray>, YOut>
}

/**
 * Enumeration of available batch splitting strategies.
 *
 * @property DENSE For dense matrix targets (standard classification/regression).
 * @property SEQUENTIAL For sequential dense targets (sequence-to-sequence models).
 * @property TOKEN For sparse token targets (language modeling, tokenized sequences).
 */
enum class SplitType { DENSE, SEQUENTIAL, TOKEN }
