package com.onyxdevtools.ai.batch

import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.subset

/**
 * Batch splitter for sequential dense targets.
 *
 * X: Tensor (rows = samples, cols = features)
 * Y: Array<Tensor> (one sequence per sample; each sequence is a matrix)
 */
class SequentialBatchSplitter :
    BatchSplitter<Array<Tensor>, Array<Tensor>> {   // YIn=Array<Tensor>, YOut=Array<Tensor>

    override fun splitBatch(
        x: Tensor,
        y: Array<Tensor>,
        testFraction: Float,
        shuffle: Boolean
    ): Quad<Tensor, Array<Tensor>, Tensor, Array<Tensor>> {

        require(testFraction in 0.0..1.0) { "Test fraction must be between 0.0 and 1.0" }
        require(x.rows == y.size) {
            "Features and targets must have same number of samples: X.rows=${x.rows}, Y.size=${y.size}"
        }

        val idx = (0 until x.rows).toMutableList().apply { if (shuffle) shuffle() }
        val testSize = ((idx.size * testFraction).toInt()).coerceAtLeast(if (idx.isEmpty()) 0 else 1)
        val testIdx = idx.take(testSize)
        val trainIdx = idx.drop(testSize)

        val xTrain = x.subset(trainIdx)
        val xTest  = x.subset(testIdx)

        val yTrain: Array<Tensor> = trainIdx.map { y[it] }.toTypedArray()
        val yTest:  Array<Tensor> = testIdx.map { y[it] }.toTypedArray()

        return Quad(xTrain, yTrain, xTest, yTest)
    }
}
