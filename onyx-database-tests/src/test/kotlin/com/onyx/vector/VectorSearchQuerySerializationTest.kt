package com.onyx.vector

import com.onyx.buffer.BufferPool
import com.onyx.buffer.BufferStream
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.ApproximateIndexCandidateQuery
import com.onyx.persistence.query.HnswSearchQuery
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.SearchMatch
import com.onyx.persistence.query.SearchMode
import com.onyx.persistence.query.SearchOptions
import com.onyx.persistence.query.SearchQuery
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.search
import com.onyx.persistence.query.approximateSearch
import com.onyx.persistence.query.hnswCandidates
import com.onyx.network.serialization.impl.DefaultServerSerializer
import entities.VectorSearchEntity
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class VectorSearchQuerySerializationTest {

    @Test
    fun `high-level search survives DefaultServerSerializer RMI transport`() {
        val original = Query(
            VectorSearchEntity::class.java,
            search(
                "expense for each animal",
                SearchOptions(
                    mode = SearchMode.HYBRID,
                    match = SearchMatch.ANY,
                    minScore = 0.35f,
                    maxCandidates = 73,
                ),
            ),
        )
        val serializer = DefaultServerSerializer()
        val buffer = serializer.serialize(original)
        val decoded = try {
            serializer.deserialize<Query>(buffer)
        } finally {
            BufferPool.recycle(buffer)
        }
        val criteria = requireNotNull(decoded.criteria)
        val highLevelSearch = criteria.value as SearchQuery

        assertEquals(QueryCriteriaOperator.SEARCH, criteria.operator)
        assertEquals(Query.FULL_TEXT_ATTRIBUTE, criteria.attribute)
        assertEquals("expense for each animal", highLevelSearch.text)
        assertEquals(SearchMode.HYBRID, highLevelSearch.mode)
        assertEquals(SearchMatch.ANY, highLevelSearch.match)
        assertEquals(0.35f, highLevelSearch.minScore)
        assertEquals(73, highLevelSearch.maxCandidates)
    }

    @Test
    fun `native HNSW query survives DefaultServerSerializer RMI transport`() {
        val original = Query(
            VectorSearchEntity::class.java,
            hnswCandidates(
                HnswSearchQuery(
                    calibrationId = -7_640_891_576_956_012_809L,
                    vector = floatArrayOf(0.25f, -0.5f, 0.75f),
                    maxCandidates = 17,
                    efSearch = 93,
                    minScore = 0.42f,
                )
            )
        )
        val serializer = DefaultServerSerializer()
        val buffer = serializer.serialize(original)
        val decoded = try {
            serializer.deserialize<Query>(buffer)
        } finally {
            BufferPool.recycle(buffer)
        }
        val criteria = requireNotNull(decoded.criteria)
        val hnsw = criteria.value as HnswSearchQuery

        assertEquals(QueryCriteriaOperator.HNSW_CANDIDATES, criteria.operator)
        assertEquals(Query.FULL_TEXT_ATTRIBUTE, criteria.attribute)
        assertEquals(-7_640_891_576_956_012_809L, hnsw.calibrationId)
        assertContentEquals(floatArrayOf(0.25f, -0.5f, 0.75f), hnsw.vector)
        assertEquals(17, hnsw.maxCandidates)
        assertEquals(93, hnsw.efSearch)
        assertEquals(0.42f, hnsw.minScore)
    }

    @Test
    fun `bounded lexical search operator survives query transport serialization`() {
        val original = Query(
            VectorSearchEntity::class.java,
            approximateSearch(
                VectorSearchQuery(
                    text = "bounded recall",
                    maxCandidates = 32,
                    requireAllTerms = false
                )
            )
        )

        val buffer = BufferStream.toBuffer(original)
        val decoded = try {
            BufferStream.fromBuffer(buffer) as Query
        } finally {
            BufferPool.recycle(buffer)
        }
        val criteria = requireNotNull(decoded.criteria)
        val searchQuery = criteria.value as VectorSearchQuery

        assertEquals(QueryCriteriaOperator.SEARCH_CANDIDATES, criteria.operator)
        assertEquals("bounded recall", searchQuery.text)
        assertEquals(32, searchQuery.maxCandidates)
        assertEquals(false, searchQuery.requireAllTerms)
    }

    @Test
    fun `approximate index candidate route survives query transport serialization`() {
        val original = Query(
            VectorSearchEntity::class.java,
            QueryCriteria(
                "category",
                QueryCriteriaOperator.CANDIDATES,
                ApproximateIndexCandidateQuery(
                    values = List(128) { "route-$it" },
                    maxCandidates = 32
                )
            )
        )

        val buffer = BufferStream.toBuffer(original)
        val decoded = try {
            BufferStream.fromBuffer(buffer) as Query
        } finally {
            BufferPool.recycle(buffer)
        }
        val criteria = requireNotNull(decoded.criteria)
        val candidateQuery = criteria.value as ApproximateIndexCandidateQuery

        assertEquals(QueryCriteriaOperator.CANDIDATES, criteria.operator)
        assertEquals("category", criteria.attribute)
        assertEquals(128, candidateQuery.values.size)
        assertEquals(32, candidateQuery.maxCandidates)
        assertEquals("route-127", candidateQuery.values.last())
    }

    @Test
    fun `semantic search query survives query transport serialization`() {
        val fingerprint = longArrayOf(0x1234_5678_9abc_def0L)
        val signature = SemanticVectorSignature(
            calibrationId = 81L,
            bucketId = 23,
            cells = intArrayOf(2, 3),
            cellCounts = intArrayOf(5, 10),
            fingerprint = fingerprint,
            bands = SemanticVectorSignature.splitIntoFourBands(fingerprint),
            boundaryConfidence = 0.45f
        )
        val original = Query(
            VectorSearchEntity::class.java,
            search(
                VectorSearchQuery(
                    text = "delta neutral",
                    semantic = signature,
                    minScore = 0.42f,
                    nearbyBucketRadius = 2,
                    maxCandidates = 321,
                    requireAllTerms = false
                )
            )
        )

        val buffer = BufferStream.toBuffer(original)
        val decoded = try {
            BufferStream.fromBuffer(buffer) as Query
        } finally {
            BufferPool.recycle(buffer)
        }
        val searchQuery = decoded.criteria?.value as VectorSearchQuery
        val decodedSignature = searchQuery.semantic!!

        assertEquals("delta neutral", searchQuery.text)
        assertEquals(0.42f, searchQuery.minScore)
        assertEquals(2, searchQuery.nearbyBucketRadius)
        assertEquals(321, searchQuery.maxCandidates)
        assertEquals(false, searchQuery.requireAllTerms)
        assertEquals(signature.calibrationId, decodedSignature.calibrationId)
        assertEquals(signature.bucketId, decodedSignature.bucketId)
        assertContentEquals(signature.cells, decodedSignature.cells)
        assertContentEquals(signature.cellCounts, decodedSignature.cellCounts)
        assertContentEquals(signature.fingerprint, decodedSignature.fingerprint)
        assertContentEquals(signature.bands, decodedSignature.bands)
    }
}
