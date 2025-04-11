package com.onyxdevtools.ai.layer

import com.onyxdevtools.ai.extensions.elementWiseMultiply
import java.io.Serializable
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.IntStream
import kotlin.math.sqrt
import kotlin.random.Random

class Layer(
    val inputSize: Int,
    val outputSize: Int,
    val activation: Activation,
    val dropoutRate: Double = 0.0
) : Serializable {

    var weights: Array<DoubleArray> =
        Array(inputSize) {
            DoubleArray(outputSize) {
                Random.nextDouble(
                    -1.0,
                    1.0
                ) * sqrt(2.0 / (inputSize + outputSize))
            }
        }
    var biases: DoubleArray = DoubleArray(outputSize)

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

    fun applyDropout() {
        val activations = a ?: error("Layer 'a' array must not be null.")
        val rows = activations.size
        val cols = activations[0].size
        val dr = dropoutRate
        val scale = 1.0 / (1 - dr)

        // 1) Allocate the dropoutMask array
        dropoutMask = Array(rows) { DoubleArray(cols) }

        // 2) Fill the dropoutMask in parallel
        IntStream.range(0, rows).parallel().forEach { i ->
            val rng = ThreadLocalRandom.current()
            val rowMask = dropoutMask!![i]
            for (j in rowMask.indices) {
                rowMask[j] = if (rng.nextDouble() < dr) 0.0 else scale
            }
        }

        a = elementWiseMultiply(a!!, dropoutMask!!)
    }

    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}