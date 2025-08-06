package com.onyxdevtools.ai.extensions

import kotlin.math.exp
import kotlin.math.ln

/**
 * Utility functions for sparse categorical cross-entropy operations.
 * These functions enable memory-efficient training with large vocabularies by working
 * directly with token IDs instead of one-hot encoded vectors.
 */

/**
 * Computes sparse categorical cross-entropy loss between predictions and sparse targets.
 *
 * This function calculates cross-entropy loss directly from logits and target token IDs,
 * avoiding the memory overhead of creating one-hot encoded target vectors.
 * Positions with target ID -1 are ignored in the loss computation (masked out).
 *
 * @param predicted Matrix of prediction logits (samples × vocab_size)
 * @param sparseTargets Array of target token IDs for each sample position.
 *                     Each element should be in range [0, vocab_size) or -1 to ignore.
 * @param sampleWeights Optional weights for each sample. If null, all samples weighted equally.
 * @return The mean sparse categorical cross-entropy loss, ignoring masked positions.
 * @throws IllegalArgumentException if array dimensions don't match or contain invalid values
 */
fun sparseCategoricalCrossEntropy(
    predicted: Array<DoubleArray>,
    sparseTargets: IntArray,
    sampleWeights: DoubleArray? = null
): Double {
    require(predicted.size == sparseTargets.size) {
        "Predictions and targets must have same number of samples"
    }
    require(sampleWeights == null || sampleWeights.size == sparseTargets.size) {
        "Sample weights size must match targets size"
    }
    
    val vocabSize = predicted.firstOrNull()?.size ?: return 0.0
    val weights = sampleWeights ?: DoubleArray(sparseTargets.size) { 1.0 }
    
    var totalLoss = 0.0
    var totalWeight = 0.0
    
    for (i in predicted.indices) {
        val targetId = sparseTargets[i]
        if (targetId == -1) continue  // Skip ignored positions
        
        require(targetId >= 0 && targetId < vocabSize) {
            "Target token ID $targetId at position $i is out of vocabulary range [0, $vocabSize)"
        }
        
        val logits = predicted[i]
        val weight = weights[i]
        
        // Compute log-softmax for numerical stability (optimized - no temporary collections)
        val maxLogit = logits.maxOrNull() ?: continue
        var sumExp = 0.0
        for (logit in logits) {
            sumExp += exp(logit - maxLogit)
        }
        val logSumExp = ln(sumExp) + maxLogit
        val logProb = logits[targetId] - logSumExp
        
        totalLoss += weight * (-logProb)  // Negative log likelihood
        totalWeight += weight
    }
    
    return if (totalWeight > 0) totalLoss / totalWeight else 0.0
}

/**
 * Computes gradients for sparse categorical cross-entropy loss.
 *
 * This function calculates the gradients of sparse categorical cross-entropy loss
 * with respect to the logits, returning gradients only for the actual target positions.
 * Positions with target ID -1 are ignored (gradient = 0).
 *
 * The gradient for sparse categorical cross-entropy is:
 * grad[i,j] = softmax[i,j] - 1 if j == target[i], softmax[i,j] otherwise
 * where softmax[i,j] = exp(logits[i,j]) / sum_k(exp(logits[i,k]))
 *
 * @param predicted Matrix of prediction logits (samples × vocab_size)
 * @param sparseTargets Array of target token IDs for each sample position
 * @param sampleWeights Optional weights for each sample. If null, all samples weighted equally.
 * @return Gradient matrix with same shape as predicted, zero for ignored positions
 * @throws IllegalArgumentException if array dimensions don't match or contain invalid values
 */
fun sparseCategoricalCrossEntropyGradients(
    predicted: Array<DoubleArray>,
    sparseTargets: IntArray,
    sampleWeights: DoubleArray? = null
): Array<DoubleArray> {
    require(predicted.size == sparseTargets.size) {
        "Predictions and targets must have same number of samples"
    }
    require(sampleWeights == null || sampleWeights.size == sparseTargets.size) {
        "Sample weights size must match targets size"
    }
    
    val vocabSize = predicted.firstOrNull()?.size ?: return emptyArray()
    val weights = sampleWeights ?: DoubleArray(sparseTargets.size) { 1.0 }
    val gradients = Array(predicted.size) { DoubleArray(vocabSize) }
    
    for (i in predicted.indices) {
        val targetId = sparseTargets[i]
        if (targetId == -1) continue  // Skip ignored positions - gradients remain 0
        
        require(targetId >= 0 && targetId < vocabSize) {
            "Target token ID $targetId at position $i is out of vocabulary range [0, $vocabSize)"
        }
        
        val logits = predicted[i]
        val weight = weights[i]
        
        // Compute softmax probabilities (optimized - no temporary collections)
        val maxLogit = logits.maxOrNull() ?: continue
        
        // First pass: compute sum of exponentials
        var sumExp = 0.0
        for (logit in logits) {
            sumExp += exp(logit - maxLogit)
        }
        
        // Second pass: compute gradients using softmax probabilities
        for (j in logits.indices) {
            val softmaxProb = exp(logits[j] - maxLogit) / sumExp
            gradients[i][j] = weight * if (j == targetId) {
                softmaxProb - 1.0  // Target position: p - 1
            } else {
                softmaxProb  // Non-target position: p
            }
        }
    }
    
    return gradients
}

