package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.interactors.query.impl.DefaultQueryInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import entities.VectorPredicateEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultQueryInteractorScannerSelectionTest {

    private lateinit var databaseDirectory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager
    private lateinit var trackingContext: VectorScannerTrackingSchemaContext
    private lateinit var expectedIds: List<Long>

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-query-scanner-selection-")
        val location = databaseDirectory.toString()
        trackingContext = VectorScannerTrackingSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(
            databaseLocation = location,
            instance = location,
            schemaContext = trackingContext,
            addShutdownHook = false
        ).apply {
            storeType = StoreType.IN_MEMORY
            setCredentials("admin", "admin")
            initialize()
        }
        manager = factory.persistenceManager

        save(intValue = -1, category = "alpha", nullableTag = "present")
        val alphaMatch = save(intValue = 5, category = "alpha", nullableTag = "present")
        val nullMatch = save(intValue = 10, category = "beta", nullableTag = null)
        save(intValue = 20, category = "beta", nullableTag = "present")
        expectedIds = listOf(alphaMatch, nullMatch)
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
    fun forcedFullTableSelectionUsesTheSameCompoundQueryPipeline() {
        val descriptor = trackingContext.getDescriptorForEntity(VectorPredicateEntity::class.java, "")
        val automatic = DefaultQueryInteractor(descriptor, manager, trackingContext)
        val fullTable = DefaultQueryInteractorTestBridge.forceFullTable(
            descriptor,
            manager,
            trackingContext
        )

        trackingContext.resetScannerUsage()
        val indexedIds = execute(automatic)
        assertTrue(trackingContext.vectorScannerScans > 0, "Automatic selection did not execute the vector scanner")
        assertEquals(0, trackingContext.fullTableReads, "Automatic selection unexpectedly read the full table")

        trackingContext.resetScannerUsage()
        val fullTableIds = execute(fullTable)
        assertEquals(1, trackingContext.fullTableReads, "Forced selection did not execute one root full-table scan")
        assertEquals(0, trackingContext.vectorScannerScans, "Forced selection executed a vector scanner")

        assertEquals(expectedIds, indexedIds)
        assertEquals(indexedIds, fullTableIds)
    }

    @Test
    fun scalarLikeScoreBatchingPreservesPublicScoreValues() {
        val descriptor = trackingContext.getDescriptorForEntity(VectorPredicateEntity::class.java, "")
        val interactor = DefaultQueryInteractor(descriptor, manager, trackingContext)
        val query = Query(
            VectorPredicateEntity::class.java,
            QueryCriteria("category", QueryCriteriaOperator.LIKE, "ALPHA"),
            QueryOrder("id", true)
        )

        val ids = interactor.getReferencesForQuery<VectorPredicateEntity>(query)
            .results
            .map { (it as VectorPredicateEntity).id }

        assertEquals(2, ids.size)
        assertEquals(2, query.fullTextScores?.size)
        assertTrue(query.fullTextScores?.values?.all { it == 1.0f } == true)
    }

    private fun execute(interactor: DefaultQueryInteractor): List<Long> {
        val query = Query(
            VectorPredicateEntity::class.java,
            QueryCriteria("intValue", QueryCriteriaOperator.GREATER_THAN_EQUAL, 0)
                .and(QueryCriteria("category", QueryCriteriaOperator.EQUAL, "alpha"))
                .or(QueryCriteria("nullableTag", QueryCriteriaOperator.IS_NULL)),
            QueryOrder("id", true)
        )
        return interactor.getReferencesForQuery<VectorPredicateEntity>(query)
            .results
            .map { (it as VectorPredicateEntity).id }
    }

    private fun save(intValue: Int, category: String, nullableTag: String?): Long {
        val saved = manager.saveEntity<IManagedEntity>(VectorPredicateEntity().apply {
            this.intValue = intValue
            this.category = category
            this.nullableTag = nullableTag
            text = "$category $intValue"
        }) as VectorPredicateEntity
        return saved.id
    }
}
