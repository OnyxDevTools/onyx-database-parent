package com.onyxdevtools.ai.loss

import com.onyxdevtools.ai.Matrix

interface LossFunction {
    fun calculate(predictions: Matrix, targets: Matrix): Double
}
