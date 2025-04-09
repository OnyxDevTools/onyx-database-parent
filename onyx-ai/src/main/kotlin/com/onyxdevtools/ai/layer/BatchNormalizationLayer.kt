package com.onyxdevtools.ai.layer

import java.io.Serializable

class BatchNormalizationLayer(val size: Int) : Serializable {
    var gamma = DoubleArray(size) { 1.0 }
    var beta = DoubleArray(size)

    /* training‑only state */
    @Transient
    var mean: DoubleArray? = null   // keep if you want running‑mean inference

    @Transient
    var variance: DoubleArray? = null

    @Transient
    var normalized: Array<DoubleArray>? = null

    @Transient
    var a: Array<DoubleArray>? = null

    @Transient
    var mGamma = DoubleArray(size)

    @Transient
    var vGamma = DoubleArray(size)

    @Transient
    var mBeta = DoubleArray(size)

    @Transient
    var vBeta = DoubleArray(size)

    @Transient
    var gradGamma: DoubleArray? = null

    @Transient
    var gradBeta: DoubleArray? = null

    companion object {
        @JvmStatic
        private val serialVersionUID: Long = 1L
    }
}