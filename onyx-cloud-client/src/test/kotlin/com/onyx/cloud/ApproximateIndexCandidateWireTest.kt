package com.onyx.cloud

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.onyx.cloud.api.ApproximateIndexCandidateQuery
import com.onyx.cloud.api.FULL_TEXT_ATTRIBUTE
import com.onyx.cloud.api.MAX_APPROXIMATE_INDEX_CANDIDATES
import com.onyx.cloud.api.MAX_APPROXIMATE_INDEX_ROUTE_VALUES
import com.onyx.cloud.api.QueryCriteria
import com.onyx.cloud.api.QueryCriteriaOperator
import com.onyx.cloud.api.eq
import com.onyx.cloud.extensions.gson
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

class ApproximateIndexCandidateWireTest {

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
    fun boundedInRouteUsesDedicatedTypedWireShapeAndPartition() {
        server.enqueue(emptyQueryPage())

        client.from<CandidateWireEntity>()
            .inPartition("revision-7")
            .approximateCandidates("bucketId", listOf(6, 7), maxCandidates = 17)
            .list<CandidateWireEntity>()

        val request = server.takeRequest()
        assertEquals("revision-7", request.requestUrl?.queryParameter("partition"))
        val criteria = JsonParser.parseString(request.body.readUtf8())
            .asJsonObject.objectAt("conditions").objectAt("criteria")
        assertEquals("bucketId", criteria["field"].asString)
        assertEquals(QueryCriteriaOperator.CANDIDATES.name, criteria["operator"].asString)
        val value = criteria.objectAt("value")
        assertEquals(listOf(6, 7), value["values"].asJsonArray.map { it.asInt })
        assertEquals(17, value["maxCandidates"].asInt)
    }

    @Test
    fun routeCountCanExceedSharedPostingVisitBudgetAndRoundTrips() {
        val original = ApproximateIndexCandidateQuery(
            values = List(128) { "bucket-$it" },
            maxCandidates = 32
        )

        val decoded = gson.fromJson(
            gson.toJson(original),
            ApproximateIndexCandidateQuery::class.java
        )

        assertEquals(original, decoded)
        assertEquals(128, decoded.values.size)
        assertEquals(32, decoded.maxCandidates)
    }

    @Test
    fun candidateAndRouteHardBoundsAreValidatedIndependently() {
        assertFailsWith<IllegalArgumentException> {
            ApproximateIndexCandidateQuery(emptyList(), maxCandidates = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            ApproximateIndexCandidateQuery(
                values = List(MAX_APPROXIMATE_INDEX_ROUTE_VALUES + 1) { it },
                maxCandidates = 1
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ApproximateIndexCandidateQuery(
                values = listOf(1),
                maxCandidates = MAX_APPROXIMATE_INDEX_CANDIDATES + 1
            )
        }
    }

    @Test
    fun candidateRouteCannotBeAppendedToAnExistingExactCondition() {
        val builder = client.from<CandidateWireEntity>()
            .search("existing exact-compatible clause")

        val error = assertFailsWith<IllegalArgumentException> {
            builder.approximateCandidates("bucketId", 6, maxCandidates = 5)
        }

        assertTrue(error.message.orEmpty().contains("sole root"))
    }

    @Test
    fun everyCandidateOperatorRemainsTheSoleRootRegardlessOfCompositionOrder() {
        candidateOperators.forEach { operator ->
            val appendAnd = client.from<CandidateWireEntity>().where(candidateCondition(operator))
            val appendAndError = assertFailsWith<IllegalArgumentException> {
                appendAnd.and("status" eq "active")
            }
            assertTrue(appendAndError.message.orEmpty().contains(operator.name))

            val appendOr = client.from<CandidateWireEntity>().where(candidateCondition(operator))
            val appendOrError = assertFailsWith<IllegalArgumentException> {
                appendOr.or("status" eq "active")
            }
            assertTrue(appendOrError.message.orEmpty().contains(operator.name))

            val candidateAfterExact = assertFailsWith<IllegalArgumentException> {
                client.from<CandidateWireEntity>()
                    .where("status" eq "active")
                    .and(candidateCondition(operator))
            }
            assertTrue(candidateAfterExact.message.orEmpty().contains(operator.name))

            val nestedCandidate = ("status" eq "active").and(candidateCondition(operator))
            val nestedError = assertFailsWith<IllegalArgumentException> {
                client.from<CandidateWireEntity>().where(nestedCandidate)
            }
            assertTrue(nestedError.message.orEmpty().contains(operator.name))
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun everyCandidateOperatorRejectsMutationsAndLiveStreamsBeforeTransport() {
        candidateOperators.forEach { operator ->
            val deleteError = assertFailsWith<IllegalStateException> {
                client.from<CandidateWireEntity>()
                    .where(candidateCondition(operator))
                    .delete()
            }
            assertTrue(deleteError.message.orEmpty().contains(operator.name))

            val updateError = assertFailsWith<IllegalStateException> {
                client.from<CandidateWireEntity>()
                    .where(candidateCondition(operator))
                    .setUpdates("status" to "archived")
                    .update()
            }
            assertTrue(updateError.message.orEmpty().contains(operator.name))

            val streamError = assertFailsWith<IllegalStateException> {
                client.from<CandidateWireEntity>()
                    .where(candidateCondition(operator))
                    .stream<CandidateWireEntity>(includeQueryResults = false, keepAlive = false)
            }
            assertTrue(streamError.message.orEmpty().contains(operator.name))
        }

        assertEquals(0, server.requestCount)
    }

    private fun candidateCondition(operator: QueryCriteriaOperator): ConditionBuilderImpl =
        ConditionBuilderImpl(
            QueryCriteria(
                field = if (operator == QueryCriteriaOperator.CANDIDATES) {
                    "bucketId"
                } else {
                    FULL_TEXT_ATTRIBUTE
                },
                operator = operator,
                value = emptyMap<String, Any?>(),
            ),
        )

    private fun emptyQueryPage(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody("""{"records":[],"totalResults":0}""")

    private fun JsonObject.objectAt(name: String): JsonObject = get(name).asJsonObject

    private companion object {
        private val candidateOperators = listOf(
            QueryCriteriaOperator.CANDIDATES,
            QueryCriteriaOperator.SEARCH_CANDIDATES,
            QueryCriteriaOperator.HNSW_CANDIDATES,
        )
    }
}

private data class CandidateWireEntity(val id: String = "")
