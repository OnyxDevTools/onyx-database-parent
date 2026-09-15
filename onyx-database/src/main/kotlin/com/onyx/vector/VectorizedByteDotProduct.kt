package com.onyx.vector

import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorOperators
import jdk.incubator.vector.VectorShape
import jdk.incubator.vector.VectorSpecies

/** Loaded only when the application enables jdk.incubator.vector. */
internal object VectorizedByteDotProduct {
    private val preferredBits = IntVector.SPECIES_PREFERRED.vectorBitSize()
    // Fixed shapes also handle architectures whose preferred width is not a power of two.
    private val integers = if (preferredBits >= 512) IntVector.SPECIES_512 else IntVector.SPECIES_256
    // Each byte load has exactly as many lanes as the widened integer vector.
    private val bytes = VectorSpecies.of(
        Byte::class.javaPrimitiveType!!,
        VectorShape.forBitSize(integers.vectorBitSize() / 4),
    )
    val isSupported: Boolean = preferredBits >= 256

    fun dotProduct(left: ByteArray, right: ByteArray): Int {
        val step = integers.length()
        var sum0 = IntVector.zero(integers)
        var sum1 = sum0
        var index = 0
        val unrolledLimit = left.size - step * 2
        while (index <= unrolledLimit) {
            sum0 = sum0.add(load(left, index).mul(load(right, index)))
            sum1 = sum1.add(load(left, index + step).mul(load(right, index + step)))
            index += step * 2
        }
        val limit = integers.loopBound(left.size)
        while (index < limit) {
            sum0 = sum0.add(load(left, index).mul(load(right, index)))
            index += step
        }
        // Widen signed bytes before multiplying; neither products nor reductions overflow Int
        // for QuantizedCosineVector.MAX_DIMENSIONS, including the signed-byte minimum -128.
        var result = sum0.add(sum1).reduceLanes(VectorOperators.ADD)
        while (index < left.size) {
            result += left[index].toInt() * right[index].toInt()
            index++
        }
        return result
    }

    private fun load(values: ByteArray, offset: Int): IntVector =
        ByteVector.fromArray(bytes, values, offset).convertShape(VectorOperators.B2I, integers, 0) as IntVector
}
