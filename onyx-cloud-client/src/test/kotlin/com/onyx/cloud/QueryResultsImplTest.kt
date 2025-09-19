package com.onyx.cloud

import com.google.gson.JsonParser
import com.onyx.cloud.impl.OnyxClient
import com.onyx.cloud.impl.QueryBuilder
import com.onyx.cloud.impl.QueryResultsImpl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.math.BigInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueryResultsImplTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OnyxClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        val base = server.url("/").toString().trimEnd('/')
        client = OnyxClient(baseUrl = base, databaseId = "db", apiKey = "key", apiSecret = "secret")
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun recordsAreParsedWhenClassTypeIsAvailable() {
        val json = """
            [
              {"id": 1, "name": "alpha"},
              {"id": 2, "name": "beta"}
            ]
        """.trimIndent()
        val result = QueryResultsImpl<TestEntity>(recordText = JsonParser.parseString(json).asJsonArray)
        result.classType = TestEntity::class

        val records = result.records

        assertEquals(2, records.size)
        assertEquals(TestEntity(1, "alpha"), records.first())
        assertFalse(result.isEmpty())
    }

    @Test
    fun recordsReturnEmptyListWhenClassTypeMissing() {
        val json = """
            [
              {"id": 1, "name": "alpha"}
            ]
        """.trimIndent()
        val result = QueryResultsImpl<TestEntity>(recordText = JsonParser.parseString(json).asJsonArray)

        assertTrue(result.records.isEmpty())
    }

    @Test
    fun forEachOnPageIteratesCurrentRecordsOnly() {
        val json = """
            [
              {"id": 1, "value": 10},
              {"id": 2, "value": 20}
            ]
        """.trimIndent()
        val result = QueryResultsImpl<PageEntity>(recordText = JsonParser.parseString(json).asJsonArray, nextPage = "token")
        result.classType = PageEntity::class

        val visited = mutableListOf<Int>()
        result.forEachOnPage { visited += it.id }

        assertEquals(listOf(1, 2), visited)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun forEachPageFetchesPagesUntilActionStops() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 1, "value": 10},
                        {"id": 2, "value": 20}
                      ],
                      "nextPage": "token-2",
                      "totalResults": 4
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 3, "value": 30}
                      ],
                      "nextPage": "token-3",
                      "totalResults": 4
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 4, "value": 40}
                      ],
                      "totalResults": 4
                    }
                    """.trimIndent()
                )
        )

        val results = client.from<PageEntity>().list<PageEntity>() as QueryResultsImpl<PageEntity>

        val collected = mutableListOf<List<PageEntity>>()
        results.forEachPage { page ->
            collected += page
            collected.size < 2
        }

        assertEquals(2, collected.size)
        assertEquals(listOf(1, 2), collected[0].map(PageEntity::id))
        assertEquals(listOf(3), collected[1].map(PageEntity::id))

        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()
        assertTrue(firstRequest.path?.contains("nextPage") != true)
        assertTrue(secondRequest.path?.contains("nextPage=token-2") == true)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun forEachStopsIterationAcrossPages() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 1, "value": 10},
                        {"id": 2, "value": 20}
                      ],
                      "nextPage": "token-2"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 3, "value": 30},
                        {"id": 4, "value": 40}
                      ],
                      "nextPage": "token-3"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 5, "value": 50}
                      ]
                    }
                    """.trimIndent()
                )
        )

        val results = client.from<PageEntity>().list<PageEntity>() as QueryResultsImpl<PageEntity>

        assertEquals("token-2", results.nextPage)
        val visited = mutableListOf<Int>()
        val stopAfterFour: (PageEntity) -> Boolean = { entity ->
            visited += entity.id
            entity.id < 4
        }
        results.forEach(stopAfterFour)

        assertEquals(listOf(1, 2, 3, 4), visited)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun forEachAllStopsWhenActionReturnsFalse() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 1, "value": 10},
                        {"id": 2, "value": 20}
                      ],
                      "nextPage": "token-2"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 3, "value": 30},
                        {"id": 4, "value": 40}
                      ],
                      "nextPage": "token-3"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 5, "value": 50}
                      ]
                    }
                    """.trimIndent()
                )
        )

        val results = client.from<PageEntity>().list<PageEntity>() as QueryResultsImpl<PageEntity>

        val visited = mutableListOf<Int>()
        results.forEachAll { entity ->
            visited += entity.id
            entity.id < 3
        }

        assertEquals(listOf(1, 2, 3), visited)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun getAllRecordsCombinesPages() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 1, "value": 10},
                        {"id": 2, "value": 20}
                      ],
                      "nextPage": "token-2"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "records": [
                        {"id": 3, "value": 30}
                      ]
                    }
                    """.trimIndent()
                )
        )

        val results = client.from<PageEntity>().list<PageEntity>() as QueryResultsImpl<PageEntity>

        val all = results.getAllRecords()

        assertEquals(listOf(1, 2, 3), all.map(PageEntity::id))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aggregationFunctionsOperateOnAllRecords() {
        val json = """
            [
              {"name": "one", "doubleMetric": 10.5, "floatMetric": 1.5, "intMetric": 7, "longMetric": 15, "bigMetric": 5},
              {"name": "two", "doubleMetric": 20.0, "floatMetric": 2.0, "intMetric": 3, "longMetric": 25, "bigMetric": 9}
            ]
        """.trimIndent()
        val results = QueryResultsImpl<MetricEntity>(recordText = JsonParser.parseString(json).asJsonArray)
        results.classType = MetricEntity::class
        results.query = client.from<MetricEntity>() as QueryBuilder

        assertEquals(20.0, results.maxOfDouble { it.doubleMetric })
        assertEquals(10.5, results.minOfDouble { it.doubleMetric })
        assertEquals(30.5, results.sumOfDouble { it.doubleMetric })

        assertEquals(2.0f, results.maxOfFloat { it.floatMetric })
        assertEquals(1.5f, results.minOfFloat { it.floatMetric })
        assertEquals(3.5f, results.sumOfFloat { it.floatMetric })

        assertEquals(7, results.maxOfInt { it.intMetric })
        assertEquals(3, results.minOfInt { it.intMetric })
        assertEquals(10, results.sumOfInt { it.intMetric })

        assertEquals(25L, results.maxOfLong { it.longMetric })
        assertEquals(15L, results.minOfLong { it.longMetric })
        assertEquals(40L, results.sumOfLong { it.longMetric })

        assertEquals(BigInteger.valueOf(14), results.sumOfBigInt { it.bigMetric })
        assertEquals(listOf("two"), results.filterAll { it.intMetric < 5 }.map(MetricEntity::name))
        assertEquals(listOf("one", "two"), results.mapAll(MetricEntity::name))
        assertEquals(listOf("one", "two"), results.values("name"))
    }

    @Test
    fun maxAggregationReturnsDefaultsForEmptyResults() {
        val results = QueryResultsImpl<MetricEntity>(recordText = JsonParser.parseString("[]").asJsonArray)
        results.classType = MetricEntity::class
        results.query = client.from<MetricEntity>() as QueryBuilder

        assertTrue(results.maxOfDouble { it.doubleMetric }.isNaN())
        assertTrue(results.minOfDouble { it.doubleMetric }.isNaN())
        assertTrue(results.maxOfFloat { it.floatMetric }.isNaN())
        assertTrue(results.minOfFloat { it.floatMetric }.isNaN())
        assertEquals(Int.MIN_VALUE, results.maxOfInt { it.intMetric })
        assertEquals(Int.MAX_VALUE, results.minOfInt { it.intMetric })
        assertEquals(Long.MIN_VALUE, results.maxOfLong { it.longMetric })
        assertEquals(Long.MAX_VALUE, results.minOfLong { it.longMetric })
    }

    data class TestEntity(val id: Int, val name: String)
    data class PageEntity(val id: Int, val value: Int)
    data class MetricEntity(
        val name: String,
        val doubleMetric: Double,
        val floatMetric: Float,
        val intMetric: Int,
        val longMetric: Long,
        val bigMetric: BigInteger,
    )
}
