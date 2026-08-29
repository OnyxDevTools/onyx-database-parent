package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.approximateSearch
import com.onyx.persistence.query.from
import com.onyx.persistence.query.search
import com.onyx.vector.SemanticVectorSignature
import entities.VectorSearchEntity
import entities.VectorPartitionedEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VectorSearchCandidateBudgetIntegrationTest {

    private lateinit var databaseDirectory: Path
    private lateinit var trackingContext: FingerprintWorkTrackingSchemaContext
    private lateinit var factory: EmbeddedPersistenceManagerFactory

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-vector-candidate-budget-")
        val location = databaseDirectory.toString()
        trackingContext = FingerprintWorkTrackingSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = trackingContext,
            addShutdownHook = false
        ).apply {
            storeType = StoreType.IN_MEMORY
            maxCardinality = 1_000
            setCredentials("admin", "admin")
            initialize()
        }

        val signature = sharedSignature()
        repeat(RECORD_COUNT) { ordinal ->
            factory.persistenceManager.saveEntity<IManagedEntity>(VectorSearchEntity().apply {
                title = "Shared semantic route $ordinal"
                body = "common searchable payload"
                semanticSignature(signature)
            })
        }
    }

    @After
    fun cleanup() {
        try {
            if (::factory.isInitialized) factory.close()
        } finally {
            if (::databaseDirectory.isInitialized) databaseDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun semanticMaxCandidatesBoundsPostingVisitsAndCandidateHydration() {
        trackingContext.resetFingerprintWork()

        val results = factory.persistenceManager.from<VectorSearchEntity>()
            .search(
                VectorSearchQuery(
                    semantic = sharedSignature(),
                    nearbyBucketRadius = 0,
                    maxCandidates = SEMANTIC_CANDIDATE_LIMIT
                )
            )
            .list<VectorSearchEntity>()

        assertTrue(trackingContext.isFingerprintWorkCaptured)
        assertEquals(SEMANTIC_CANDIDATE_LIMIT, results.size)
        assertEquals(SEMANTIC_CANDIDATE_LIMIT, trackingContext.candidateLimit)
        assertEquals(SEMANTIC_CANDIDATE_LIMIT, trackingContext.candidateCount)
        assertEquals(SEMANTIC_CANDIDATE_LIMIT, trackingContext.evaluatedCandidateCount)
        assertTrue(trackingContext.postingVisits <= trackingContext.postingVisitLimit)
        assertTrue(trackingContext.routeLookups <= trackingContext.routeLookupLimit)
    }

    @Test
    fun lexicalOnlySearchRemainsAnExactUntruncatedPredicate() {
        val results = factory.persistenceManager.from<VectorSearchEntity>()
            .search(
                VectorSearchQuery(
                    text = "common searchable",
                    maxCandidates = 5,
                    requireAllTerms = true
                )
            )
            .list<VectorSearchEntity>()

        assertEquals(RECORD_COUNT, results.size)
    }

    @Test
    fun lexicalOnlyApproximateSearchUsesTheSharedWorkBudget() {
        trackingContext.resetFingerprintWork()

        val results = factory.persistenceManager.from<VectorSearchEntity>()
            .approximateSearch(
                VectorSearchQuery(
                    text = "common searchable",
                    maxCandidates = LEXICAL_CANDIDATE_LIMIT,
                    requireAllTerms = true
                )
            )
            .list<VectorSearchEntity>()

        assertTrue(trackingContext.isFingerprintWorkCaptured)
        assertEquals(LEXICAL_CANDIDATE_LIMIT, results.size)
        assertEquals(LEXICAL_CANDIDATE_LIMIT, trackingContext.candidateLimit)
        assertEquals(LEXICAL_CANDIDATE_LIMIT, trackingContext.candidateCount)
        assertEquals(LEXICAL_CANDIDATE_LIMIT, trackingContext.evaluatedCandidateCount)
        assertEquals(LEXICAL_CANDIDATE_LIMIT, trackingContext.postingVisitLimit)
        assertTrue(trackingContext.postingVisits <= LEXICAL_CANDIDATE_LIMIT)
        assertTrue(trackingContext.routeLookups <= trackingContext.routeLookupLimit)
    }

    @Test
    fun schemaFreeSearchCandidatesMapUsesTheSamePhysicalBudget() {
        trackingContext.resetFingerprintWork()
        val query = Query(
            VectorSearchEntity::class.java,
            QueryCriteria(
                Query.FULL_TEXT_ATTRIBUTE,
                QueryCriteriaOperator.SEARCH_CANDIDATES,
                mapOf(
                    "text" to "common searchable",
                    "maxCandidates" to 5.0,
                    "requireAllTerms" to true
                )
            )
        )

        val results = factory.persistenceManager.executeQuery<VectorSearchEntity>(query)

        assertEquals(5, results.size)
        assertEquals(5, query.resultsCount)
        assertEquals(5, trackingContext.candidateLimit)
        assertEquals(5, trackingContext.candidateCount)
        assertEquals(5, trackingContext.evaluatedCandidateCount)
        assertEquals(5, trackingContext.postingVisitLimit)
        assertTrue(trackingContext.postingVisits <= 5)
    }

    @Test
    fun searchCandidatesEnforcesDedicatedAdmissionContract() {
        val composed = Query(
            VectorSearchEntity::class.java,
            approximateSearch("common searchable", maxCandidates = 5)
                .and("id", QueryCriteriaOperator.GREATER_THAN, 0L)
        )
        val delete = Query(
            VectorSearchEntity::class.java,
            approximateSearch("common searchable", maxCandidates = 5)
        )
        val allPartitions = Query(
            VectorPartitionedEntity::class.java,
            approximateSearch("common searchable", maxCandidates = 5)
        )

        assertFailsWith<IllegalArgumentException> {
            factory.persistenceManager.executeQuery<VectorSearchEntity>(composed)
        }
        assertFailsWith<IllegalArgumentException> {
            factory.persistenceManager.executeDelete(delete)
        }
        assertFailsWith<IllegalArgumentException> {
            factory.persistenceManager.executeQuery<VectorPartitionedEntity>(allPartitions)
        }
    }

    @Test
    fun partitionedSearchCandidatesScansOnlyOneConcreteVectorPartition() {
        repeat(12) { ordinal ->
            factory.persistenceManager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
                region = "north"
                tag = "north-$ordinal"
                body = "common searchable payload"
            })
        }
        repeat(4) { ordinal ->
            factory.persistenceManager.saveEntity<IManagedEntity>(VectorPartitionedEntity().apply {
                region = "south"
                tag = "south-$ordinal"
                body = "common searchable payload"
            })
        }
        trackingContext.resetFingerprintWork()

        val results = factory.persistenceManager.from<VectorPartitionedEntity>()
            .approximateSearch("common searchable", maxCandidates = 5)
            .inPartition("north")
            .list<VectorPartitionedEntity>()

        assertEquals(5, results.size)
        assertTrue(results.all { it.region == "north" })
        assertEquals(5, trackingContext.candidateLimit)
        assertTrue(trackingContext.postingVisits <= 5)
    }

    private fun sharedSignature(): SemanticVectorSignature {
        val fingerprint = longArrayOf(0x1357_2468_1357_2468L)
        return SemanticVectorSignature(
            calibrationId = 73L,
            bucketId = 6,
            cells = intArrayOf(1, 2),
            cellCounts = intArrayOf(4, 4),
            fingerprint = fingerprint,
            bands = SemanticVectorSignature.splitIntoFourBands(fingerprint),
            boundaryConfidence = 1f
        )
    }

    private companion object {
        const val RECORD_COUNT = 80
        const val SEMANTIC_CANDIDATE_LIMIT = 17
        const val LEXICAL_CANDIDATE_LIMIT = 5
    }
}
