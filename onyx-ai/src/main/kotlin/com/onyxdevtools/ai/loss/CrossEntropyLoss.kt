package com.onyxdevtools.ai.loss

import com.onyxdevtools.ai.Matrix
import kotlin.math.exp
import kotlin.math.ln

class CrossEntropyLoss : LossFunction {
    override fun calculate(predictions: Matrix, targets: Matrix): Double {
        var loss = 0.0
        val batchSize = targets.size

        for (batchIndex in 0 until batchSize) {
            val logits = predictions[batchIndex]
            val expSum = logits.sumOf { exp(it) }
            val probs = logits.map { exp(it) / expSum }.toDoubleArray()

            val targetIndex = targets[batchIndex].indexOfFirst { it == 1.0 }
            if (targetIndex >= 0 && targetIndex < probs.size) {
                loss -= ln(probs[targetIndex] + 1e-10)
            }
        }
        return loss / batchSize
    }
}
