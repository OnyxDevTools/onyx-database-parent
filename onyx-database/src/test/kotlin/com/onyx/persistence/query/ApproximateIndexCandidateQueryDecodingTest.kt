package com.onyx.persistence.query.decoding

import com.onyx.persistence.query.ApproximateIndexCandidateQuery
import com.onyx.persistence.query.MAX_APPROXIMATE_INDEX_CANDIDATES
import com.onyx.persistence.query.MAX_APPROXIMATE_INDEX_ROUTE_VALUES
import com.onyx.persistence.query.MAX_VECTOR_SEARCH_CANDIDATES
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.resolveApproximateIndexCandidateQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApproximateIndexCandidateQueryDecodingTest {

    @Test
    fun `generic cloud map decodes bounded IN route`() {
        val decoded = resolveApproximateIndexCandidateQuery(
            mapOf(
                "values" to listOf(6.0, 7.0),
                "maxCandidates" to 17.0
            )
        )

        assertEquals(listOf(6.0, 7.0), decoded.values)
        assertEquals(17, decoded.maxCandidates)
    }

    @Test
    fun `snake case scalar alias decodes bounded equality route`() {
        val decoded = resolveApproximateIndexCandidateQuery(
            mapOf("value" to "active", "max_candidates" to "9")
        )

        assertEquals(listOf("active"), decoded.values)
        assertEquals(9, decoded.maxCandidates)
    }

    @Test
    fun `route count is independent from candidate budget`() {
        val routes = (1..128).toList()

        val query = ApproximateIndexCandidateQuery(routes, maxCandidates = 32)
        val decoded = resolveApproximateIndexCandidateQuery(
            mapOf("values" to routes, "maxCandidates" to 32.0)
        )

        assertEquals(routes, query.values)
        assertEquals(32, query.maxCandidates)
        assertEquals(query, decoded)
    }

    @Test
    fun `invalid route and work bounds are rejected separately`() {
        assertFailsWith<IllegalArgumentException> {
            ApproximateIndexCandidateQuery(emptyList(), 1)
        }
        assertFailsWith<IllegalArgumentException> {
            ApproximateIndexCandidateQuery(
                List(MAX_APPROXIMATE_INDEX_ROUTE_VALUES + 1) { it },
                maxCandidates = 1
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ApproximateIndexCandidateQuery(listOf(1), MAX_APPROXIMATE_INDEX_CANDIDATES + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            resolveApproximateIndexCandidateQuery(mapOf("unexpected" to true))
        }
    }

    @Test
    fun `semantic query also enforces its public hard candidate ceiling`() {
        assertFailsWith<IllegalArgumentException> {
            VectorSearchQuery(
                text = "bounded",
                maxCandidates = MAX_VECTOR_SEARCH_CANDIDATES + 1
            )
        }
    }

}
