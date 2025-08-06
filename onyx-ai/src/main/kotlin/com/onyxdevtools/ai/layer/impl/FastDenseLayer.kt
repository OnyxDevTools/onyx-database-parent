package com.onyxdevtools.ai.layer.impl

import Activation
import com.onyxdevtools.ai.Matrix
import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.Layer
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * High-performance dense layer optimized for large vocabularies.
 * Uses standard dense matrix operations with sparse gradient updates for efficiency.
 */
class FastDenseLayer(
    private val inputSize: Int,
    private val outputSize: Int,
    override val activation: Activation = Activation.LINEAR
) : Layer {

    override var preActivation: Matrix? = null
    override var output: Matrix? = null

    private val weightInitLimit = sqrt(6.0 / (inputSize + outputSize))

    // Dense weight matrices - much faster than sparse HashMap
    private var weights = Array(inputSize) { DoubleArray(outputSize) { Random.nextDouble(-weightInitLimit, weightInitLimit) } }
    private var momentWeights = Array(inputSize) { DoubleArray(outputSize) }
    private var velocityWeights = Array(inputSize) { DoubleArray(outputSize) }
    
    private var biases = DoubleArray(outputSize) { 0.0 }
    private var momentBiases = DoubleArray(outputSize)
    private var velocityBiases = DoubleArray(outputSize)

    private var gradientWeights: Matrix? = null
    private var gradientBiases: DoubleArray? = null

    override fun forward(input: Matrix, isTraining: Boolean, nextLayer: Layer?): Matrix {
        // Fast dense matrix multiplication: input × weights + biases
        val linearOutput = matrixMultiplyDense(input, weights, biases)
        
        val nextInput = nextLayer?.preForward(linearOutput, isTraining) ?: linearOutput
        this.preActivation = nextInput
        output = applyElementWise(nextInput, activation::activate)
        return output!!
    }
    
    /**
     * Optimized dense matrix multiplication: input × weights + biases
     * Much faster than sparse operations for large vocabularies.
     */
    private fun matrixMultiplyDense(input: Matrix, weights: Matrix, biases: DoubleArray): Matrix {
        val batchSize = input.size
        val result = Array(batchSize) { DoubleArray(outputSize) }
        
        // Dense matrix multiplication - highly optimized
        for (batchIdx in 0 until batchSize) {
            // Initialize with biases
            System.arraycopy(biases, 0, result[batchIdx], 0, outputSize)
            
            // input[batch] × weights = result[batch]
            val inputRow = input[batchIdx]
            val resultRow = result[batchIdx]
            
            for (inputIdx in 0 until inputSize) {
                val inputValue = inputRow[inputIdx]
                val weightRow = weights[inputIdx]
                
                // Vectorized inner product
                for (outputIdx in 0 until outputSize) {
                    resultRow[outputIdx] += inputValue * weightRow[outputIdx]
                }
            }
        }
        
        return result
    }

    /**
     * Sparse backward pass - only updates weights for tokens that appeared in batch.
     * This gives you the speed of dense forward pass with sparse gradient efficiency.
     */
    fun backwardSparse(
        currentInput: Matrix?,
        predictions: Matrix,
        sparseTargets: IntArray,
        featureSize: Double,
        previousLayer: Layer?,
        lambda: Double
    ): Matrix {
        val previousOutput = previousLayer?.output ?: currentInput!!
        
        // Find unique tokens that appear in targets (sparse optimization)
        val uniqueTokens = sparseTargets.filter { it >= 0 }.toSet()
        
        // Initialize gradients (only need to compute for accessed tokens)
        gradientWeights = Array(inputSize) { DoubleArray(outputSize) { 0.0 } }
        gradientBiases = DoubleArray(outputSize) { 0.0 }
        
        // Compute sparse categorical cross-entropy gradients
        val gradientLogits = Array(predictions.size) { DoubleArray(outputSize) { 0.0 } }
        
        for (sampleIdx in predictions.indices) {
            if (sampleIdx < sparseTargets.size) {
                val targetToken = sparseTargets[sampleIdx]
                if (targetToken >= 0 && targetToken < outputSize) {
                    val logits = predictions[sampleIdx]
                    
                    // Compute softmax efficiently
                    val maxLogit = logits.maxOrNull() ?: 0.0
                    var sumExp = 0.0
                    for (logit in logits) {
                        sumExp += kotlin.math.exp(logit - maxLogit)
                    }
                    
                    // Gradient: softmax[i] - δ(i == target)
                    for (tokenIdx in logits.indices) {
                        val softmaxValue = kotlin.math.exp(logits[tokenIdx] - maxLogit) / sumExp
                        gradientLogits[sampleIdx][tokenIdx] = if (tokenIdx == targetToken) {
                            softmaxValue - 1.0
                        } else {
                            softmaxValue
                        }
                    }
                }
            }
        }
        
        val currentDelta = elementWiseMultiply(
            gradientLogits,
            applyElementWise(preActivation!!, activation::derivative)
        )

        // SPARSE gradient computation - only update weights for accessed tokens
        for (inputIdx in 0 until inputSize) {
            for (outputIdx in uniqueTokens) {
                if (outputIdx >= 0 && outputIdx < outputSize) {
                    var gradient = 0.0
                    for (sampleIdx in currentDelta.indices) {
                        gradient += previousOutput[sampleIdx][inputIdx] * currentDelta[sampleIdx][outputIdx]
                    }
                    val currentWeight = weights[inputIdx][outputIdx]
                    gradientWeights!![inputIdx][outputIdx] = gradient / featureSize + lambda * currentWeight
                }
            }
        }
        
        // Sparse bias gradients
        for (outputIdx in uniqueTokens) {
            if (outputIdx >= 0 && outputIdx < outputSize) {
                var gradient = 0.0
                for (sampleIdx in currentDelta.indices) {
                    gradient += currentDelta[sampleIdx][outputIdx]
                }
                gradientBiases!![outputIdx] = gradient / featureSize
            }
        }

        // Backward pass to previous layer
        val deltaPrevious = Array(currentDelta.size) { DoubleArray(inputSize) { 0.0 } }
        
        for (sampleIdx in currentDelta.indices) {
            for (inputIdx in 0 until inputSize) {
                var sum = 0.0
                for (outputIdx in 0 until outputSize) {
                    sum += currentDelta[sampleIdx][outputIdx] * weights[inputIdx][outputIdx]
                }
                deltaPrevious[sampleIdx][inputIdx] = sum
            }
        }
        
        return deltaPrevious
    }

    override fun backward(
        currentInput: Matrix?,
        delta: Matrix,
        featureSize: Double,
        nextLayer: Layer?,
        previousLayer: Layer?,
        lambda: Double
    ): Matrix {
        return backwardSparse(currentInput, delta, IntArray(0), featureSize, previousLayer, lambda)
//        throw UnsupportedOperationException("Use backwardSparse() for vocabulary layers")
    }

    override fun updateParameters(
        adamBeta1Power: Double,
        adamBeta2Power: Double,
        adamBeta1: Double,
        adamBeta2: Double,
        learningRate: Double
    ) {
        fun correctMoment(m: Double) = m / (1.0 - adamBeta1Power)
        fun correctVelocity(v: Double) = v / (1.0 - adamBeta2Power)

        // SPARSE parameter updates - only update accessed tokens
        val gradWeights = gradientWeights!!
        val gradBiases = gradientBiases!!
        
        for (inputIdx in 0 until inputSize) {
            for (outputIdx in 0 until outputSize) {
                val gradient = gradWeights[inputIdx][outputIdx]
                if (gradient != 0.0) { // Only update if gradient was computed
                    val newMoment = adamBeta1 * momentWeights[inputIdx][outputIdx] + (1 - adamBeta1) * gradient
                    val newVelocity = adamBeta2 * velocityWeights[inputIdx][outputIdx] + (1 - adamBeta2) * gradient * gradient
                    
                    momentWeights[inputIdx][outputIdx] = newMoment
                    velocityWeights[inputIdx][outputIdx] = newVelocity
                    weights[inputIdx][outputIdx] -= learningRate *
                            correctMoment(newMoment) / (sqrt(correctVelocity(newVelocity)) + EPSILON)
                }
            }
        }

        for (outputIdx in 0 until outputSize) {
            val gradient = gradBiases[outputIdx]
            if (gradient != 0.0) {
                momentBiases[outputIdx] = adamBeta1 * momentBiases[outputIdx] + (1 - adamBeta1) * gradient
                velocityBiases[outputIdx] = adamBeta2 * velocityBiases[outputIdx] + (1 - adamBeta2) * gradient * gradient
                biases[outputIdx] -= learningRate *
                        correctMoment(momentBiases[outputIdx]) / (sqrt(correctVelocity(velocityBiases[outputIdx])) + EPSILON)
            }
        }
    }

    override fun clone(): FastDenseLayer {
        return FastDenseLayer(inputSize, outputSize, activation).also { copy ->
            copy.weights = weights.deepCopy()
            copy.momentWeights = momentWeights.deepCopy()
            copy.velocityWeights = velocityWeights.deepCopy()
            copy.biases = biases.copyOf()
            copy.momentBiases = momentBiases.copyOf()
            copy.velocityBiases = velocityBiases.copyOf()
            copy.preActivation = preActivation?.deepCopy()
            copy.output = output?.deepCopy()
            copy.gradientWeights = gradientWeights?.deepCopy()
            copy.gradientBiases = gradientBiases?.copyOf()
        }
    }

    companion object {
        private const val EPSILON = 1e-8
        private const val serialVersionUID = 1L
    }
}
