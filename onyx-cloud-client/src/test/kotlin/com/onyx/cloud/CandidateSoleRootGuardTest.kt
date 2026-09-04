package com.onyx.cloud

import com.onyx.cloud.api.ApproximateIndexCandidateQuery
import com.onyx.cloud.api.FULL_TEXT_ATTRIBUTE
import com.onyx.cloud.api.HnswSearchQuery
import com.onyx.cloud.api.IConditionBuilder
import com.onyx.cloud.api.QueryCriteria
import com.onyx.cloud.api.QueryCriteriaOperator
import com.onyx.cloud.api.VectorSearchQuery
import com.onyx.cloud.api.approximateCandidates
import com.onyx.cloud.api.approximateSearch
import com.onyx.cloud.api.eq
import com.onyx.cloud.impl.ConditionBuilderImpl
import com.onyx.cloud.impl.OnyxClient
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CandidateSoleRootGuardTest {

    private val client = OnyxClient(
        baseUrl = "https://example.invalid",
        databaseId = "db",
        apiKey = "key",
        apiSecret = "secret",
    )

    @Test
    fun soleRootCandidateConditionCannotBeAddedAfterAnExactRoot() {
        soleRootCandidateConditions().forEach { (operator, candidateCondition) ->
            val builder = client.from<CandidateGuardEntity>().where("id".eq("record-1"))

            assertSoleRootFailure(operator) {
                builder.and(candidateCondition())
            }
        }
    }

    @Test
    fun soleRootCandidateCannotBeFollowedByAnotherCondition() {
        soleRootCandidateConditions().forEach { (operator, candidateCondition) ->
            val builder = client.from<CandidateGuardEntity>().where(candidateCondition())

            assertSoleRootFailure(operator) {
                builder.and("id".eq("record-1"))
            }
        }
    }

    @Test
    fun whereRejectsACompoundTreeContainingASoleRootCandidateOperator() {
        soleRootCandidateConditions().forEach { (operator, candidateCondition) ->
            val compound = "id".eq("record-1").and(candidateCondition())

            assertSoleRootFailure(operator) {
                client.from<CandidateGuardEntity>().where(compound)
            }
        }
    }

    @Test
    fun whereCanReplaceAScalarRootWithACandidateRoot() {
        candidateConditions().forEach { (_, candidateCondition) ->
            client.from<CandidateGuardEntity>()
                .where("id".eq("record-1"))
                .where(candidateCondition())
        }
    }

    @Test
    fun whereCanReplaceACandidateRootWithAScalarRoot() {
        candidateConditions().forEach { (_, candidateCondition) ->
            client.from<CandidateGuardEntity>()
                .where(candidateCondition())
                .where("id".eq("record-1"))
        }
    }

    @Test
    fun indexCandidatesComposeWithAndInEitherOrderAndInsideNestedAnd() {
        client.from<CandidateGuardEntity>()
            .where("id".eq("record-1"))
            .and(indexCandidate())
        client.from<CandidateGuardEntity>()
            .where(indexCandidate())
            .and("id".eq("record-1"))
        client.from<CandidateGuardEntity>()
            .where("id".eq("record-1").and(indexCandidate()))
    }

    @Test
    fun indexCandidatesRejectOrComposition() {
        val error = assertFailsWith<IllegalArgumentException> {
            client.from<CandidateGuardEntity>()
                .where(indexCandidate())
                .or("id".eq("record-1"))
        }

        assertTrue(error.message.orEmpty().contains("non-negated AND"))
    }

    private fun indexCandidate(): IConditionBuilder = approximateCandidates(
        "bucketId",
        ApproximateIndexCandidateQuery(value = 6, maxCandidates = 5),
    )

    private fun soleRootCandidateConditions(): List<Pair<QueryCriteriaOperator, () -> IConditionBuilder>> =
        candidateConditions().filterNot { it.first == QueryCriteriaOperator.CANDIDATES }

    private fun candidateConditions(): List<Pair<QueryCriteriaOperator, () -> IConditionBuilder>> = listOf(
        QueryCriteriaOperator.CANDIDATES to {
            approximateCandidates(
                "bucketId",
                ApproximateIndexCandidateQuery(value = 6, maxCandidates = 5),
            )
        },
        QueryCriteriaOperator.SEARCH_CANDIDATES to {
            approximateSearch(VectorSearchQuery(text = "bounded recall", maxCandidates = 5))
        },
        QueryCriteriaOperator.HNSW_CANDIDATES to {
            ConditionBuilderImpl(
                QueryCriteria(
                    FULL_TEXT_ATTRIBUTE,
                    QueryCriteriaOperator.HNSW_CANDIDATES,
                    HnswSearchQuery(73L, floatArrayOf(1f, 0f), 5, 8),
                )
            )
        },
    )

    private fun assertSoleRootFailure(
        operator: QueryCriteriaOperator,
        block: () -> Unit,
    ) {
        val error = assertFailsWith<IllegalArgumentException>(block = block)
        assertTrue(error.message.orEmpty().contains(operator.name))
        assertTrue(error.message.orEmpty().contains("sole root"))
    }
}

private data class CandidateGuardEntity(val id: String = "")
