package com.onyx.persistence.annotations.values

/**
 * Enum for different index types
 */
enum class IndexType {
    DEFAULT,
    /** Managed sparse interval/categorical features plus semantic fingerprint routing metadata. */
    VECTOR,
    /** Reserved persistence ordinal for the retired full-text index implementation. */
    @Deprecated("This persisted index type is retired and cannot be used")
    RETIRED
}
