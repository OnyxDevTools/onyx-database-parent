package com.onyx.vector

/**
 * Transient index-write state produced alongside a persisted [VectorRepresentation].
 *
 * Feature route keys are captured while the encoder still owns the complete fingerprints. This
 * lets index maintenance avoid decoding the representation and recreating every fingerprint only
 * to derive the same routing keys again. The prepared state is never part of the persisted format.
 */
internal class PreparedVectorRepresentation(
    val representation: VectorRepresentation,
    val featureRouteKeys: LongArray
) {
    companion object {
        /** Reconstructs transient routing state when only a persisted representation is available. */
        fun fromRepresentation(representation: VectorRepresentation): PreparedVectorRepresentation {
            val featureWords = representation.featureWords
            val wordCount = representation.featureWordCount
            val routeKeys = LinkedHashSet<Long>(representation.featureCount)
            var offset = 0
            while (offset < featureWords.size) {
                routeKeys += FeatureFingerprint(
                    featureWords.copyOfRange(offset, offset + wordCount)
                ).routeKey
                offset += wordCount
            }
            return PreparedVectorRepresentation(representation, routeKeys.toLongArray())
        }
    }
}
