package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.interactors.query.impl.DefaultQueryInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import entities.VectorPredicateEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** End-to-end parity for Unicode comparison routes and their conservative fallback edge. */
class VectorUnicodeRoutingIntegrationTest {
    private lateinit var databaseDirectory: Path
    private lateinit var trackingContext: VectorScannerTrackingSchemaContext
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager
    private lateinit var indexed: DefaultQueryInteractor
    private lateinit var fullScan: DefaultQueryInteractor
    private lateinit var ids: Map<String, Long>

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-vector-unicode-")
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

        val deseretUpper = String(Character.toChars(0x10400))
        ids = linkedMapOf(
            "decomposed" to "A\u030Aland",
            "dotless" to "Istanbul",
            "supplementary" to "prefix${deseretUpper}suffix",
            "other" to "nothing relevant"
        ).mapValues { (label, text) ->
            val saved = manager.saveEntity<IManagedEntity>(
                VectorPredicateEntity().apply {
                    category = label
                    this.text = text
                }
            ) as VectorPredicateEntity
            saved.id
        }

        val descriptor = trackingContext.getDescriptorForEntity(VectorPredicateEntity::class.java, "")
        indexed = DefaultQueryInteractor(descriptor, manager, trackingContext)
        fullScan = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, trackingContext)
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
    fun unicodeRoutesMatchTheAuthoritativeFullScanForPositiveAndNegativePredicates() {
        val deseretLower = String(Character.toChars(0x10428))
        val allIds = ids.values.toSet()
        val dotlessIMatches = setOf(
            ids.getValue("dotless"),
            ids.getValue("supplementary"),
            ids.getValue("other")
        )
        listOf(
            Case(QueryCriteria("text", QueryCriteriaOperator.STARTS_WITH, "A"), setOf(ids.getValue("decomposed"))),
            Case(QueryCriteria("text", QueryCriteriaOperator.CONTAINS, "A"), setOf(ids.getValue("decomposed"))),
            Case(QueryCriteria("text", QueryCriteriaOperator.CONTAINS_IGNORE_CASE, "\u0131"), dotlessIMatches),
            Case(
                QueryCriteria("text", QueryCriteriaOperator.NOT_CONTAINS_IGNORE_CASE, "\u0131"),
                allIds - dotlessIMatches
            ),
            Case(
                QueryCriteria("text", QueryCriteriaOperator.CONTAINS_IGNORE_CASE, deseretLower),
                setOf(ids.getValue("supplementary"))
            )
        ).forEach { case ->
            trackingContext.resetScannerUsage()
            val indexedIds = execute(indexed, case.criteria)
            assertTrue(trackingContext.vectorScannerScans > 0, "${case.criteria.operator} did not use the vector scanner")
            assertEquals(0, trackingContext.fullTableReads, "${case.criteria.operator} unexpectedly scanned the table")

            trackingContext.resetScannerUsage()
            val fullScanIds = execute(fullScan, case.criteria)
            assertTrue(trackingContext.fullTableReads > 0, "forced baseline did not scan the table")

            assertEquals(case.expected, fullScanIds, "authoritative ${case.criteria.operator} result")
            assertEquals(fullScanIds, indexedIds, "indexed ${case.criteria.operator} parity")
        }
    }

    @Test
    fun unpairedSurrogateLiteralFallsBackAndKeepsRawUtf16Semantics() {
        val criteria = QueryCriteria("text", QueryCriteriaOperator.CONTAINS, "\uDC00")

        trackingContext.resetScannerUsage()
        val automaticIds = execute(indexed, criteria)

        assertTrue(trackingContext.fullTableReads > 0)
        assertEquals(0, trackingContext.vectorScannerScans)
        assertEquals(setOf(ids.getValue("supplementary")), automaticIds)
    }

    private fun execute(interactor: DefaultQueryInteractor, criteria: QueryCriteria): Set<Long> =
        interactor.getReferencesForQuery<VectorPredicateEntity>(
            Query(VectorPredicateEntity::class.java, criteria)
        ).results.mapTo(LinkedHashSet()) { (it as VectorPredicateEntity).id }

    private data class Case(val criteria: QueryCriteria, val expected: Set<Long>)
}
