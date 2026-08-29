package com.onyx.persistence.query.decoding

import com.onyx.persistence.query.resolveVectorSearchQuery
import com.onyx.vector.SemanticVectorSignature
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class VectorSearchQueryMapDecodingTest {

    @Test
    fun `canonical cloud map decodes lossless hybrid query`() {
        val fingerprint = longArrayOf(0xfedc_ba98_7654_3210UL.toLong())
        val bands = SemanticVectorSignature.splitIntoFourBands(fingerprint)
        val decoded = assertNotNull(
            resolveVectorSearchQuery(
                mapOf(
                    "text" to "delta neutral options",
                    "semantic" to mapOf(
                        "calibrationId" to Long.MAX_VALUE.toString(),
                        "bucketId" to 6.0,
                        "cells" to listOf(1.0, 2.0),
                        "cellCounts" to listOf(4.0, 4.0),
                        "fingerprint" to fingerprint.map(::wireWord),
                        "bands" to bands.map(::wireWord),
                        "boundaryConfidence" to 0.75
                    ),
                    "minScore" to 0.42,
                    "nearbyBucketRadius" to 2.0,
                    "maxCandidates" to 321.0,
                    "requireAllTerms" to false
                )
            )
        )

        assertEquals("delta neutral options", decoded.text)
        assertEquals(0.42f, decoded.minScore)
        assertEquals(2, decoded.nearbyBucketRadius)
        assertEquals(321, decoded.maxCandidates)
        assertEquals(false, decoded.requireAllTerms)
        val signature = assertNotNull(decoded.semantic)
        assertEquals(Long.MAX_VALUE, signature.calibrationId)
        assertEquals(6, signature.bucketId)
        assertContentEquals(intArrayOf(1, 2), signature.cells)
        assertContentEquals(intArrayOf(4, 4), signature.cellCounts)
        assertContentEquals(fingerprint, signature.fingerprint)
        assertContentEquals(bands, signature.bands)
        assertEquals(0.75f, signature.boundaryConfidence)
    }

    @Test
    fun `snake case semantic map computes omitted four bands`() {
        val fingerprint = longArrayOf(0x0123_4567_89ab_cdefL)
        val decoded = assertNotNull(
            resolveVectorSearchQuery(
                mapOf(
                    "semantic_signature" to mapOf(
                        "calibration_id" to "73",
                        "bucket_id" to 1,
                        "cells" to intArrayOf(1),
                        "cell_counts" to intArrayOf(2),
                        "fingerprint_words" to fingerprint.map(::wireWord),
                        "boundary_confidence" to "1.0"
                    ),
                    "nearby_bucket_radius" to "0",
                    "max_candidates" to "17",
                    "require_all_terms" to "true"
                )
            )
        )

        assertEquals(0, decoded.nearbyBucketRadius)
        assertEquals(17, decoded.maxCandidates)
        assertContentEquals(
            SemanticVectorSignature.splitIntoFourBands(fingerprint),
            assertNotNull(decoded.semantic).bands
        )
    }

    @Test
    fun `legacy full text object remains compatible`() {
        val decoded = assertNotNull(
            resolveVectorSearchQuery(mapOf("queryText" to "storm warning", "minScore" to 0.5))
        )
        assertEquals("storm warning", decoded.text)
        assertEquals(0.5f, decoded.minScore)
        assertEquals(null, decoded.semantic)
    }

    @Test
    fun `unsafe long json number is rejected instead of silently rounded`() {
        val error = assertFailsWith<IllegalArgumentException> {
            resolveVectorSearchQuery(
                semanticMap(
                    calibrationId = 9_007_199_254_740_992.0,
                    bands = null
                )
            )
        }
        assertEquals(true, error.message?.contains("JSON safe-integer range") == true)
    }

    @Test
    fun `eight band payload is explicitly rejected by current index contract`() {
        val error = assertFailsWith<IllegalArgumentException> {
            resolveVectorSearchQuery(
                semanticMap(
                    calibrationId = "73",
                    bands = List(8) { "0x0000000000000000" }
                )
            )
        }
        assertEquals(true, error.message?.contains("exactly") == true)
        assertEquals(true, error.message?.contains("band", ignoreCase = true) == true)
    }

    @Test
    fun `unknown object is never converted to map toString search text`() {
        assertFailsWith<IllegalArgumentException> {
            resolveVectorSearchQuery(mapOf("unexpected" to "value"))
        }
    }

    private fun semanticMap(calibrationId: Any, bands: List<String>?): Map<String, Any?> {
        val fingerprint = longArrayOf(0L)
        return mapOf(
            "semantic" to mapOf(
                "calibrationId" to calibrationId,
                "bucketId" to 0,
                "cells" to listOf(0),
                "cellCounts" to listOf(2),
                "fingerprint" to fingerprint.map(::wireWord),
                "bands" to bands,
                "boundaryConfidence" to 1.0
            ).filterValues { it != null }
        )
    }

    private fun wireWord(value: Long): String =
        "0x" + java.lang.Long.toUnsignedString(value, 16).padStart(16, '0')
}
