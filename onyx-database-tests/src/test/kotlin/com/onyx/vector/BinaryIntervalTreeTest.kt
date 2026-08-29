package com.onyx.vector

import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BinaryIntervalTreeTest {

    @Test
    fun pathContainsEveryAncestorThroughTheLeaf() {
        val coordinate = BigInteger.valueOf(73)
        val path = BinaryIntervalTree.path(coordinate, 8)

        assertEquals((0..8).toList(), path.map(IntervalNode::depth))
        assertEquals(
            listOf(0L, 0L, 1L, 2L, 4L, 9L, 18L, 36L, 73L).map(BigInteger::valueOf),
            path.map(IntervalNode::prefix),
        )
        assertEquals(IntervalNode(0, BigInteger.ZERO), path.first())
        assertEquals(IntervalNode(8, coordinate), path.last())
        assertTrue(path.zipWithNext().all { (parent, child) -> parent.contains(child) })
    }

    @Test
    fun sampleRangeHasCanonicalMinimalCover() {
        val cover = BinaryIntervalTree.cover(
            BigInteger.valueOf(70),
            BigInteger.valueOf(85),
            8,
        )

        assertEquals(
            listOf(
                IntervalNode(7, BigInteger.valueOf(35)), // [70, 71]
                IntervalNode(5, BigInteger.valueOf(9)),  // [72, 79]
                IntervalNode(6, BigInteger.valueOf(20)), // [80, 83]
                IntervalNode(7, BigInteger.valueOf(42)), // [84, 85]
            ),
            cover,
        )

        val recordPath = BinaryIntervalTree.path(BigInteger.valueOf(73), 8)
        assertEquals(setOf(IntervalNode(5, BigInteger.valueOf(9))), recordPath.intersect(cover.toSet()))
        assertTrue(BinaryIntervalTree.path(BigInteger.valueOf(103), 8).intersect(cover.toSet()).isEmpty())
    }

    @Test
    fun allSmallUnsignedRangesHaveExactDisjointAntichainCovers() {
        for (bits in 1..8) {
            val domainSize = 1 shl bits
            for (lower in 0 until domainSize) {
                for (upper in lower until domainSize) {
                    val cover = BinaryIntervalTree.cover(lower.bigInteger(), upper.bigInteger(), bits)

                    assertExactCover(cover, lower, upper, bits)
                    assertAntichain(cover)
                    assertNoMergeableSiblingPair(cover)
                }
            }
        }
    }

    @Test
    fun pathAndCoverHandleThirtyTwoBitBoundaries() {
        val bits = 32
        val maximum = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE)

        val zeroPath = BinaryIntervalTree.path(BigInteger.ZERO, bits)
        val maximumPath = BinaryIntervalTree.path(maximum, bits)
        assertEquals(bits + 1, zeroPath.size)
        assertEquals(IntervalNode(bits, BigInteger.ZERO), zeroPath.last())
        assertEquals(IntervalNode(bits, maximum), maximumPath.last())

        assertEquals(
            listOf(IntervalNode(0, BigInteger.ZERO)),
            BinaryIntervalTree.cover(BigInteger.ZERO, maximum, bits),
        )

        val signBoundary = BigInteger.ONE.shiftLeft(31)
        val cover = BinaryIntervalTree.cover(signBoundary.subtract(BigInteger.ONE), signBoundary, bits)
        assertEquals(
            listOf(
                IntervalNode(bits, signBoundary.subtract(BigInteger.ONE)),
                IntervalNode(bits, signBoundary),
            ),
            cover,
        )
    }

    @Test
    fun pathAndCoverHandleFullUnsignedSixtyFourBitDomain() {
        val bits = 64
        val maximum = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE)

        val path = BinaryIntervalTree.path(maximum, bits)
        assertEquals(bits + 1, path.size)
        assertEquals(IntervalNode(0, BigInteger.ZERO), path.first())
        assertEquals(IntervalNode(bits, maximum), path.last())

        assertEquals(
            listOf(IntervalNode(0, BigInteger.ZERO)),
            BinaryIntervalTree.cover(BigInteger.ZERO, maximum, bits),
        )

        val tail = BinaryIntervalTree.cover(maximum.subtract(BigInteger.valueOf(3)), maximum, bits)
        assertEquals(
            listOf(IntervalNode(62, BigInteger.ONE.shiftLeft(62).subtract(BigInteger.ONE))),
            tail,
        )
        assertEquals(maximum.subtract(BigInteger.valueOf(3)), tail.single().lowerBound(bits))
        assertEquals(maximum, tail.single().upperBound(bits))
    }

    @Test
    fun singletonCoverIsTheExactLeafAndFullCoverIsTheRoot() {
        for (bits in 1..64) {
            val value = BigInteger.ONE.shiftLeft(bits - 1)
            assertEquals(
                listOf(IntervalNode(bits, value)),
                BinaryIntervalTree.cover(value, value, bits),
            )

            val maximum = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE)
            assertEquals(
                listOf(IntervalNode(0, BigInteger.ZERO)),
                BinaryIntervalTree.cover(BigInteger.ZERO, maximum, bits),
            )
        }
    }

    @Test
    fun intervalTokensAreCanonicalAndBoundsAreExact() {
        val node = IntervalNode(5, BigInteger.valueOf(9))
        assertEquals("interval:5:9", node.canonicalToken)
        assertEquals(node.canonicalToken, node.token)
        assertEquals(node.canonicalToken, node.toString())
        assertEquals(BigInteger.valueOf(72), node.lowerBound(8))
        assertEquals(BigInteger.valueOf(79), node.upperBound(8))

        assertTrue(node.contains(IntervalNode(8, BigInteger.valueOf(73))))
        assertFalse(node.contains(IntervalNode(8, BigInteger.valueOf(103))))
    }

    @Test
    fun invalidNodesCoordinatesRangesAndWidthsAreRejected() {
        assertFailsWith<IllegalArgumentException> { IntervalNode(-1, BigInteger.ZERO) }
        assertFailsWith<IllegalArgumentException> { IntervalNode(65, BigInteger.ZERO) }
        assertFailsWith<IllegalArgumentException> { IntervalNode(0, BigInteger.ONE) }
        assertFailsWith<IllegalArgumentException> { IntervalNode(3, BigInteger.valueOf(8)) }
        assertFailsWith<IllegalArgumentException> { IntervalNode(3, BigInteger.valueOf(-1)) }

        listOf(0, 65).forEach { bits ->
            assertFailsWith<IllegalArgumentException> { BinaryIntervalTree.path(BigInteger.ZERO, bits) }
            assertFailsWith<IllegalArgumentException> {
                BinaryIntervalTree.cover(BigInteger.ZERO, BigInteger.ZERO, bits)
            }
        }

        assertFailsWith<IllegalArgumentException> { BinaryIntervalTree.path(BigInteger.valueOf(-1), 8) }
        assertFailsWith<IllegalArgumentException> { BinaryIntervalTree.path(BigInteger.valueOf(256), 8) }
        assertFailsWith<IllegalArgumentException> {
            BinaryIntervalTree.cover(BigInteger.valueOf(-1), BigInteger.ZERO, 8)
        }
        assertFailsWith<IllegalArgumentException> {
            BinaryIntervalTree.cover(BigInteger.ZERO, BigInteger.valueOf(256), 8)
        }
        assertFailsWith<IllegalArgumentException> {
            BinaryIntervalTree.cover(BigInteger.TEN, BigInteger.ONE, 8)
        }
        assertFailsWith<IllegalArgumentException> { IntervalNode(5, BigInteger.ONE).lowerBound(4) }
    }

    private fun assertExactCover(
        cover: List<IntervalNode>,
        expectedLower: Int,
        expectedUpper: Int,
        bits: Int,
    ) {
        assertTrue(cover.isNotEmpty())

        var nextExpected = expectedLower.bigInteger()
        cover.forEach { node ->
            val lower = node.lowerBound(bits)
            val upper = node.upperBound(bits)
            assertEquals(nextExpected, lower, "gap or overlap before $node")
            assertTrue(lower <= upper)
            nextExpected = upper.add(BigInteger.ONE)
        }
        assertEquals(expectedUpper.bigInteger().add(BigInteger.ONE), nextExpected)
    }

    private fun assertAntichain(nodes: List<IntervalNode>) {
        nodes.forEachIndexed { firstIndex, first ->
            nodes.forEachIndexed { secondIndex, second ->
                if (firstIndex != secondIndex) {
                    assertFalse(first.contains(second), "$first contains $second")
                }
            }
        }
    }

    private fun assertNoMergeableSiblingPair(nodes: List<IntervalNode>) {
        val nodeSet = nodes.toSet()
        nodes.filter { it.depth > 0 }.forEach { node ->
            val sibling = IntervalNode(node.depth, node.prefix.xor(BigInteger.ONE))
            assertFalse(sibling in nodeSet, "$node and $sibling should have been replaced by their parent")
        }
    }

    private fun Int.bigInteger(): BigInteger = BigInteger.valueOf(toLong())
}
