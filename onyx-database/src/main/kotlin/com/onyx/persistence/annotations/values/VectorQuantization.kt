package com.onyx.persistence.annotations.values

/**
 * Vector quantization mode for HNSW vector indexes.
 *
 * Controls how vectors are stored and similarity is computed:
 * - NONE: Full float32 precision (highest accuracy, highest memory usage)
 * - INT8: 8-bit quantization (good balance of accuracy and memory)
 * - INT4: 4-bit quantization (lowest memory usage, some accuracy loss)
 *
 * @author Tim Osborn
 * @since 3.0.0
 */
enum class VectorQuantization {
    /** Full float32 precision - no quantization */
    NONE,
    /** 8-bit signed integer quantization */
    INT8,
    /** 4-bit signed integer quantization (packed 2 per byte) */
    INT4
}
