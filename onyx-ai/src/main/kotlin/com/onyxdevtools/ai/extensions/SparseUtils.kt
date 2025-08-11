package com.onyxdevtools.ai.extensions

import com.onyxdevtools.ai.FlexibleMatrix
import com.onyxdevtools.ai.createMatrix
import kotlin.math.exp
import kotlin.math.ln

/**
 * Utility functions for sparse categorical cross-entropy operations.
 * These functions enable memory-efficient training with large vocabularies by working
 * directly with token IDs instead of one-hot encoded vectors.
 * Updated to work directly with FlexibleMatrix to avoid unnecessary copying.
 */

/**
 * Computes sparse categorical cross-entropy loss between predictions and sparse targets.
 * Works directly with FlexibleMatrix to avoid copying.
 *
 * @param predicted FlexibleMatrix of prediction logits (samples × vocab_size)
 * @param sparseTargets Array of target token IDs for each sample position.
 * @param sampleWeights Optional weights for each sample.
 * @return The mean sparse categorical cross-entropy loss, ignoring masked positions.
 */
fun sparseCategoricalCrossEntropy(
    predicted: FlexibleMatrix,
    sparseTargets: IntArray,
    sampleWeights: DoubleArray? = null
): Double {
    require(predicted.rows == sparseTargets.size) {
        "Predictions and targets must have same number of samples"
    }
    require(sampleWeights == null || sampleWeights.size == sparseTargets.size) {
        "Sample weights size must match targets size"
    }
    
    if (predicted.rows == 0 || predicted.cols == 0) return 0.0
    
    val vocabSize = predicted.cols
    val weights = sampleWeights ?: DoubleArray(sparseTargets.size) { 1.0 }
    
    var totalLoss = 0.0
    var totalWeight = 0.0
    
    for (i in 0 until predicted.rows) {
        val targetId = sparseTargets[i]
        if (targetId == -1) continue  // Skip ignored positions
        
        require(targetId >= 0 && targetId < vocabSize) {
            "Target token ID $targetId at position $i is out of vocabulary range [0, $vocabSize)"
        }
        
        val weight = weights[i]
        
        // Compute log-softmax for numerical stability (direct FlexibleMatrix access)
        var maxLogit = Double.NEGATIVE_INFINITY
        for (j in 0 until predicted.cols) {
            val logit = predicted[i, j]
            if (logit > maxLogit) maxLogit = logit
        }
        
        var sumExp = 0.0
        for (j in 0 until predicted.cols) {
            sumExp += exp(predicted[i, j] - maxLogit)
        }
        val logSumExp = ln(sumExp) + maxLogit
        val logProb = predicted[i, targetId] - logSumExp
        
        totalLoss += weight * (-logProb)  // Negative log likelihood
        totalWeight += weight
    }
    
    return if (totalWeight > 0) totalLoss / totalWeight else 0.0
}

/**
 * Computes gradients for sparse categorical cross-entropy loss.
 * Works directly with FlexibleMatrix to avoid copying.
 *
 * @param predicted FlexibleMatrix of prediction logits (samples × vocab_size)
 * @param sparseTargets Array of target token IDs for each sample position
 * @param sampleWeights Optional weights for each sample.
 * @return Gradient FlexibleMatrix with same shape as predicted
 */
fun sparseCategoricalCrossEntropyGradients(
    predicted: FlexibleMatrix,
    sparseTargets: IntArray,
    sampleWeights: DoubleArray? = null
): FlexibleMatrix {
    require(predicted.rows == sparseTargets.size) {
        "Predictions and targets must have same number of samples"
    }
    require(sampleWeights == null || sampleWeights.size == sparseTargets.size) {
        "Sample weights size must match targets size"
    }
    
    if (predicted.rows == 0 || predicted.cols == 0) {
        return createMatrix(0, 0, predicted.isSinglePrecision)
    }
    
    val vocabSize = predicted.cols
    val weights = sampleWeights ?: DoubleArray(sparseTargets.size) { 1.0 }
    val gradients = createMatrix(predicted.rows, predicted.cols, predicted.isSinglePrecision)
    
    for (i in 0 until predicted.rows) {
        val targetId = sparseTargets[i]
        if (targetId == -1) continue  // Skip ignored positions - gradients remain 0
        
        require(targetId >= 0 && targetId < vocabSize) {
            "Target token ID $targetId at position $i is out of vocabulary range [0, $vocabSize)"
        }
        
        val weight = weights[i]
        
        // Compute softmax probabilities (direct FlexibleMatrix access)
        var maxLogit = Double.NEGATIVE_INFINITY
        for (j in 0 until predicted.cols) {
            val logit = predicted[i, j]
            if (logit > maxLogit) maxLogit = logit
        }
        
        // First pass: compute sum of exponentials
        var sumExp = 0.0
        for (j in 0 until predicted.cols) {
            sumExp += exp(predicted[i, j] - maxLogit)
        }
        
        // Second pass: compute gradients using softmax probabilities
        for (j in 0 until predicted.cols) {
            val softmaxProb = exp(predicted[i, j] - maxLogit) / sumExp
            gradients[i, j] = weight * if (j == targetId) {
                softmaxProb - 1.0  // Target position: p - 1
            } else {
                softmaxProb  // Non-target position: p
            }
        }
    }
    
    return gradients
}

// Backward compatibility functions for legacy Matrix types
fun sparseCategoricalCrossEntropy(
    predicted: Array<DoubleArray>,
    sparseTargets: IntArray,
    sampleWeights: DoubleArray? = null
): Double = sparseCategoricalCrossEntropy(
    predicted = predicted.let { matrix ->
        createMatrix(matrix.size, matrix.firstOrNull()?.size ?: 0, false) { r, c ->
            matrix[r][c]
        }
    },
    sparseTargets = sparseTargets,
    sampleWeights = sampleWeights
)
