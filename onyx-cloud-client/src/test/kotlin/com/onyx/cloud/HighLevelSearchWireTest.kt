package com.onyx.cloud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.onyx.cloud.api.FULL_TEXT_ATTRIBUTE
import com.onyx.cloud.api.FullTextSearchResult
import com.onyx.cloud.api.MAX_SEARCH_CANDIDATES
import com.onyx.cloud.api.QueryCriteria
import com.onyx.cloud.api.QueryCriteriaOperator
import com.onyx.cloud.api.SearchMatch
import com.onyx.cloud.api.SearchMode
import com.onyx.cloud.api.SearchOptions
import com.onyx.cloud.api.eq
import com.onyx.cloud.api.search
import com.onyx.cloud.impl.ConditionBuilderImpl
import com.onyx.cloud.impl.OnyxClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HighLevelSearchWireTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OnyxClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OnyxClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            databaseId = "db",
            apiKey = "key",
            apiSecret = "secret",
        )
    }

    @AfterTest
    fun tearDown() = server.shutdown()

    @Test
    fun semanticSearchUsesCanonicalFailClosedWireShape() {
        server.enqueue(emptyQueryPage())

        client.from<HighLevelSearchEntity>()
            .search(
                "how do i calculate cost per horse",
                SearchOptions(mode = SearchMode.SEMANTIC),
            )
            .list<HighLevelSearchEntity>()

        val criteria = requestCriteria()
        assertEquals(FULL_TEXT_ATTRIBUTE, criteria["field"].asString)
        assertEquals(QueryCriteriaOperator.SEARCH.name, criteria["operator"].asString)
        val value = criteria.objectAt("value")
        assertEquals("how do i calculate cost per horse", value["text"].asString)
        assertEquals("semantic", value["mode"].asString)
        assertEquals("any", value["match"].asString)
        assertTrue(value["minScore"].isJsonNull)
        assertEquals(1_000, value["maxCandidates"].asInt)
    }

    @Test
    fun defaultSearchOptionsUseCanonicalHybridWireShape() {
        server.enqueue(emptyQueryPage())

        client.from<HighLevelSearchEntity>()
            .search("how do i calculate cost per horse", SearchOptions())
            .list<HighLevelSearchEntity>()

        val criteria = requestCriteria()
        assertEquals(FULL_TEXT_ATTRIBUTE, criteria["field"].asString)
        assertEquals(QueryCriteriaOperator.SEARCH.name, criteria["operator"].asString)
        val value = criteria.objectAt("value")
        assertEquals("how do i calculate cost per horse", value["text"].asString)
        assertEquals("hybrid", value["mode"].asString)
        assertEquals("any", value["match"].asString)
        assertTrue(value["minScore"].isJsonNull)
        assertEquals(1_000, value["maxCandidates"].asInt)
    }

    @Test
    fun databaseWideSearchDoesNotInheritDefaultPartitionButTableSearchDoes() {
        val partitionedClient = OnyxClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            databaseId = "db",
            apiKey = "key",
            apiSecret = "secret",
            defaultPartition = "tenant-a",
        )

        server.enqueue(emptyQueryPage())
        partitionedClient.search("cost per horse", SearchOptions()).list<FullTextSearchResult>()

        val allTablesRequest = server.takeRequest()
        assertEquals(null, allTablesRequest.requestUrl?.queryParameter("partition"))
        assertTrue(
            !JsonParser.parseString(allTablesRequest.body.readUtf8()).asJsonObject.has("partition")
        )

        server.enqueue(emptyQueryPage())
        partitionedClient.search("cost per horse").list<FullTextSearchResult>()

        val legacyAllTablesRequest = server.takeRequest()
        assertEquals(null, legacyAllTablesRequest.requestUrl?.queryParameter("partition"))
        assertTrue(
            !JsonParser.parseString(legacyAllTablesRequest.body.readUtf8())
                .asJsonObject.has("partition")
        )

        server.enqueue(emptyQueryPage())
        partitionedClient.from<HighLevelSearchEntity>()
            .search("cost per horse", SearchOptions())
            .list<HighLevelSearchEntity>()

        val tableRequest = server.takeRequest()
        assertEquals("tenant-a", tableRequest.requestUrl?.queryParameter("partition"))
        assertEquals(
            "tenant-a",
            JsonParser.parseString(tableRequest.body.readUtf8())
                .asJsonObject["partition"].asString,
        )
    }

    @Test
    fun lexicalAnySearchComposesWithOrdinaryFilters() {
        server.enqueue(emptyQueryPage())

        client.from<HighLevelSearchEntity>()
            .where("status" eq "active")
            .search(
                "how do i calculate cost per horse",
                SearchOptions(
                    mode = SearchMode.LEXICAL,
                    match = SearchMatch.ANY,
                    minScore = 0.4f,
                    maxCandidates = 500,
                ),
            )
            .list<HighLevelSearchEntity>()

        val conditions = JsonParser.parseString(server.takeRequest().body.readUtf8())
            .asJsonObject.objectAt("conditions")
        assertEquals("CompoundCondition", conditions["conditionType"].asString)
        val searchCriteria = conditions["conditions"].asJsonArray[1]
            .asJsonObject.objectAt("criteria")
        assertEquals(QueryCriteriaOperator.SEARCH.name, searchCriteria["operator"].asString)
        val value = searchCriteria.objectAt("value")
        assertEquals("lexical", value["mode"].asString)
        assertEquals("any", value["match"].asString)
        assertEquals(0.4f, value["minScore"].asFloat)
        assertEquals(500, value["maxCandidates"].asInt)
    }

    @Test
    fun searchOptionsValidateBeforeTransportAndSearchIsReadOnly() {
        assertFailsWith<IllegalArgumentException> {
            SearchOptions(mode = SearchMode.HYBRID, minScore = Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            SearchOptions(mode = SearchMode.HYBRID, maxCandidates = MAX_SEARCH_CANDIDATES + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            client.from<HighLevelSearchEntity>()
                .search(" ", SearchOptions(mode = SearchMode.SEMANTIC))
        }

        val delete = client.from<HighLevelSearchEntity>()
            .search("cost per horse", SearchOptions(mode = SearchMode.HYBRID))
        assertFailsWith<IllegalStateException> { delete.delete() }

        val update = client.from<HighLevelSearchEntity>()
            .search("cost per horse", SearchOptions(mode = SearchMode.HYBRID))
            .setUpdates("status" to "archived")
        assertFailsWith<IllegalStateException> { update.update() }
    }

    @Test
    fun searchCannotOpenLiveStreamAndDoesNotSendARequest() {
        val query = client.from<HighLevelSearchEntity>()
            .search("cost per horse", SearchOptions(mode = SearchMode.HYBRID))

        val error = assertFailsWith<IllegalStateException> {
            query.stream<HighLevelSearchEntity>(
                includeQueryResults = false,
                keepAlive = false,
            )
        }

        assertTrue(error.message.orEmpty().contains("live streams"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun searchRejectsDuplicatesAndOtherFullTextCriteriaRegardlessOfCallOrder() {
        val options = SearchOptions(mode = SearchMode.LEXICAL)

        val wrongFieldError = assertFailsWith<IllegalArgumentException> {
            client.from<HighLevelSearchEntity>().where(
                ConditionBuilderImpl(
                    QueryCriteria(
                        field = "status",
                        operator = QueryCriteriaOperator.SEARCH,
                        value = emptyMap<String, Any?>(),
                    ),
                ),
            )
        }
        assertTrue(wrongFieldError.message.orEmpty().contains(FULL_TEXT_ATTRIBUTE))

        val duplicateError = assertFailsWith<IllegalArgumentException> {
            client.from<HighLevelSearchEntity>()
                .search("first search", options)
                .search("second search", options)
        }
        assertTrue(duplicateError.message.orEmpty().contains("only one SEARCH"))

        val highLevelFirstError = assertFailsWith<IllegalArgumentException> {
            client.from<HighLevelSearchEntity>()
                .search("high-level", options)
                .search("legacy")
        }
        assertTrue(highLevelFirstError.message.orEmpty().contains("another full-text"))

        val legacyFirstError = assertFailsWith<IllegalArgumentException> {
            client.from<HighLevelSearchEntity>()
                .search("legacy")
                .search("high-level", options)
        }
        assertTrue(legacyFirstError.message.orEmpty().contains("another full-text"))

        val nestedConflict = search("legacy").and(search("high-level", options))
        val nestedError = assertFailsWith<IllegalArgumentException> {
            client.from<HighLevelSearchEntity>().where(nestedConflict)
        }
        assertTrue(nestedError.message.orEmpty().contains("another full-text"))

        assertEquals(0, server.requestCount)
    }

    @Test
    fun legacySearchWireRemainsUnchanged() {
        server.enqueue(emptyQueryPage())
        client.from<HighLevelSearchEntity>().search("cost per horse").list<HighLevelSearchEntity>()

        val criteria = requestCriteria()
        assertEquals(QueryCriteriaOperator.MATCHES.name, criteria["operator"].asString)
        val value = criteria.objectAt("value")
        assertEquals("cost per horse", value["queryText"].asString)
        assertTrue(value["minScore"].isJsonNull)
    }

    private fun requestCriteria(): JsonObject = JsonParser.parseString(server.takeRequest().body.readUtf8())
        .asJsonObject.objectAt("conditions").objectAt("criteria")

    private fun emptyQueryPage(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody("""{"records":[],"totalResults":0}""")

    private fun JsonObject.objectAt(name: String): JsonObject = get(name).asJsonObject
}

private data class HighLevelSearchEntity(
    val id: String = "",
    val status: String = "",
)
