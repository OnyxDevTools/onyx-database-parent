// File: com/onyxdevtools/ai/extensions/SparseCE.kt
package com.onyxdevtools.ai.extensions

import com.onyxdevtools.ai.Tensor
import com.onyxdevtools.ai.createTensor
import kotlin.math.exp
import kotlin.math.ln

/**
 * Sparse categorical cross-entropy on TENSOR logits.
 *
 * @param predicted  Tensor of logits (samples × vocab_size)
 * @param sparseTargets  IntArray of size = samples; values in [0, vocab_size) or -1 to ignore
 * @param sampleWeights  optional per-sample weights (length = samples) or null for 1.0
 * @return mean sparse CE over non-masked samples
 */
fun sparseCategoricalCrossEntropy(
    predicted: Tensor,
    sparseTargets: IntArray,
    sampleWeights: FloatArray? = null
): Float {
    require(predicted.rows == sparseTargets.size) {
        "Predictions (rows=${predicted.rows}) and targets (size=${sparseTargets.size}) must match"
    }
    require(sampleWeights == null || sampleWeights.size == sparseTargets.size) {
        "Sample weights size must match targets size"
    }

    val rows = predicted.rows
    val cols = predicted.cols
    if (rows == 0 || cols == 0) return 0.0f

    val weights = sampleWeights ?: FloatArray(rows) { 1.0f }
    var totalLoss = 0.0f
    var totalWeight = 0.0f

    // reuse one scratch row
    val logits = FloatArray(cols)

    var i = 0
    while (i < rows) {
        val targetId = sparseTargets[i]
        if (targetId != -1) {
            require(targetId in 0 until cols) {
                "Target token ID $targetId at position $i is out of vocabulary range [0, $cols)"
            }

            predicted.readRowInto(i, logits)

            // log-softmax for numerical stability
            var maxLogit = logits[0]
            var j = 1
            while (j < cols) { if (logits[j] > maxLogit) maxLogit = logits[j]; j++ }

            var sumExp = 0.0f
            j = 0
            while (j < cols) { sumExp += exp(logits[j] - maxLogit); j++ }

            val logSumExp = ln(sumExp) + maxLogit
            val logProb = logits[targetId] - logSumExp

            val w = weights[i]
            totalLoss += w * (-logProb)
            totalWeight += w
        }
        i++
    }

    return if (totalWeight > 0f) totalLoss / totalWeight else 0.0f
}

/**
 * Sparse CE gradients w.r.t. logits.
 *
 * @return Tensor with same shape as predicted (rows × vocab_size). Zero rows when masked.
 */
fun sparseCategoricalCrossEntropyGradients(
    predicted: Tensor,
    sparseTargets: IntArray,
    sampleWeights: FloatArray? = null
): Tensor {
    require(predicted.rows == sparseTargets.size) {
        "Predictions (rows=${predicted.rows}) and targets (size=${sparseTargets.size}) must match"
    }
    require(sampleWeights == null || sampleWeights.size == sparseTargets.size) {
        "Sample weights size must match targets size"
    }

    val rows = predicted.rows
    val cols = predicted.cols
    // Instantiate an output tensor (even if cols==0, we return a proper empty-shaped tensor)
    val gradients = createTensor(rows, cols)
    if (rows == 0 || cols == 0) return gradients

    val weights = sampleWeights ?: FloatArray(rows) { 1.0f }

    // scratch buffers
    val logits = FloatArray(cols)

    var i = 0
    while (i < rows) {
        val targetId = sparseTargets[i]
        if (targetId == -1) {
            // leave gradient row as zeros
        } else {
            require(targetId in 0 until cols) {
                "Target token ID $targetId at position $i is out of vocabulary range [0, $cols)"
            }

            predicted.readRowInto(i, logits)

            // max for stability
            var maxLogit = logits[0]
            var j = 1
            while (j < cols) { if (logits[j] > maxLogit) maxLogit = logits[j]; j++ }

            // sum of exp
            var sumExp = 0.0f
            j = 0
            while (j < cols) { sumExp += exp(logits[j] - maxLogit); j++ }

            val w = weights[i]

            // write gradient row: softmax - onehot(target)
            j = 0
            while (j < cols) {
                val p = exp(logits[j] - maxLogit) / sumExp
                gradients[i, j] = w * (if (j == targetId) p - 1.0f else p)
                j++
            }
        }
        i++
    }

    return gradients
}
