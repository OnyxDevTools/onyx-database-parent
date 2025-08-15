package com.onyxdevtools.ai.batch

import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import com.onyxdevtools.ai.subset

/**
 * Batch splitter implementation for sparse token targets.
 */
class TokenBatchSplitter :
    BatchSplitter<Array<IntArray>, List<IntArray>> {

    override fun splitBatch(
        x: Tensor,
        y: Array<IntArray>,
        testFraction: Float,
        shuffle: Boolean
    ): Quad<Tensor, List<IntArray>, Tensor, List<IntArray>> {

        require(testFraction in 0.0..1.0) { "Test fraction must be between 0.0 and 1.0" }
        require(x.rows == y.size) {
            "Features and targets must have the same number of samples: X.rows=${x.rows}, Y.size=${y.size}"
        }

        // Ensure at least one training sample remains for very small datasets
        if (x.rows <= 2) {
            return Quad(
                x,
                y.toList(),
                createTensor(0, x.cols),
                emptyList()
            )
        }

        val idx = x.indices.toMutableList().apply { if (shuffle) shuffle() }
        val requestedTest = (idx.size * testFraction).toInt().coerceAtLeast(1)
        val testSize = minOf(requestedTest, idx.size - 1) // keep >= 1 train row

        val testIdx = idx.take(testSize)
        val trainIdx = idx.drop(testSize)

        val xTrain = x.subset(trainIdx)
        val xTest  = x.subset(testIdx)
        val yTrain: List<IntArray> = trainIdx.map { y[it] }
        val yTest:  List<IntArray> = testIdx.map { y[it] }

        return Quad(xTrain, yTrain, xTest, yTest)
    }
}
