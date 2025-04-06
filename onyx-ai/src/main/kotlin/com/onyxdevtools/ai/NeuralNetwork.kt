@file:Suppress("unused", "MemberVisibilityCanBePrivate")
package com.onyxdevtools.ai

import com.onyxdevtools.ai.extensions.*
import com.onyxdevtools.ai.layer.BatchNormalizationLayer
import com.onyxdevtools.ai.layer.Layer
import java.io.Serializable
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class NeuralNetwork(
    private val layers: List<Any>,
    private val learningRate: Double = 0.001,
    private val lambda: Double = 0.0001,
    private val beta1: Double = 0.9,
    private val beta2: Double = 0.999,
    private val epsilon: Double = 1e-8
) : Serializable {

    @Transient
    private var adamStepTime = 0

    @Transient
    private var currentInput: Array<DoubleArray>? = null

    fun predict(input: Array<DoubleArray>, isTraining: Boolean = false): Array<DoubleArray> {
        currentInput = input
        var a = input
        for (layer in layers) when (layer) {
            is Layer -> {
                val z = addVectorToRows(matrixMultiply(a, layer.weights), layer.biases)
                layer.z = z
                layer.a = applyElementWise(z, layer.activation::f)

                if (isTraining && layer.dropoutRate > 0.0) {
                    layer.dropoutMask = layer.a!!.map { row ->
                        row.map { if (Random.nextDouble() < layer.dropoutRate) 0.0 else 1.0 / (1 - layer.dropoutRate) }
                            .toDoubleArray()
                    }.toTypedArray()
                    layer.a = elementWiseMultiply(layer.a!!, layer.dropoutMask!!)
                }
                a = layer.a!!
            }

            is BatchNormalizationLayer -> {
                val mean = DoubleArray(layer.size) { j -> a.sumOf { it[j] } / a.size }
                val varc = DoubleArray(layer.size) { j -> a.sumOf { (it[j] - mean[j]).pow(2) } / a.size }
                layer.mean = mean
                layer.variance = varc
                layer.normalized = a.map { row ->
                    row.mapIndexed { j, x -> (x - mean[j]) / sqrt(varc[j] + 1e-8) }.toDoubleArray()
                }.toTypedArray()
                layer.a = layer.normalized!!.map { row ->
                    row.mapIndexed { j, x -> layer.gamma[j] * x + layer.beta[j] }.toDoubleArray()
                }.toTypedArray()
                a = layer.a!!
            }
        }
        return a
    }

    /* ---------- backward ---------- */

    private fun backward(yPred: Array<DoubleArray>, yTrue: Array<DoubleArray>, sampleWeights: DoubleArray? = null) {
        requireNotNull(currentInput) { "Call forward() before backward()" }
        val w = sampleWeights ?: DoubleArray(yTrue.size) { 1.0 }

        var delta = subtract(yPred, yTrue).mapIndexed { i, row ->
            row.map { it * w[i] }.toDoubleArray()
        }.toTypedArray()

        for (i in layers.indices.reversed()) when (val layer = layers[i]) {
            is Layer -> {
                val aPrev = if (layer === layers.first { it is Layer }) currentInput!!
                else (layers[i - 1] as? Layer)?.a ?: (layers[i - 1] as BatchNormalizationLayer).a!!

                layer.gradWeights = add(matrixMultiply(transpose(aPrev), delta), scalarMultiply(layer.weights, lambda))
                layer.gradBiases = sumColumns(delta)

                if (layer !== layers.first { it is Layer }) {
                    val zPrev = when (val prev = layers[i - 1]) {
                        is Layer -> prev.z!!
                        is BatchNormalizationLayer -> (layers[i - 2] as Layer).z!!
                        else -> error("Unexpected layer type")
                    }
                    delta = matrixMultiply(delta, transpose(layer.weights))
                    delta = elementWiseMultiply(delta, applyElementWise(zPrev, layer.activation::d))
                }
            }

            is BatchNormalizationLayer -> {
                layer.gradGamma = sumColumns(delta)
                layer.gradBeta = sumColumns(delta)

                val dNorm = delta.map { row ->
                    row.mapIndexed { j, d -> d * layer.gamma[j] }.toDoubleArray()
                }.toTypedArray()
                delta = dNorm.map { row ->
                    row.mapIndexed { j, d -> d / sqrt(layer.variance!![j] + 1e-8) }.toDoubleArray()
                }.toTypedArray()

                if (i > 0 && layers[i - 1] is Layer) {
                    val prev = layers[i - 1] as Layer
                    delta = Array(yTrue.size) { k ->
                        DoubleArray(prev.outputSize) { j -> delta[k].getOrElse(j) { 0.0 } }
                    }
                }
            }
        }
    }

    /* ---------- Adam update ---------- */

    @Suppress("DuplicatedCode")
    private fun updateParameters() {
        adamStepTime++
        for (layer in layers) when (layer) {
            is Layer -> {
                for (i in 0 until layer.inputSize) for (j in 0 until layer.outputSize) {
                    val g = layer.gradWeights!![i][j]
                    layer.mWeights[i][j] = beta1 * layer.mWeights[i][j] + (1 - beta1) * g
                    layer.vWeights[i][j] = beta2 * layer.vWeights[i][j] + (1 - beta2) * g * g
                    val mHat = layer.mWeights[i][j] / (1 - beta1.pow(adamStepTime.toDouble()))
                    val vHat = layer.vWeights[i][j] / (1 - beta2.pow(adamStepTime.toDouble()))
                    layer.weights[i][j] -= learningRate * mHat / (sqrt(vHat) + epsilon)
                }
                for (j in 0 until layer.outputSize) {
                    val g = layer.gradBiases!![j]
                    layer.mBiases[j] = beta1 * layer.mBiases[j] + (1 - beta1) * g
                    layer.vBiases[j] = beta2 * layer.vBiases[j] + (1 - beta2) * g * g
                    val mHat = layer.mBiases[j] / (1 - beta1.pow(adamStepTime.toDouble()))
                    val vHat = layer.vBiases[j] / (1 - beta2.pow(adamStepTime.toDouble()))
                    layer.biases[j] -= learningRate * mHat / (sqrt(vHat) + epsilon)
                }
            }

            is BatchNormalizationLayer -> {
                for (j in 0 until layer.size) {
                    val gG = layer.gradGamma!![j]
                    layer.mGamma[j] = beta1 * layer.mGamma[j] + (1 - beta1) * gG
                    layer.vGamma[j] = beta2 * layer.vGamma[j] + (1 - beta2) * gG * gG
                    val mHatG = layer.mGamma[j] / (1 - beta1.pow(adamStepTime.toDouble()))
                    val vHatG = layer.vGamma[j] / (1 - beta2.pow(adamStepTime.toDouble()))
                    layer.gamma[j] -= learningRate * mHatG / (sqrt(vHatG) + epsilon)

                    val gB = layer.gradBeta!![j]
                    layer.mBeta[j] = beta1 * layer.mBeta[j] + (1 - beta1) * gB
                    layer.vBeta[j] = beta2 * layer.vBeta[j] + (1 - beta2) * gB * gB
                    val mHatB = layer.mBeta[j] / (1 - beta1.pow(adamStepTime.toDouble()))
                    val vHatB = layer.vBeta[j] / (1 - beta2.pow(adamStepTime.toDouble()))
                    layer.beta[j] -= learningRate * mHatB / (sqrt(vHatB) + epsilon)
                }
            }
        }
    }

    /* ---------- training loop ---------- */

    fun train(
        input: Array<DoubleArray>,
        yTrue: Array<DoubleArray>,
        sampleWeights: DoubleArray? = null,
        epochs: Int = 1_000,
        maxLoss: Double = 0.08
    ) {
        var loss = Double.MAX_VALUE
        var epoch = 0
        while (epoch < epochs || maxLoss < loss) {
            epoch++
            val yPred = predict(input, isTraining = true)
            backward(yPred, yTrue, sampleWeights)
            updateParameters()
            if (epoch % 100 == 0) {
                loss = computeLoss(yPred, yTrue, sampleWeights)
                println("Epoch $epoch, loss = $loss")
            }
        }
        println("Epoch $epoch, loss = $loss")
    }

    private fun computeLoss(yPred: Array<DoubleArray>, yTrue: Array<DoubleArray>, w: DoubleArray?): Double {
        val weights = w ?: DoubleArray(yTrue.size) { 1.0 }
        return yPred.zip(yTrue).mapIndexed { i, (p, t) ->
            p.zip(t).sumOf { (pp, tt) -> (pp - tt).pow(2) } * weights[i]
        }.sum() / yTrue.size
    }
}
