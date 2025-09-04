package dev.onyx.ai.extensions

import dev.onyx.ai.Tensor
import kotlin.math.pow

/**
 * Computes the mean standard error between this tensor (predictions) and [actual] tensor.
 *
 * Delegates to the current compute backend for efficient computation.
 *
 * @receiver Predicted tensor.
 * @param actual Actual tensor of same or larger dimensions.
 * @return Mean squared error across matching elements, or 0.0f if no elements.
 */
fun Tensor.meanStandardError(actual: Tensor): Float {
    val predicted = this
    var sum = 0.0f
    var total = 0

    val rows = minOf(predicted.size, actual.size)
    for (i in 0 until rows) {
        val cols = minOf(predicted[i].size, actual[i].size)
        for (j in 0 until cols) {
            sum += (predicted[i][j] - actual[i][j]).pow(2)
            total++
        }
    }
    return if (total > 0) sum / total else 0.0f
}

fun Tensor.average(): Double = this.data.average()
