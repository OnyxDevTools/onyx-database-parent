package com.onyxdevtools.ai.loss

import com.onyxdevtools.ai.FlexibleMatrix


/**
 * Interface defining the contract for loss functions used in neural network training.
 *
 * Loss functions measure the difference between predicted outputs and target values,
 * providing a scalar value that the neural network aims to minimize during training.
 * The loss value guides the optimization process through backpropagation.
 *
 * Common implementations include:
 * - Cross-entropy loss for classification tasks
 * - Mean squared error for regression tasks
 * - Sparse categorical cross-entropy for multi-class classification
 *
 * @see CrossEntropyLoss
 */
interface LossFunction {
    /**
     * Calculates the loss value between predicted outputs and target values using FlexibleMatrix.
     *
     * The loss function computes a scalar value representing how far the predictions
     * are from the target values. Lower values indicate better model performance.
     *
     * @param predictions The predicted output FlexibleMatrix from the neural network.
     *                   Shape should be [batch_size, output_features]
     * @param targets The target/ground truth FlexibleMatrix for comparison.
     *               Shape should match predictions: [batch_size, output_features]
     * @return The calculated loss value as a Double. Lower values indicate better predictions.
     * @throws IllegalArgumentException if matrix dimensions don't match
     */
    fun calculate(predictions: FlexibleMatrix, targets: FlexibleMatrix): Double

}
