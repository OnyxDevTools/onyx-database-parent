package com.onyx.cloud.api

import kotlin.test.*
import java.math.BigInteger

/**
 * Exercises pagination helpers provided by [QueryResultsImpl].
 */
class QueryResultsImplTest {
    data class Record(val num: Int)

    @Test
    fun iteratesAcrossPagesAndAggregates() {
        val page2 = QueryResultsImpl(listOf(Record(3), Record(4)))
        val page1 = QueryResultsImpl(
            records = listOf(Record(1), Record(2)),
            nextPage = "token",
            fetcher = { token ->
                assertEquals("token", token)
                page2
            }
        )

        val all = page1.getAllRecords()
        assertEquals(listOf(Record(1), Record(2), Record(3), Record(4)), all)

        val values = page1.values("num")
        assertEquals(listOf(1, 2, 3, 4), values)

        assertEquals(4, page1.maxOfInt { it.num })
        assertEquals(1, page1.minOfInt { it.num })
        assertEquals(10, page1.sumOfInt { it.num })

        assertEquals(BigInteger.valueOf(10), page1.sumOfBigInt { BigInteger.valueOf(it.num.toLong()) })

        val pages = mutableListOf<List<Record>>()
        page1.forEachPage { page -> pages.add(page); true }
        assertEquals(2, pages.size)
    }

    @Test
    fun firstThrowsWhenEmpty() {
        val empty = QueryResultsImpl(emptyList<Record>())
        assertFailsWith<IllegalStateException> { empty.first() }
        assertNull(empty.firstOrNull())
    }

    @Test
    fun filterAndMapAcrossPages() {
        val page2 = QueryResultsImpl(listOf(Record(3), Record(4)))
        val page1 = QueryResultsImpl(
            records = listOf(Record(1), Record(2)),
            nextPage = "token",
            fetcher = { page2 }
        )
        assertEquals(listOf(Record(3), Record(4)), page1.filterAll { it.num > 2 })
        assertEquals(listOf(2, 4, 6, 8), page1.mapAll { it.num * 2 })
    }

    @Test
    fun valuesExtractFromMaps() {
        val page = QueryResultsImpl(listOf(mapOf("name" to "A"), mapOf("name" to "B")))
        val names = page.values("name")
        assertEquals(listOf("A", "B"), names)
    }

    @Test
    fun forEachPageStopsWhenFalse() {
        var fetched = false
        val page1 = QueryResultsImpl(
            records = listOf(Record(1)),
            nextPage = "token",
            fetcher = { fetched = true; QueryResultsImpl(listOf(Record(2))) }
        )
        page1.forEachPage { false }
        assertFalse(fetched)
    }
}

