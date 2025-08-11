package com.onyxdevtools.ai.layer

import Activation
import com.onyxdevtools.ai.FlexibleMatrix
import com.onyxdevtools.ai.extensions.Matrix
import com.onyxdevtools.ai.toFlexibleMatrix
import com.onyxdevtools.ai.toMatrix

/**
 * Represents a neural network layer that supports both single and double precision matrices.
 * A layer supports forward and backward passes, parameter updates, and cloning for training.
 */
interface Layer : java.io.Serializable {

    /**
     * The output matrix after the activation function has been applied.
     */
    var output: FlexibleMatrix?

    /**
     * The pre-activation output (i.e., the raw result before the activation function is applied).
     */
    var preActivation: FlexibleMatrix?

    /**
     * The activation function used by this layer.
     */
    val activation: Activation

    /**
     * Creates a deep copy of this layer, including its parameters.
     *
     * @return A new instance of this layer with the same state.
     */
    fun clone(): Layer

    /**
     * Performs a forward pass through the layer.
     *
     * @param input The input matrix for this layer.
     * @param isTraining Whether the model is in training mode.
     * @param nextLayer Optional reference to the next layer in the network.
     * @return The output matrix after applying layer operations.
     */
    fun forward(
        input: FlexibleMatrix,
        isTraining: Boolean,
        nextLayer: Layer?
    ): FlexibleMatrix = input
    
    /**
     * Backward compatibility method for legacy Matrix input
     */
    fun forward(
        input: Matrix,
        isTraining: Boolean,
        nextLayer: Layer?
    ): Matrix = forward(input.toFlexibleMatrix(), isTraining, nextLayer).toMatrix()

    /**
     * Updates this layer's learnable parameters using the Adam optimizer.
     *
     * @param adamBeta1Power Exponential decay power of beta1 for bias correction.
     * @param adamBeta2Power Exponential decay power of beta2 for bias correction.
     * @param adamBeta1 Exponential decay rate for the first moment estimates.
     * @param adamBeta2 Exponential decay rate for the second moment estimates.
     * @param learningRate The learning rate for weight updates.
     */
    fun updateParameters(
        adamBeta1Power: Double,
        adamBeta2Power: Double,
        adamBeta1: Double,
        adamBeta2: Double,
        learningRate: Double
    )

    /**
     * Performs the backward pass (backpropagation) for this layer.
     *
     * @param currentInput The input matrix from the previous forward pass.
     * @param delta The gradient of the loss with respect to this layer's output.
     * @param featureSize The number of training samples in the current batch.
     * @param nextLayer Optional reference to the next layer (used for chaining gradients).
     * @param previousLayer Optional reference to the previous layer.
     * @param lambda The L2 regularization strength.
     * @return The gradient with respect to the input of this layer (to pass backward).
     */
    fun backward(
        currentInput: FlexibleMatrix?,
        delta: FlexibleMatrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): FlexibleMatrix
    
    /**
     * Backward compatibility method for legacy Matrix input
     */
    fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): Matrix = backward(
        currentInput?.toFlexibleMatrix(),
        delta.toFlexibleMatrix(),
        featureSize,
        nextLayer,
        previousLayer,
        lambda
    ).toMatrix()

    /**
     * Prepares the input for forward propagation.
     * Can be overridden by subclasses to normalize or preprocess inputs.
     *
     * @param input The raw input matrix.
     * @param isTraining Identify if the predict is training or not
     * @return The processed input matrix.
     */
    fun preForward(input: FlexibleMatrix, isTraining: Boolean): FlexibleMatrix = input
    
    /**
     * Backward compatibility method for legacy Matrix input
     */
    fun preForward(input: Matrix, isTraining: Boolean): Matrix = preForward(input.toFlexibleMatrix(), isTraining).toMatrix()
}
