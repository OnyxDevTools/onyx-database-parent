package database.query

import com.onyx.extension.meetsCriteria
import com.onyx.interactors.record.data.Reference
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.VectorSearchQuery
import com.onyx.persistence.query.from
import com.onyx.persistence.query.search
import com.onyx.vector.SemanticVectorSignature
import database.base.DatabaseBaseTest
import entities.VectorSearchEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class VectorSearchLiveQueryTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun prepare() {
        manager.from<VectorSearchEntity>().delete()
    }

    @Test
    fun directCriteriaEvaluationUsesCurrentUnpersistedLexicalValues() {
        val entity = VectorSearchEntity().apply {
            title = "Live cache"
            body = "alpha needle beta"
        }
        val query = Query(
            VectorSearchEntity::class.java,
            search(VectorSearchQuery(text = "alpha needle"))
        )
        val descriptor = manager.context.getBaseDescriptorForEntity(VectorSearchEntity::class.java)!!

        assertTrue(query.meetsCriteria(entity, Reference(0L, 41L), manager.context, descriptor))

        entity.body = "unrelated replacement"
        assertFalse(query.meetsCriteria(entity, Reference(0L, 41L), manager.context, descriptor))
    }

    @Test
    fun multipleSearchClausesAreEvaluatedIndependentlyAndRefreshTheScore() {
        val entity = VectorSearchEntity().apply { body = "alpha" }
        val reference = Reference(0L, 42L)
        val criteria = search(
            VectorSearchQuery(text = "alpha absent", requireAllTerms = false)
        ).and(search(VectorSearchQuery(text = "missing")))
        val query = Query(VectorSearchEntity::class.java, criteria)
        val descriptor = manager.context.getBaseDescriptorForEntity(VectorSearchEntity::class.java)!!

        assertFalse(query.meetsCriteria(entity, reference, manager.context, descriptor))
        assertEquals(0.5f, query.fullTextScores?.get(reference))

        entity.body = "alpha missing"
        assertTrue(query.meetsCriteria(entity, reference, manager.context, descriptor))
        assertEquals(1.0f, query.fullTextScores?.get(reference))

        entity.body = "unrelated"
        assertFalse(query.meetsCriteria(entity, reference, manager.context, descriptor))
        assertFalse(query.fullTextScores?.containsKey(reference) == true)
    }

    @Test
    fun cachedLexicalSearchAddsNewMatchingRecord() {
        val added = CountDownLatch(1)
        val observed = AtomicReference<VectorSearchEntity>()
        val builder = manager.from<VectorSearchEntity>()
            .search("fresh lexical needle")
            .onItemAdded<VectorSearchEntity> {
                observed.set(it)
                added.countDown()
            }

        assertTrue(builder.list<VectorSearchEntity>().isEmpty())
        val saved = manager.saveEntity<IManagedEntity>(VectorSearchEntity().apply {
            title = "Listener candidate"
            body = "a fresh lexical needle arrived"
        }) as VectorSearchEntity

        assertTrue(added.await(2, TimeUnit.SECONDS), "Lexical listener did not receive the inserted match")
        assertTrue(observed.get()?.id == saved.id)
        builder.stopListening()
    }

    @Test
    fun cachedLexicalSearchRemovesRecordUsingUpdatedValues() {
        val saved = manager.saveEntity<IManagedEntity>(VectorSearchEntity().apply {
            title = "Mutable candidate"
            body = "tracked lexical phrase"
        }) as VectorSearchEntity
        val removed = CountDownLatch(1)
        val builder = manager.from<VectorSearchEntity>()
            .search("tracked lexical phrase")
            .onItemDeleted<VectorSearchEntity> { removed.countDown() }

        assertTrue(builder.list<VectorSearchEntity>().any { it.id == saved.id })
        saved.body = "replacement words"
        manager.saveEntity<IManagedEntity>(saved)

        assertTrue(removed.await(2, TimeUnit.SECONDS), "Lexical listener did not receive the removed match")
        builder.stopListening()
    }

    @Test
    fun cachedSemanticSearchAddsNewMatchingSignature() {
        val signature = semanticSignature(fingerprint = 0x1357_2468_1357_2468L)
        val added = CountDownLatch(1)
        val builder = manager.from<VectorSearchEntity>()
            .search(signature)
            .onItemAdded<VectorSearchEntity> { added.countDown() }

        assertTrue(builder.list<VectorSearchEntity>().isEmpty())
        manager.saveEntity<IManagedEntity>(VectorSearchEntity().apply {
            title = "Semantic candidate"
            body = "dense vectors are not persisted"
            semanticSignature(signature)
        })

        assertTrue(added.await(2, TimeUnit.SECONDS), "Semantic listener did not receive the inserted match")
        builder.stopListening()
    }

    @Test
    fun cachedSemanticSearchRemovesRecordWhenSignatureChanges() {
        val requested = semanticSignature(fingerprint = 0x1357_2468_1357_2468L)
        val saved = manager.saveEntity<IManagedEntity>(VectorSearchEntity().apply {
            title = "Mutable semantic candidate"
            semanticSignature(requested)
        }) as VectorSearchEntity
        val removed = CountDownLatch(1)
        val builder = manager.from<VectorSearchEntity>()
            .search(requested, minScore = 0.99f, nearbyBucketRadius = 0)
            .onItemDeleted<VectorSearchEntity> { removed.countDown() }

        assertTrue(builder.list<VectorSearchEntity>().any { it.id == saved.id })
        saved.semanticSignature(
            semanticSignature(
                fingerprint = 0x2468_1357_2468_1357L,
                calibrationId = 9_002L
            )
        )
        manager.saveEntity<IManagedEntity>(saved)

        assertTrue(removed.await(2, TimeUnit.SECONDS), "Semantic listener did not receive the removed match")
        builder.stopListening()
    }

    private fun semanticSignature(
        fingerprint: Long,
        calibrationId: Long = 9_001L
    ): SemanticVectorSignature {
        val words = longArrayOf(fingerprint)
        return SemanticVectorSignature(
            calibrationId = calibrationId,
            bucketId = 1,
            cells = intArrayOf(0, 1),
            cellCounts = intArrayOf(2, 2),
            fingerprint = words,
            bands = SemanticVectorSignature.splitIntoFourBands(words),
            boundaryConfidence = 1f,
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> =
            listOf(EmbeddedPersistenceManagerFactory::class)
    }
}
