package database.query

import com.onyx.diskmap.store.StoreType
import com.onyx.interactors.query.impl.DefaultQueryInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import com.onyx.persistence.query.QueryPartitionMode
import entities.SelectIdentifierTestEntity
import entities.partition.IndexPartitionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class ScalarAndIndexPlannerIntegrationTest {

    private lateinit var databaseDirectory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager
    private lateinit var trackingContext: VectorScannerTrackingSchemaContext
    private lateinit var automatic: DefaultQueryInteractor
    private lateinit var fullTable: DefaultQueryInteractor
    private lateinit var rows: List<SelectIdentifierTestEntity>

    @Before
    fun initialize() {
        databaseDirectory = Files.createTempDirectory("onyx-scalar-and-planner-")
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
        rows = (0 until 128).map { offset ->
            manager.saveEntity<IManagedEntity>(SelectIdentifierTestEntity().apply {
                id = offset + 1L
                index = offset % 8
                attribute = if (offset % 3 == 0) null else "present"
            }) as SelectIdentifierTestEntity
        }
        val descriptor = trackingContext.getDescriptorForEntity(SelectIdentifierTestEntity::class.java, "")
        automatic = DefaultQueryInteractor(descriptor, manager, trackingContext)
        fullTable = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, trackingContext)
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
    fun `indexed equality avoids table traversal regardless of predicate order`() {
        val expected = matchingIds { it.attribute == "present" && it.index == 3 }
        assertEquivalent(expected, criteria = { present().and(indexEquals(3)) })
        assertEquivalent(expected, criteria = { indexEquals(3).and(present()) })
    }

    @Test
    fun `indexed IN preserves duplicate membership and the residual filter`() {
        assertEquivalent(
            matchingIds { it.attribute == "present" && it.index in setOf(1, 3) },
            criteria = {
                present().and(QueryCriteria("index", QueryCriteriaOperator.IN, listOf(1, 3, 3)))
            }
        )
    }

    @Test
    fun `nested AND finds an indexed predicate and still evaluates the whole group`() {
        assertEquivalent(
            matchingIds { it.attribute == "present" && it.index == 3 && it.id > 40L },
            criteria = {
                present().and(
                    QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 40L)
                        .and(indexEquals(3))
                )
            }
        )
    }

    @Test
    fun `paging retains total count and repeated execution preserves caller criteria`() {
        val expected = matchingIds { it.attribute == "present" && it.index == 3 }
        assertEquivalent(expected, criteria = { present().and(indexEquals(3)) }) {
            queryOrders = listOf(QueryOrder("id", false))
            firstRow = 2
            maxResults = 3
        }

        val query = query(present().and(indexEquals(3)))
        query.getAllCriteria()
        val originalCriteria = criteriaShape(query.criteria!!)
        repeat(2) {
            trackingContext.resetScannerUsage()
            assertEquals(expected, execute(automatic, query))
            assertEquals(expected.size, query.resultsCount)
            assertEquals(0, trackingContext.fullTableReads)
            assertEquals(originalCriteria, criteriaShape(query.criteria!!))
        }
    }

    @Test
    fun `identifier equality can seed a query with an unindexed first predicate`() {
        // Choosing the broader secondary index first would exceed this limit and scan the table.
        trackingContext.maxCardinality = 4
        assertEquivalent(
            listOf(12L),
            criteria = {
                present().and(indexEquals(3))
                    .and(QueryCriteria("id", QueryCriteriaOperator.EQUAL, 12L))
            }
        )
        assertEquivalent(
            emptyList(),
            criteria = { present().and(QueryCriteria("id", QueryCriteriaOperator.EQUAL, 4L)) }
        )
    }

    @Test
    fun `empty indexed candidate sets do not trigger a table scan`() {
        assertEquivalent(emptyList(), criteria = { present().and(indexEquals(99)) })
        assertEquivalent(emptyList(), criteria = {
            present().and(QueryCriteria("index", QueryCriteriaOperator.IN, emptyList<Int>()))
        })
    }

    @Test
    fun `OR and group negation retain full query semantics`() {
        assertEquivalent(
            matchingIds { it.attribute == "present" || it.index == 3 },
            expectedTableScans = 1,
            criteria = { present().or(indexEquals(3)) }
        )
        assertEquivalent(
            matchingIds { it.attribute == "present" && (it.index == 3 || it.index == 5) },
            expectedTableScans = 1,
            criteria = { present().and(indexEquals(3).or(indexEquals(5))) }
        )
        assertEquivalent(
            matchingIds { !(it.attribute == "present" && it.index == 3) },
            expectedTableScans = 1,
            criteria = { present().and(indexEquals(3)).not() }
        )
    }

    @Test
    fun `range predicates retain the full scan comparison behavior`() {
        val cases = listOf<Pair<QueryCriteriaOperator, (Int) -> Boolean>>(
            QueryCriteriaOperator.GREATER_THAN to { it > 3 },
            QueryCriteriaOperator.GREATER_THAN_EQUAL to { it >= 3 },
            QueryCriteriaOperator.LESS_THAN to { it < 3 },
            QueryCriteriaOperator.LESS_THAN_EQUAL to { it <= 3 }
        )
        cases.forEach { (operator, matches) ->
            assertEquivalent(
                matchingIds { it.attribute == "present" && matches(it.index) },
                expectedTableScans = 1,
                criteria = { present().and(QueryCriteria("index", operator, 3)) }
            )
        }
    }

    @Test
    fun `coercing and nullable values retain full scan comparison behavior`() {
        assertEquivalent(
            matchingIds { it.attribute == "present" && it.index == 3 },
            expectedTableScans = 1,
            criteria = { present().and(QueryCriteria("index", QueryCriteriaOperator.EQUAL, "3")) }
        )
        assertEquivalent(
            listOf(2L),
            expectedTableScans = 1,
            criteria = { present().and(QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2)) }
        )
        assertEquivalent(
            matchingIds { it.attribute == "present" && it.index == 3 },
            expectedTableScans = 1,
            criteria = {
                present().and(QueryCriteria("index", QueryCriteriaOperator.IN, listOf(3, null)))
            }
        )
    }

    @Test
    fun `large candidate sets fall back without rejecting a small final result`() {
        manager.saveEntity<IManagedEntity>(SelectIdentifierTestEntity().apply {
            id = 1000L
            index = 3
            attribute = "rare"
        })
        trackingContext.maxCardinality = 4
        assertEquivalent(
            listOf(1000L),
            expectedTableScans = 1,
            criteria = { QueryCriteria("attribute", QueryCriteriaOperator.EQUAL, "rare").and(indexEquals(3)) }
        )
    }

    @Test
    fun `public query lazy cache and count entrypoints use the indexed candidate path`() {
        val expected = matchingIds { it.attribute == "present" && it.index == 3 }
        for (cacheEnabled in listOf(false, true)) {
            repeat(2) {
                val query = query(present().and(indexEquals(3))).apply { cache = cacheEnabled }
                trackingContext.resetScannerUsage()
                assertEquals(expected, manager.executeQuery<SelectIdentifierTestEntity>(query).map { it.id })
                assertEquals(expected.size, query.resultsCount)
                assertEquals(0, trackingContext.fullTableReads)
            }
        }

        val lazyQuery = query(present().and(indexEquals(3))).apply {
            firstRow = 2
            maxResults = 3
        }
        trackingContext.resetScannerUsage()
        assertEquals(
            expected.drop(2).take(3),
            manager.executeLazyQuery<SelectIdentifierTestEntity>(lazyQuery).map { it.id }
        )
        assertEquals(expected.size, lazyQuery.resultsCount)
        assertEquals(0, trackingContext.fullTableReads)

        trackingContext.resetScannerUsage()
        assertEquals(expected.size.toLong(), manager.countForQuery(query(present().and(indexEquals(3)))))
        assertEquals(0, trackingContext.fullTableReads)
    }

    @Test
    fun `indexed collection equality retains whole value comparison`() {
        listOf(
            Triple(1L, "present", arrayListOf(1, 2)),
            Triple(2L, "present", arrayListOf(2, 3)),
            Triple(3L, "absent", arrayListOf(1, 2))
        ).forEach { (identifier, tag, keys) ->
            manager.saveEntity<IManagedEntity>(ScalarAndCollectionIndexEntity().apply {
                id = identifier
                attribute = tag
                values = keys
            })
        }
        val descriptor = trackingContext.getDescriptorForEntity(ScalarAndCollectionIndexEntity::class.java, "")
        val indexed = DefaultQueryInteractor(descriptor, manager, trackingContext)
        val forced = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, trackingContext)
        fun collectionQuery() = Query(
            ScalarAndCollectionIndexEntity::class.java,
            QueryCriteria("attribute", QueryCriteriaOperator.EQUAL, "present")
                .and(QueryCriteria("values", QueryCriteriaOperator.EQUAL, arrayListOf(1, 2))),
            QueryOrder("id", true)
        )
        for (interactor in listOf(indexed, forced)) {
            trackingContext.resetScannerUsage()
            val query = collectionQuery()
            val ids = interactor.getReferencesForQuery<ScalarAndCollectionIndexEntity>(query).results.map {
                (it as ScalarAndCollectionIndexEntity).id
            }
            assertEquals(listOf(1L), ids)
            assertEquals(1, query.resultsCount)
            assertEquals(1, trackingContext.fullTableReads)
        }
    }

    @Test
    fun `partitioned candidates remain within the requested partition`() {
        val expected = mutableListOf<Long>()
        for (partition in listOf(7L, 8L)) {
            repeat(12) { offset ->
                val saved = manager.saveEntity<IManagedEntity>(IndexPartitionEntity().apply {
                    partitionId = partition
                    indexVal = (offset % 3).toLong()
                }) as IndexPartitionEntity
                if (partition == 7L && saved.indexVal == 1L) expected += saved.id!!
            }
        }
        val descriptor = trackingContext.getDescriptorForEntity(IndexPartitionEntity::class.java, 7L)
        val indexed = DefaultQueryInteractor(descriptor, manager, trackingContext)
        val forced = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, trackingContext)
        fun partitionQuery() = Query(
            IndexPartitionEntity::class.java,
            QueryCriteria("partitionId", QueryCriteriaOperator.EQUAL, 7L)
                .and(QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 1L)),
            QueryOrder("id", true)
        ).apply { partition = 7L }
        fun executePartition(interactor: DefaultQueryInteractor, query: Query): List<Long> =
            interactor.getReferencesForQuery<IndexPartitionEntity>(query).results.map {
                val entity = it as IndexPartitionEntity
                assertEquals(7L, entity.partitionId)
                entity.id!!
            }

        trackingContext.resetScannerUsage()
        val indexedQuery = partitionQuery()
        assertEquals(expected.sorted(), executePartition(indexed, indexedQuery))
        assertEquals(0, trackingContext.fullTableReads)

        trackingContext.resetScannerUsage()
        val fullQuery = partitionQuery()
        assertEquals(expected.sorted(), executePartition(forced, fullQuery))
        assertEquals(1, trackingContext.fullTableReads)
        assertEquals(expected.size, indexedQuery.resultsCount)
        assertEquals(fullQuery.resultsCount, indexedQuery.resultsCount)
    }

    @Test
    fun `all partitions preserve results when a candidate task exceeds cardinality`() {
        val expected = mutableListOf<Long>()
        for ((partition, count) in listOf(7L to 3, 8L to 20)) {
            repeat(count) {
                val saved = manager.saveEntity<IManagedEntity>(IndexPartitionEntity().apply {
                    partitionId = partition
                    indexVal = 1L
                }) as IndexPartitionEntity
                if (partition == 7L) expected += saved.id!!
            }
        }
        val descriptor = trackingContext.getDescriptorForEntity(IndexPartitionEntity::class.java, "")
        val indexed = DefaultQueryInteractor(descriptor, manager, trackingContext)
        val forced = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, trackingContext)
        fun partitionQuery() = Query(
            IndexPartitionEntity::class.java,
            QueryCriteria("partitionId", QueryCriteriaOperator.EQUAL, 7L)
                .and(QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 1L)),
            QueryOrder("id", true)
        ).apply { partition = QueryPartitionMode.ALL }
        fun executePartition(interactor: DefaultQueryInteractor, query: Query): List<Long> =
            interactor.getReferencesForQuery<IndexPartitionEntity>(query).results.map {
                val entity = it as IndexPartitionEntity
                assertEquals(7L, entity.partitionId)
                entity.id!!
            }

        for ((limit, expectedTableScans) in listOf(100 to 0, 4 to 1)) {
            trackingContext.maxCardinality = limit
            trackingContext.resetScannerUsage()
            val indexedQuery = partitionQuery()
            assertEquals(expected.sorted(), executePartition(indexed, indexedQuery))
            assertEquals(expectedTableScans, trackingContext.fullTableReads)

            trackingContext.resetScannerUsage()
            val fullQuery = partitionQuery()
            assertEquals(expected.sorted(), executePartition(forced, fullQuery))
            assertEquals(1, trackingContext.fullTableReads)
            assertEquals(expected.size, indexedQuery.resultsCount)
            assertEquals(fullQuery.resultsCount, indexedQuery.resultsCount)
        }
    }

    private fun assertEquivalent(
        expectedIds: List<Long>,
        expectedTableScans: Int = 0,
        criteria: () -> QueryCriteria,
        configure: Query.() -> Unit = {}
    ) {
        val indexedQuery = query(criteria()).apply(configure)
        indexedQuery.getAllCriteria()
        val originalCriteria = criteriaShape(indexedQuery.criteria!!)
        val sortedIds = if (indexedQuery.queryOrders!!.single().isAscending) expectedIds else expectedIds.reversed()
        val expectedPage = sortedIds.drop(indexedQuery.firstRow).let {
            if (indexedQuery.maxResults >= 0) it.take(indexedQuery.maxResults) else it
        }

        trackingContext.resetScannerUsage()
        val actualIds = execute(automatic, indexedQuery)
        val actualTableScans = trackingContext.fullTableReads
        assertEquals(originalCriteria, criteriaShape(indexedQuery.criteria!!), "Execution changed the caller's predicate tree")

        trackingContext.resetScannerUsage()
        val fullQuery = query(criteria()).apply(configure)
        val fullIds = execute(fullTable, fullQuery)
        assertEquals(fullIds, actualIds, "Automatic selection differs from the full-table reference execution")
        assertEquals(expectedPage, actualIds)
        assertEquals(expectedIds.size, indexedQuery.resultsCount)
        assertEquals(expectedTableScans, actualTableScans, "Unexpected full-table traversal count")
        assertEquals(indexedQuery.resultsCount, fullQuery.resultsCount)
        assertEquals(1, trackingContext.fullTableReads, "The reference execution must traverse the table")
    }

    private fun query(criteria: QueryCriteria) =
        Query(SelectIdentifierTestEntity::class.java, criteria, QueryOrder("id", true))

    private fun execute(interactor: DefaultQueryInteractor, query: Query): List<Long> =
        interactor.getReferencesForQuery<SelectIdentifierTestEntity>(query).results.map {
            (it as SelectIdentifierTestEntity).id
        }

    private fun matchingIds(predicate: (SelectIdentifierTestEntity) -> Boolean): List<Long> =
        rows.filter(predicate).map { it.id }.sorted()

    private fun present() = QueryCriteria("attribute", QueryCriteriaOperator.EQUAL, "present")

    private fun indexEquals(value: Int) = QueryCriteria("index", QueryCriteriaOperator.EQUAL, value)

    private fun criteriaShape(criteria: QueryCriteria): List<Any?> = listOf(
        criteria.attribute,
        criteria.operator,
        criteria.value,
        criteria.isAnd,
        criteria.isOr,
        criteria.isNot,
        criteria.flip,
        criteria.subCriteria.map(::criteriaShape)
    )
}

@Entity
class ScalarAndCollectionIndexEntity : ManagedEntity() {
    @Identifier
    @Attribute
    var id: Long = 0L

    @Attribute
    var attribute: String = ""

    @Attribute
    @Index
    var values: ArrayList<Int> = arrayListOf()
}
