package com.onyx.cloud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.onyx.cloud.api.FULL_TEXT_ATTRIBUTE
import com.onyx.cloud.api.HNSW_QUERY_FORMAT_VERSION
import com.onyx.cloud.api.HnswSearchQuery
import com.onyx.cloud.api.MAX_HNSW_CANDIDATES
import com.onyx.cloud.api.MAX_HNSW_EF_SEARCH
import com.onyx.cloud.api.MAX_HNSW_VECTOR_DIMENSION
import com.onyx.cloud.api.QueryCriteriaOperator
import com.onyx.cloud.extensions.gson
import com.onyx.cloud.impl.OnyxClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HnswCandidateWireTest {

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
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun nativeCandidateRequestUsesLosslessBoundedWireShapeAndPartition() {
        server.enqueue(emptyQueryPage())
        val calibrationId = Long.MIN_VALUE + 73L

        client.from<HnswWireEntity>()
            .inPartition("revision-7")
            .hnswCandidates(
                HnswSearchQuery(
                    calibrationId = calibrationId,
                    vector = floatArrayOf(0.25f, -0.5f, 0.75f),
                    maxCandidates = 40,
                    efSearch = 96,
                    minScore = 0.2f,
                )
            )
            .limit(40)
            .list<HnswWireEntity>()

        val request = server.takeRequest()
        assertEquals("revision-7", request.requestUrl?.queryParameter("partition"))
        val criteria = JsonParser.parseString(request.body.readUtf8())
            .asJsonObject.objectAt("conditions").objectAt("criteria")
        assertEquals(FULL_TEXT_ATTRIBUTE, criteria["field"].asString)
        assertEquals(QueryCriteriaOperator.HNSW_CANDIDATES.name, criteria["operator"].asString)
        val value = criteria.objectAt("value")
        assertEquals(HNSW_QUERY_FORMAT_VERSION, value["formatVersion"].asInt)
        assertEquals(calibrationId.toString(), value["calibrationId"].asString)
        assertEquals(listOf(0.25f, -0.5f, 0.75f), value["vector"].asJsonArray.map { it.asFloat })
        assertEquals(40, value["maxCandidates"].asInt)
        assertEquals(96, value["efSearch"].asInt)
        assertEquals(0.2f, value["minScore"].asFloat)
    }

    @Test
    fun wireDtoRoundTripsWithoutRoundingCalibrationId() {
        val original = HnswSearchQuery(
            calibrationId = Long.MAX_VALUE,
            vector = floatArrayOf(1f, 0f, -1f),
            maxCandidates = 17,
            efSearch = 64,
        )

        val decoded = gson.fromJson(gson.toJson(original), HnswSearchQuery::class.java)

        assertEquals(original, decoded)
        assertEquals(Long.MAX_VALUE.toString(), decoded.calibrationId)
    }

    @Test
    fun nativeCandidateBuilderRequiresSoleRoot() {
        val builder = client.from<HnswWireEntity>().search("existing clause")

        val error = assertFailsWith<IllegalArgumentException> {
            builder.hnswCandidates(HnswSearchQuery(73L, floatArrayOf(1f, 0f), 2, 8))
        }

        assertTrue(error.message.orEmpty().contains("sole root"))
    }

    @Test
    fun vectorAndWorkBoundsAreRejectedBeforeTransport() {
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(0L, floatArrayOf(1f))
        }
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(1L, floatArrayOf(0f, 0f))
        }
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(1L, FloatArray(MAX_HNSW_VECTOR_DIMENSION + 1) { 1f })
        }
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(1L, floatArrayOf(1f), maxCandidates = MAX_HNSW_CANDIDATES + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(1L, floatArrayOf(1f), maxCandidates = 2, efSearch = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            HnswSearchQuery(
                1L,
                floatArrayOf(1f),
                maxCandidates = 1,
                efSearch = MAX_HNSW_EF_SEARCH + 1,
            )
        }
    }

    private fun emptyQueryPage(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody("""{"records":[],"totalResults":0}""")

    private fun JsonObject.objectAt(name: String): JsonObject = get(name).asJsonObject
}

private data class HnswWireEntity(val id: String = "")
