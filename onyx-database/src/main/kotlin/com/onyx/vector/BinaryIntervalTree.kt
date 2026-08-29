package com.onyx.vector

import java.math.BigInteger

/** A canonical node in a binary interval tree. */
data class IntervalNode(
    val depth: Int,
    val prefix: BigInteger,
) {

    init {
        require(depth in 0..MAX_DOMAIN_BITS) { "depth must be between zero and $MAX_DOMAIN_BITS" }
        require(prefix.signum() >= 0) { "prefix must be unsigned" }
        require(prefix.bitLength() <= depth) { "prefix does not fit within depth $depth" }
    }

    /** Stable logical-feature token for this node. */
    val canonicalToken: String
        get() = "interval:$depth:${prefix.toString(16)}"

    /** Alias suitable for callers that treat tokens as stored properties. */
    val token: String
        get() = canonicalToken

    /** Inclusive lower coordinate represented by this node in a [bits]-wide domain. */
    fun lowerBound(bits: Int): BigInteger {
        validateDomainBits(bits)
        require(depth <= bits) { "node depth $depth exceeds domain width $bits" }
        return prefix.shiftLeft(bits - depth)
    }

    /** Inclusive upper coordinate represented by this node in a [bits]-wide domain. */
    fun upperBound(bits: Int): BigInteger {
        val lower = lowerBound(bits)
        return lower.add(BigInteger.ONE.shiftLeft(bits - depth)).subtract(BigInteger.ONE)
    }

    /** Returns whether this node is an ancestor of, or equal to, [other]. */
    fun contains(other: IntervalNode): Boolean {
        if (depth > other.depth) return false
        return other.prefix.shiftRight(other.depth - depth) == prefix
    }

    override fun toString(): String = canonicalToken

    private companion object {
        const val MAX_DOMAIN_BITS: Int = 64

        fun validateDomainBits(bits: Int) {
            require(bits in 1..MAX_DOMAIN_BITS) { "bits must be between one and $MAX_DOMAIN_BITS" }
        }
    }
}

/** Canonical binary interval-tree encoding for unsigned domains up to 64 bits. */
object BinaryIntervalTree {

    private const val MAX_DOMAIN_BITS: Int = 64

    /**
     * Returns the root-to-leaf path containing [coordinate].
     *
     * The returned list contains `bits + 1` nodes: the depth-zero root and one
     * node at every depth through the exact leaf at [bits].
     */
    fun path(coordinate: BigInteger, bits: Int): List<IntervalNode> {
        validateCoordinate(coordinate, bits, "coordinate")
        return (0..bits).map { depth ->
            IntervalNode(depth, coordinate.shiftRight(bits - depth))
        }
    }

    /**
     * Returns the minimal disjoint dyadic cover of the inclusive range.
     *
     * Nodes are emitted in ascending coordinate order. No returned node is an
     * ancestor of another, and no sibling pair remains that could be replaced
     * by its parent.
     */
    fun cover(
        lowerInclusive: BigInteger,
        upperInclusive: BigInteger,
        bits: Int,
    ): List<IntervalNode> {
        validateCoordinate(lowerInclusive, bits, "lowerInclusive")
        validateCoordinate(upperInclusive, bits, "upperInclusive")
        require(lowerInclusive <= upperInclusive) { "lowerInclusive must not exceed upperInclusive" }

        val nodes = ArrayList<IntervalNode>(bits * 2)
        var cursor = lowerInclusive

        while (cursor <= upperInclusive) {
            val remaining = upperInclusive.subtract(cursor).add(BigInteger.ONE)
            val largestRemainingPower = remaining.bitLength() - 1
            val largestAlignedPower = if (cursor.signum() == 0) {
                bits
            } else {
                cursor.lowestSetBit.coerceAtMost(bits)
            }
            val blockPower = minOf(largestAlignedPower, largestRemainingPower)
            val depth = bits - blockPower

            nodes.add(IntervalNode(depth, cursor.shiftRight(blockPower)))
            cursor = cursor.add(BigInteger.ONE.shiftLeft(blockPower))
        }

        return nodes
    }

    private fun validateCoordinate(coordinate: BigInteger, bits: Int, name: String) {
        require(bits in 1..MAX_DOMAIN_BITS) { "bits must be between one and $MAX_DOMAIN_BITS" }
        require(coordinate.signum() >= 0) { "$name must be unsigned" }
        require(coordinate.bitLength() <= bits) { "$name does not fit within an unsigned $bits-bit domain" }
    }
}
