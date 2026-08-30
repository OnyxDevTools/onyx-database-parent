package com.onyx.persistence.query

import org.junit.Test
import kotlin.test.assertEquals

class QueryCriteriaOperatorCompatibilityTest {
    @Test
    fun `candidate operator ordinals remain wire compatible`() {
        assertEquals(22, QueryCriteriaOperator.CANDIDATES.ordinal)
        assertEquals(23, QueryCriteriaOperator.SEARCH_CANDIDATES.ordinal)
        assertEquals(24, QueryCriteriaOperator.HNSW_CANDIDATES.ordinal)
        assertEquals(25, QueryCriteriaOperator.SEARCH.ordinal)
    }
}
