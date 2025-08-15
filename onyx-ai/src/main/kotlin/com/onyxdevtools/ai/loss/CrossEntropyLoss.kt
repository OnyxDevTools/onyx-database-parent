package com.onyxdevtools.ai.loss

import com.onyxdevtools.ai.Tensor
import kotlin.math.exp
import kotlin.math.ln

class CrossEntropyLoss : LossFunction {

    override fun calculate(predictions: Tensor, targets: Tensor): Float {
        require(predictions.rows == targets.rows && predictions.cols == targets.cols) {
            "Predictions (${predictions.rows}x${predictions.cols}) and targets (${targets.rows}x${targets.cols}) must have same shape"
        }

        val batchSize = targets.rows
        if (batchSize == 0 || targets.cols == 0) return 0f

        var loss = 0.0

        var i = 0
        while (i < batchSize) {
            // find one-hot target index
            var targetIndex = -1
            var j = 0
            val numClasses = targets.cols
            while (j < numClasses) {
                if (targets[i, j] == 1.0f) { targetIndex = j; break }
                j++
            }
            if (targetIndex >= 0) {
                // stable softmax: shift by row max
                var maxLogit = predictions[i, 0]
                j = 1
                while (j < numClasses) {
                    val v = predictions[i, j]
                    if (v > maxLogit) maxLogit = v
                    j++
                }
                var sumExp = 0.0f
                j = 0
                while (j < numClasses) {
                    sumExp += exp((predictions[i, j] - maxLogit).toDouble()).toFloat()
                    j++
                }
                val logProb = (predictions[i, targetIndex] - maxLogit) - ln(sumExp.toDouble()).toFloat()
                loss -= logProb.toDouble()
            }
            i++
        }

        return (loss / batchSize).toFloat()
    }
}
