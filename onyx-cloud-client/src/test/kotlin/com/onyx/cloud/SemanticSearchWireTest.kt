package com.onyx.cloud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.onyx.cloud.api.FULL_TEXT_ATTRIBUTE
import com.onyx.cloud.api.MAX_VECTOR_SEARCH_CANDIDATES
import com.onyx.cloud.api.QueryCriteriaOperator
import com.onyx.cloud.api.SemanticVectorSignature
import com.onyx.cloud.api.VectorSearchQuery
import com.onyx.cloud.api.search
import com.onyx.cloud.extensions.gson
import com.onyx.cloud.impl.OnyxClient
import com.onyx.cloud.impl.QueryCondition
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SemanticSearchWireTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OnyxClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OnyxClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            databaseId = "db",
            apiKey = "key",
            apiSecret = "secret"
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun hybridQueryUsesLosslessTypedJsonWireShape() {
        server.enqueue(emptyQueryPage())
        val fingerprint = longArrayOf(0xfedc_ba98_7654_3210UL.toLong())
        val signature = SemanticVectorSignature(
            calibrationId = Long.MAX_VALUE,
            bucketId = 6,
            cells = intArrayOf(1, 2),
            cellCounts = intArrayOf(4, 4),
            fingerprint = fingerprint,
            boundaryConfidence = 0.75f
        )

        client.from<SemanticWireEntity>()
            .search(
                VectorSearchQuery(
                    text = "delta neutral options",
                    semantic = signature,
                    minScore = 0.42f,
                    nearbyBucketRadius = 2,
                    maxCandidates = 321,
                    requireAllTerms = false
                )
            )
            .list<SemanticWireEntity>()

        val request = server.takeRequest()
        val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        val criteria = payload.objectAt("conditions").objectAt("criteria")
        assertEquals(FULL_TEXT_ATTRIBUTE, criteria["field"].asString)
        assertEquals(QueryCriteriaOperator.MATCHES.name, criteria["operator"].asString)
        val value = criteria.objectAt("value")
        assertEquals("delta neutral options", value["text"].asString)
        assertEquals(0.42f, value["minScore"].asFloat)
        assertEquals(2, value["nearbyBucketRadius"].asInt)
        assertEquals(321, value["maxCandidates"].asInt)
        assertEquals(false, value["requireAllTerms"].asBoolean)

        val semantic = value.objectAt("semantic")
        assertEquals(Long.MAX_VALUE.toString(), semantic["calibrationId"].asString)
        assertEquals(6, semantic["bucketId"].asInt)
        assertEquals(listOf(1, 2), semantic["cells"].asJsonArray.map { it.asInt })
        assertEquals(listOf(4, 4), semantic["cellCounts"].asJsonArray.map { it.asInt })
        assertEquals("0xfedcba9876543210", semantic["fingerprint"].asJsonArray[0].asString)
        assertEquals(
            listOf(
                "0x0000000000003210",
                "0x0000000000007654",
                "0x000000000000ba98",
                "0x000000000000fedc"
            ),
            semantic["bands"].asJsonArray.map { it.asString }
        )
        assertEquals(0.75f, semantic["boundaryConfidence"].asFloat)
    }

    @Test
    fun existingLexicalSearchRetainsFullTextQueryShape() {
        server.enqueue(emptyQueryPage())

        client.from<SemanticWireEntity>()
            .search("storm warning", minScore = 0.5f)
            .list<SemanticWireEntity>()

        val payload = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val value = payload.objectAt("conditions").objectAt("criteria").objectAt("value")
        assertEquals("storm warning", value["queryText"].asString)
        assertEquals(0.5f, value["minScore"].asFloat)
        assertEquals(null, value["semantic"])
    }

    @Test
    fun conditionAndBuilderConvenienceOverloadsRetainTypedSignature() {
        val signature = SemanticVectorSignature(
            calibrationId = 73L,
            bucketId = 1,
            cells = intArrayOf(1),
            cellCounts = intArrayOf(2),
            fingerprint = longArrayOf(0x0123_4567_89ab_cdefL),
            boundaryConfidence = 1f
        )

        val condition = assertIs<QueryCondition.SingleCondition>(
            search(signature, minScore = 0.8f, nearbyBucketRadius = 0, maxCandidates = 17).toCondition()
        )
        val query = assertIs<VectorSearchQuery>(condition.criteria.value)
        assertEquals(signature, query.semantic)
        assertEquals(17, query.maxCandidates)
        assertEquals(0, query.nearbyBucketRadius)
    }

    @Test
    fun wireDtosRoundTripThroughConfiguredJsonSerializer() {
        val original = VectorSearchQuery(
            text = "hybrid prompt",
            semantic = SemanticVectorSignature(
                calibrationId = 73L,
                bucketId = 1,
                cells = intArrayOf(1),
                cellCounts = intArrayOf(2),
                fingerprint = longArrayOf(0xfedc_ba98_7654_3210UL.toLong()),
                boundaryConfidence = 0.25f
            ),
            minScore = 0.33f,
            nearbyBucketRadius = 3,
            maxCandidates = 512,
            requireAllTerms = false
        )

        val decoded = gson.fromJson(gson.toJson(original), VectorSearchQuery::class.java)

        assertEquals(original, decoded)
    }

    @Test
    fun lexicalApproximateQueryUsesDedicatedRejectableWireOperator() {
        server.enqueue(emptyQueryPage())

        client.from<SemanticWireEntity>()
            .approximateSearch(
                VectorSearchQuery(
                    text = "bounded prompt recall",
                    maxCandidates = 32
                )
            )
            .list<SemanticWireEntity>()

        val criteria = JsonParser.parseString(server.takeRequest().body.readUtf8())
            .asJsonObject.objectAt("conditions").objectAt("criteria")
        assertEquals(QueryCriteriaOperator.SEARCH_CANDIDATES.name, criteria["operator"].asString)
        val value = criteria.objectAt("value")
        assertEquals("bounded prompt recall", value["text"].asString)
        assertEquals(32, value["maxCandidates"].asInt)
    }

    @Test
    fun lexicalApproximateBuilderRequiresTextOnlySoleRoot() {
        val existing = client.from<SemanticWireEntity>().search("existing exact search")
        assertFailsWith<IllegalArgumentException> {
            existing.approximateSearch("bounded recall", maxCandidates = 5)
        }

        val semantic = SemanticVectorSignature(
            calibrationId = 73L,
            bucketId = 1,
            cells = intArrayOf(1),
            cellCounts = intArrayOf(2),
            fingerprint = longArrayOf(0L)
        )
        assertFailsWith<IllegalArgumentException> {
            client.from<SemanticWireEntity>().approximateSearch(
                VectorSearchQuery(semantic = semantic, maxCandidates = 5)
            )
        }
    }

    @Test
    fun currentWireContractRejectsEightBandsExplicitly() {
        val valid = SemanticVectorSignature(
            calibrationId = 73L,
            bucketId = 0,
            cells = intArrayOf(0),
            cellCounts = intArrayOf(2),
            fingerprint = longArrayOf(0L)
        )

        val error = assertFailsWith<IllegalArgumentException> {
            SemanticVectorSignature(
                calibrationId = valid.calibrationId,
                bucketId = valid.bucketId,
                cells = valid.cells,
                cellCounts = valid.cellCounts,
                fingerprint = valid.fingerprint,
                bands = List(8) { "0x0000000000000000" }
            )
        }
        assertTrue(error.message.orEmpty().contains("exactly four"))
    }

    @Test
    fun vectorQueryRejectsInvalidPublicBounds() {
        assertFailsWith<IllegalArgumentException> {
            VectorSearchQuery(text = "prompt", nearbyBucketRadius = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            VectorSearchQuery(text = "prompt", maxCandidates = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            VectorSearchQuery(text = "prompt", maxCandidates = MAX_VECTOR_SEARCH_CANDIDATES + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            VectorSearchQuery(text = " ")
        }
    }

    private fun emptyQueryPage(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody("""{"records":[],"totalResults":0}""")

    private fun JsonObject.objectAt(name: String): JsonObject = get(name).asJsonObject
}

private data class SemanticWireEntity(val id: String = "")
