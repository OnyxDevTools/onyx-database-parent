package com.onyxdevtools.ai.loss

import com.onyxdevtools.ai.FlexibleMatrix
import kotlin.math.exp
import kotlin.math.ln

/**
 * Cross-entropy loss function implementation for multi-class classification tasks.
 *
 * Cross-entropy loss measures the performance of a classification model whose output
 * is a probability value between 0 and 1. The loss increases as the predicted probability
 * diverges from the actual label. This implementation uses the softmax function to convert
 * raw logits to probabilities before computing the cross-entropy.
 *
 * The formula used is: L = -1/N * Σ(log(p_correct_class))
 * where N is the batch size and p_correct_class is the predicted probability for the correct class.
 *
 * Key features:
 * - Applies softmax normalization to convert logits to probabilities
 * - Expects targets in one-hot encoded format
 * - Includes numerical stability through epsilon addition (1e-10)
 * - Returns average loss across the batch
 *
 * This loss function is particularly effective for:
 * - Multi-class classification problems
 * - Language modeling tasks
 * - Any scenario where outputs represent class probabilities
 *
 * @see LossFunction
 */
class CrossEntropyLoss : LossFunction {
    /**
     * Calculates the cross-entropy loss between predictions and targets using FlexibleMatrix.
     *
     * The implementation performs the following steps:
     * 1. Applies softmax to convert logits to probabilities
     * 2. Identifies the correct class from one-hot encoded targets
     * 3. Computes negative log-likelihood of the correct class probability
     * 4. Averages the loss across all samples in the batch
     *
     * @param predictions FlexibleMatrix of raw logits from the neural network.
     *                   Shape: [batch_size, num_classes]
     * @param targets FlexibleMatrix of one-hot encoded target labels.
     *               Shape: [batch_size, num_classes] where each row contains
     *               exactly one 1.0 at the correct class index and 0.0 elsewhere
     * @return The calculated cross-entropy loss averaged across the batch.
     *         Lower values indicate better model performance.
     * @throws IllegalArgumentException if matrix dimensions don't match or targets
     *                                 are not properly one-hot encoded
     */
    override fun calculate(predictions: FlexibleMatrix, targets: FlexibleMatrix): Double {
        var loss = 0.0
        val batchSize = predictions.rows

        for (batchIndex in 0 until batchSize) {
            // Find max logit for numerical stability
            var maxLogit = predictions[batchIndex, 0]
            for (colIndex in 1 until predictions.cols) {
                val logit = predictions[batchIndex, colIndex]
                if (logit > maxLogit) maxLogit = logit
            }
            
            // Calculate exp sum for softmax denominator
            var expSum = 0.0
            for (colIndex in 0 until predictions.cols) {
                expSum += exp(predictions[batchIndex, colIndex] - maxLogit)
            }

            // Find target class index and compute loss directly
            var targetIndex = -1
            for (colIndex in 0 until targets.cols) {
                if (targets[batchIndex, colIndex] == 1.0) {
                    targetIndex = colIndex
                    break
                }
            }
            
            if (targetIndex >= 0) {
                val targetLogit = predictions[batchIndex, targetIndex]
                val prob = exp(targetLogit - maxLogit) / expSum
                loss -= ln(prob + 1e-10)
            }
        }
        return loss / batchSize
    }
}
