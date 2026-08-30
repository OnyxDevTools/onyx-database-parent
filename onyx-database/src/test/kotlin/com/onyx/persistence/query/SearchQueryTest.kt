package com.onyx.persistence.query

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchQueryTest {
    @Test
    fun `wire maps decode aliases and defaults into the canonical contract`() {
        val query = resolveSearchQuery(
            linkedMapOf<String, Any?>().apply {
                put("query_text", "cost per horse")
                put("mode", "both")
                put("min_score", 0.4)
                put("max_candidates", 20)
            },
        )

        assertEquals("cost per horse", query.text)
        assertEquals(SearchMode.HYBRID, query.mode)
        assertEquals(SearchMatch.ANY, query.match)
        assertEquals(0.4f, query.minScore)
        assertEquals(20, query.maxCandidates)
    }

    @Test
    fun `wire maps reject unknown and ambiguous fields`() {
        assertFailsWith<IllegalArgumentException> {
            resolveSearchQuery(
                linkedMapOf<String, Any?>().apply {
                    put("text", "cost per horse")
                    put("mode", "lexical")
                    put("maxCandidate", 20)
                },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            resolveSearchQuery(
                linkedMapOf<String, Any?>().apply {
                    put("text", "cost per horse")
                    put("queryText", "different text")
                    put("mode", "lexical")
                },
            )
        }
    }
}
