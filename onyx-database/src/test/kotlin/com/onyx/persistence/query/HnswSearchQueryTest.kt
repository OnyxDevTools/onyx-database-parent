package com.onyx.persistence.query.decoding

import com.onyx.persistence.query.HNSW_QUERY_FORMAT_VERSION
import com.onyx.persistence.query.HnswSearchQuery
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.resolveHnswSearchQuery
import com.onyx.persistence.query.resolveVectorSearchQuery

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HnswSearchQueryTest {

    @Test
    fun `typed reflective transport values are fully revalidated before use`() {
        val hnsw = HnswSearchQuery(
            calibrationId = 91L,
            vector = floatArrayOf(1f, 0f),
            maxCandidates = 2,
            efSearch = 8,
        )
        HnswSearchQuery::class.java.getDeclaredField("efSearch").apply {
            isAccessible = true
            setInt(hnsw, 0)
        }
        assertFailsWith<IllegalArgumentException> { resolveHnswSearchQuery(hnsw) }

        val lexical = VectorSearchQuery(text = "transport")
        VectorSearchQuery::class.java.getDeclaredField("maxCandidates").apply {
            isAccessible = true
            setInt(lexical, 0)
        }
        assertFailsWith<IllegalArgumentException> { resolveVectorSearchQuery(lexical) }
    }

    @Test
    fun `typed query survives java serialization without derived quantizer state`() {
        val original = HnswSearchQuery(
            calibrationId = 73L,
            vector = floatArrayOf(1f, -2f, 3f),
            maxCandidates = 7,
            efSearch = 31,
            minScore = 0.25f,
        )
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(original) }
            output.toByteArray()
        }
        val decoded = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as HnswSearchQuery
        }

        assertEquals(73L, decoded.calibrationId)
        assertContentEquals(floatArrayOf(1f, -2f, 3f), decoded.vector)
        assertEquals(7, decoded.maxCandidates)
        assertEquals(31, decoded.efSearch)
        assertEquals(0.25f, decoded.minScore)
        assertEquals(HNSW_QUERY_FORMAT_VERSION, decoded.formatVersion)
        assertEquals(3, decoded.quantizedVector.dimensions)
    }

    @Test
    fun `generic map accepts snake case and safe long strings`() {
        val decoded = resolveHnswSearchQuery(
            mapOf(
                "format_version" to 1.0,
                "calibration_id" to Long.MAX_VALUE.toString(),
                "embedding" to listOf(1.0, 0.0, -1.0),
                "max_candidates" to 9.0,
                "ef_search" to 27.0,
                "min_score" to -0.5,
            )
        )

        assertEquals(Long.MAX_VALUE, decoded.calibrationId)
        assertContentEquals(floatArrayOf(1f, 0f, -1f), decoded.vector)
        assertEquals(9, decoded.maxCandidates)
        assertEquals(27, decoded.efSearch)
        assertEquals(-0.5f, decoded.minScore)
    }

    @Test
    fun `validation enforces format vector and work bounds`() {
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(1L, floatArrayOf(), formatVersion = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(1L, floatArrayOf(0f, 0f))
        }
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(1L, floatArrayOf(Float.NaN))
        }
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(0L, floatArrayOf(1f))
        }
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(1L, floatArrayOf(1f), maxCandidates = 5, efSearch = 4)
        }
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(1L, floatArrayOf(1f), formatVersion = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            resolveHnswSearchQuery(mapOf("calibrationId" to 1, "vector" to listOf(1), "formatVersion" to 2))
        }
    }

    @Test
    fun `operator inverse rejects approximate negation`() {
        assertFailsWith<UnsupportedOperationException> {
            QueryCriteriaOperator.HNSW_CANDIDATES.inverse
        }
    }

    @Test
    fun `schema free vectors are bounded before materialization`() {
        assertFailsWith<IllegalArgumentException> {
            resolveHnswSearchQuery(
                mapOf("calibrationId" to 1L, "vector" to FloatArray(16_385) { 1f })
            )
        }

        var consumed = 0
        val unbounded = Iterable<Number> {
            object : Iterator<Number> {
                override fun hasNext(): Boolean = true
                override fun next(): Number = 1.also { consumed++ }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            resolveHnswSearchQuery(mapOf("calibrationId" to 1L, "vector" to unbounded))
        }
        assertEquals(16_384, consumed)
    }
}
