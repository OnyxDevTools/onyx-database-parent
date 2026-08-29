package database.index

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.VectorManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.cont
import com.onyx.persistence.query.containsIgnoreCase
import com.onyx.persistence.query.eq
import com.onyx.persistence.query.from
import com.onyx.persistence.query.like
import com.onyx.persistence.query.match
import com.onyx.persistence.query.notCont
import com.onyx.persistence.query.notContainsIgnoreCase
import com.onyx.persistence.query.notLike
import com.onyx.persistence.query.notMatch
import com.onyx.persistence.query.notStartsWith
import com.onyx.persistence.query.search
import com.onyx.persistence.query.startsWith
import database.base.DatabaseBaseTest
import entities.VectorIndexEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Deterministic integration coverage for the managed vector fingerprint index. */
@RunWith(Parameterized::class)
class VectorIndexTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun prepare() {
        manager.from<VectorIndexEntity>().delete()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> =
            listOf(EmbeddedPersistenceManagerFactory::class)
    }

    @Test
    fun savesManagedVectorEntity() {
        val saved = save(
            label = "saved",
            vectorData = "This is a test string for fingerprint indexing"
        )

        assertNotNull(saved)
        assertTrue(saved.id > 0L)
    }

    @Test
    fun fingerprintInteractorReturnsOnlyRecordsContainingEveryQueryTerm() {
        val distinguishingTerms = listOf("amber", "birch", "cedar", "dogwood", "elmwood")
        distinguishingTerms.forEach { term ->
            save(
                label = "vector_$term",
                vectorData = "This is test vector string with $term content to differentiate"
            )
        }

        val results = fingerprintInteractor().matchAll(
            "test vector string cedar content"
        )

        assertEquals(1, results.size)
        assertEquals(listOf(1.0f), results.values.toList())
    }

    @Test
    fun deletingEntityRemovesItsFingerprintPostings() {
        val saved = save(
            label = "deleted",
            vectorData = "This is a vector string for embedding and deletion"
        )
        val queryText = "vector string embedding"

        assertEquals(1, fingerprintInteractor().matchAll(queryText).size)

        manager.deleteEntity(saved)

        assertTrue(fingerprintInteractor().matchAll(queryText).isEmpty())
    }

    @Test
    fun selectingScoreForAttributeLikeReturnsExactScore() {
        save("score_1", "alpha delta", "unused")
        save("score_2", "beta gamma", "unused")

        val results = manager.from(VectorIndexEntity::class)
            .select(Query.SCORE_SELECTION, "id", "vectorData")
            .where("vectorData" like "alpha")
            .list<Map<String, Any?>>()

        assertEquals(1, results.size)
        assertEquals("alpha delta", results.single()["vectorData"])
        assertEquals(1.0f, results.single()[Query.SCORE_SELECTION] as Float)
    }

    @Test
    fun wholeRecordLexicalSearchRequiresEveryQueryTerm() {
        save("partial_1", "This is a test vector string with partial matching content")
        save("partial_2", "This is a test vector string with different content")
        save("partial_3", "This is a test vector string with partial matching content and more")

        val results = manager.from(VectorIndexEntity::class)
            .where(search("test vector string partial matching"))
            .list<VectorIndexEntity>()

        assertEquals(setOf("partial_1", "partial_3"), labels(results))
    }

    @Test
    fun equalityReturnsTheExactPersistedRecord() {
        save("equal_1", "This is a test vector string for query testing")
        save("equal_2", "vector string for testing")
        save("equal_3", "This is a test vector string")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" eq "This is a test vector string for query testing")
            .list<VectorIndexEntity>()

        assertEquals(setOf("equal_1"), labels(results))
    }

    @Test
    fun fingerprintInteractorReturnsEveryExactLexicalDuplicate() {
        save("duplicate_1", "Identical vector content for testing")
        save("duplicate_2", "Identical vector content for testing")
        save("different", "Different vector content for testing")

        val results = fingerprintInteractor().matchAll("Identical vector content for testing")

        assertEquals(2, results.size)
        assertTrue(results.values.all { it == 1.0f })
    }

    @Test
    fun matchesOperatorUsesExactRegexSemantics() {
        save("matches_1", "This is a test vector string for matches testing")
        save("matches_2", "vector string for testing matches")
        save("matches_3", "This is a test vector string")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" match "This is a test vector string for matches testing")
            .list<VectorIndexEntity>()

        assertEquals(setOf("matches_1"), labels(results))
    }

    @Test
    fun likeOperatorRequiresEveryRequestedTerm() {
        save("like_1", "Identical vector content for testing")
        save("like_2", "Identical vector content for testing")
        save("like_3", "Different vector content for testing")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" like "Different vector content for testing")
            .list<VectorIndexEntity>()

        assertEquals(setOf("like_3"), labels(results))
    }

    @Test
    fun containsOperatorReturnsExactMatches() {
        save("contains_1", "This is a test vector string for contains testing")
        save("contains_2", "vector string for testing contains")
        save("contains_3", "This is a test vector string")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" cont "contains")
            .list<VectorIndexEntity>()

        assertEquals(setOf("contains_1", "contains_2"), labels(results))
    }

    @Test
    fun startsWithOperatorReturnsExactMatches() {
        save("starts_1", "This is a test vector string for starts with testing")
        save("starts_2", "vector string for testing starts with")
        save("starts_3", "This is a test vector string")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" startsWith "This is a")
            .list<VectorIndexEntity>()

        assertEquals(setOf("starts_1", "starts_3"), labels(results))
    }

    @Test
    fun notContainsOperatorReturnsExactComplement() {
        save("not_contains_1", "This is a test vector string for not contains testing")
        save("not_contains_2", "vector string for testing not contains")
        save("not_contains_3", "This is a test vector string")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" notCont "not contains")
            .list<VectorIndexEntity>()

        assertEquals(setOf("not_contains_3"), labels(results))
    }

    @Test
    fun containsIgnoreCaseOperatorReturnsExactMatches() {
        save("ignore_case_1", "This is a test vector string for contains ignore case testing")
        save("ignore_case_2", "vector string for testing CONTAINS ignore case")
        save("ignore_case_3", "This is a test vector string")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" containsIgnoreCase "CONTAINS")
            .list<VectorIndexEntity>()

        assertEquals(setOf("ignore_case_1", "ignore_case_2"), labels(results))
    }

    @Test
    fun notContainsIgnoreCaseOperatorReturnsExactComplement() {
        save("not_ignore_case_1", "This is a test vector string for not contains ignore case testing")
        save("not_ignore_case_2", "vector string for testing NOT contains ignore case")
        save("not_ignore_case_3", "This is a test vector string")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" notContainsIgnoreCase "NOT")
            .list<VectorIndexEntity>()

        assertEquals(setOf("not_ignore_case_3"), labels(results))
    }

    @Test
    fun notStartsWithOperatorReturnsExactComplement() {
        save("not_starts_1", "This is a test vector string for not starts with testing")
        save("not_starts_2", "vector string for testing not starts with")
        save("not_starts_3", "Another test vector string")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" notStartsWith "This")
            .list<VectorIndexEntity>()

        assertEquals(setOf("not_starts_2", "not_starts_3"), labels(results))
    }

    @Test
    fun notMatchesOperatorReturnsExactRegexComplement() {
        save("not_matches_1", "This is a test vector string for not matches testing")
        save("not_matches_2", "vector string for testing not matches")
        save("not_matches_3", "This is a test vector string")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" notMatch "This is a test vector string for not matches testing")
            .list<VectorIndexEntity>()

        assertEquals(setOf("not_matches_2", "not_matches_3"), labels(results))
    }

    @Test
    fun notLikeOperatorReturnsExactTokenComplement() {
        save("not_like_1", "This is a test vector string for not like testing")
        save("not_like_2", "vector string for testing not like")
        save("not_like_3", "This is a test vector string")

        val results = manager.from(VectorIndexEntity::class)
            .where("vectorData" notLike "not like")
            .list<VectorIndexEntity>()

        assertEquals(setOf("not_like_3"), labels(results))
    }

    @Test
    fun partitionedFingerprintQuerySurvivesReopen() {
        save(
            label = "partitioned",
            vectorData = "This is a test vector string for partition testing",
            partitionId = 1L
        )

        factory.close()
        initialize()

        val results = manager.from(VectorIndexEntity::class)
            .where("partitionId" eq 1L)
            .and("vectorData" like "This is a test vector string for partition testing")
            .list<VectorIndexEntity>()

        assertEquals(setOf("partitioned"), labels(results))
        assertEquals(setOf(1L), results.mapNotNull { it.partitionId }.toSet())
    }

    @Test
    fun predicatesCanTargetEitherManagedTextField() {
        save(
            label = "two_fields",
            vectorData = "This is the first vector string for testing",
            vectorData2 = "This is the second vector string for testing"
        )

        val firstResults = manager.from(VectorIndexEntity::class)
            .where("vectorData" like "first vector string")
            .list<VectorIndexEntity>()
        val secondResults = manager.from(VectorIndexEntity::class)
            .where("vectorData2" like "second vector string")
            .list<VectorIndexEntity>()

        assertEquals(setOf("two_fields"), labels(firstResults))
        assertEquals(setOf("two_fields"), labels(secondResults))
    }

    @Test
    fun fingerprintRebuildStreamsRecordsAndRestoresSearchRoutes() {
        repeat(256) { index ->
            save(
                label = "rebuild_$index",
                vectorData = "shared rebuild material unique-token-$index",
            )
        }
        val interactor = fingerprintInteractor()
        interactor.clear()

        assertTrue(interactor.matchAll("unique-token-173").isEmpty())

        interactor.rebuild()

        val rebuilt = manager.from(VectorIndexEntity::class)
            .where("vectorData" like "unique-token-173")
            .list<VectorIndexEntity>()
        assertEquals(setOf("rebuild_173"), labels(rebuilt))
    }

    private fun save(
        label: String,
        vectorData: String,
        vectorData2: String? = null,
        partitionId: Long? = null
    ): VectorIndexEntity = manager.saveEntity<IManagedEntity>(VectorIndexEntity().apply {
        this.label = label
        this.vectorData = vectorData
        this.vectorData2 = vectorData2
        this.partitionId = partitionId
    }) as VectorIndexEntity

    private fun fingerprintInteractor() = manager.context.getIndexInteractor(
        requireNotNull(
            manager.context.getBaseDescriptorForEntity(VectorIndexEntity::class.java)
                ?.indexes
                ?.get(VectorManagedEntity.REPRESENTATION_FIELD)
        )
    )

    private fun labels(results: List<VectorIndexEntity>): Set<String> =
        results.mapNotNull(VectorIndexEntity::label).toSet()
}
