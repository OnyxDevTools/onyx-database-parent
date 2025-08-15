package com.onyxdevtools.ai

import com.onyxdevtools.ai.compute.*

/**
 * Create a tensor with the best available backend:
 * 1) If Metal is available: MetalTensor + MetalComputeBackend
 * 2) Else if Vector API available: HeapTensor + CPUComputeBackend
 * 3) Else: HeapTensor + BasicCPUComputeBackend
 */
fun createTensor(rows: Int, cols: Int): Tensor {
    val metalBackend = runCatching {
        if (MetalComputeBackend.isMetalAvailable()) metalCompute else null
    }.getOrNull()

    if (metalBackend != null) {
        val buf = Tensor.allocateDirectBuffer(rows * cols)
        val t = MetalTensor(
            buffer = buf,
            rows = rows,
            cols = cols,
            metal = metalBackend
        )
        return t
    }

    // --- Otherwise choose CPU path based on Vector API availability ---
    val vectorsAvailable = isVectorApiAvailable()
    val backend: ComputeBackend =
        if (vectorsAvailable) CPUComputeBackend() else BasicCPUComputeBackend()

    val t = HeapTensor(FloatArray(rows * cols), rows, cols)
    return t
}

/** Best-effort check for Java Vector API without requiring compile-time module presence. */
private fun isVectorApiAvailable(): Boolean {
    return try {
        val cls = Class.forName("jdk.incubator.vector.FloatVector")
        // Access SPECIES_PREFERRED reflectively to ensure the vector module is actually usable
        cls.getDeclaredField("SPECIES_PREFERRED").get(null)
        true
    } catch (_: Throwable) {
        false
    }
}
