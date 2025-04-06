package com.onyxdevtools.ai.layer

import java.io.Serializable
import kotlin.math.sqrt
import kotlin.random.Random

class Layer(
    val inputSize: Int,
    val outputSize: Int,
    val activation: Activation,
    val dropoutRate: Double = 0.0
) : Serializable {

    val weights: Array<DoubleArray> =
        Array(inputSize) {
            DoubleArray(outputSize) {
                Random.nextDouble(
                    -1.0,
                    1.0
                ) * sqrt(2.0 / (inputSize + outputSize))
            }
        }
    val biases: DoubleArray = DoubleArray(outputSize)

    @Transient
    var z: Array<DoubleArray>? = null

    @Transient
    var a: Array<DoubleArray>? = null

    @Transient
    var dropoutMask: Array<DoubleArray>? = null

    @Transient
    var mWeights = Array(inputSize) { DoubleArray(outputSize) }

    @Transient
    var vWeights = Array(inputSize) { DoubleArray(outputSize) }

    @Transient
    var mBiases = DoubleArray(outputSize)

    @Transient
    var vBiases = DoubleArray(outputSize)

    @Transient
    var gradWeights: Array<DoubleArray>? = null

    @Transient
    var gradBiases: DoubleArray? = null
}