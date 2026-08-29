package com.onyx.persistence.annotations

/**
 * Selects the sparse feature families emitted for one vector-managed field.
 *
 * Presence/null routes are emitted for every included field independently of [families]. [AUTO]
 * keeps the low-cost default of categorical equality only. More expensive interval and lexical
 * routes must be selected explicitly, while [UNIVERSAL] selects every available family.
 */
@Target(AnnotationTarget.FIELD)
annotation class VectorAttribute(
    val mode: VectorAttributeMode = VectorAttributeMode.AUTO,
    val families: Array<VectorFeatureFamily> = []
)

enum class VectorAttributeMode {
    /** Emit presence/null plus categorical equality routes. [VectorAttribute.families] must be empty. */
    AUTO,

    /** Emit exactly [VectorAttribute.families], in addition to presence/null routes. */
    SELECTED,

    /** Emit every feature family. */
    UNIVERSAL,

    /** Exclude the field from the vector representation entirely. */
    IGNORE
}

/** Independently selectable sparse feature families for a vector-managed field. */
enum class VectorFeatureFamily {
    /** Stable full-value category used by equality and set predicates. */
    CATEGORICAL,

    /** Root-to-leaf binary interval path used by ordered/range predicates. */
    INTERVAL,

    /** Java-compatible case-folded value used by exact regex and tokenless LIKE routes. */
    TEXT_EXACT,

    /** Normalized per-field and whole-record tokens used by LIKE fallback and lexical search. */
    TEXT_TERM,

    /** Length-preserving case-folded leading code-point routes used by STARTS_WITH. */
    TEXT_PREFIX,

    /** Case-folded one-through-three-code-point routes used by CONTAINS and safe regex literals. */
    TEXT_NGRAM
}
