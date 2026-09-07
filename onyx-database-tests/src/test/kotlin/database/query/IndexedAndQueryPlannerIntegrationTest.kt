package database.query

import com.onyx.buffer.BufferStream
import com.onyx.diskmap.store.StoreType
import com.onyx.interactors.query.impl.DefaultQueryInteractor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.annotations.Index
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryOrder
import com.onyx.persistence.query.QueryPartitionMode
import entities.NullIndexEntity
import entities.SelectIdentifierTestEntity
import entities.partition.IndexPartitionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndexedAndQueryPlannerIntegrationTest {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var context: IndexedAndTrackingSchemaContext
    private lateinit var manager: PersistenceManager
    private lateinit var automatic: DefaultQueryInteractor
    private lateinit var fullTable: DefaultQueryInteractor

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-indexed-and-planner-")
        val location = directory.toString()
        context = IndexedAndTrackingSchemaContext(location, location)
        factory = EmbeddedPersistenceManagerFactory(location, location, context, false).apply {
            storeType = StoreType.IN_MEMORY
            initialize()
        }
        manager = factory.persistenceManager
        for (identifier in 1L..128L) {
            manager.saveEntity<IManagedEntity>(SelectIdentifierTestEntity().apply {
                id = identifier
                index = if (identifier == 128L) 2 else 1
            })
        }
        val descriptor = context.getDescriptorForEntity(SelectIdentifierTestEntity::class.java, "")
        automatic = DefaultQueryInteractor(descriptor, manager, context)
        fullTable = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, context)
    }

    @After
    fun cleanup() {
        try {
            if (::factory.isInitialized) factory.close()
        } finally {
            if (::directory.isInitialized) directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `identifier hits misses and residual rejection avoid broad postings in either order`() {
        for (identifier in listOf(64L, -1L, 128L)) {
            for (identifierFirst in listOf(true, false)) {
                assertSelectiveEquivalent(if (identifier == 64L) listOf(64L) else emptyList(), criteria = {
                    conjunction(idEquals(identifier), broadIndex(), identifierFirst)
                })
            }
        }
    }

    @Test
    fun `bounded identifier IN takes priority over broad equality and deduplicates identifiers`() {
        context.maxCardinality = 4
        for (identifiers in listOf(listOf(2L, 3L, 3L, 4L, -1L), emptyList())) {
            for (identifierFirst in listOf(true, false)) {
                assertSelectiveEquivalent(identifiers.filter { it > 0 }.distinct(), criteria = {
                    conjunction(idIn(identifiers), broadIndex(), identifierFirst)
                })
            }
        }
        assertSelectiveEquivalent(listOf(2L, 3L), criteria = {
            idIn(listOf(2L, 2L, 3L)).and(QueryCriteria("index", QueryCriteriaOperator.IN, listOf(1, 1)))
        })
    }

    @Test
    fun `intermediate broad posting cardinality cannot reject a one row query`() {
        context.maxCardinality = 4
        for (identifierFirst in listOf(true, false)) {
            assertSelectiveEquivalent(listOf(64L), criteria = {
                conjunction(idEquals(64L), broadIndex(), identifierFirst)
            })
        }
    }

    @Test
    fun `nested conjunction selects the shortest bounded identifier IN`() {
        context.maxCardinality = 4
        assertSelectiveEquivalent(listOf(2L, 3L), criteria = {
            broadIndex().and(idIn((1L..100L).toList()).and(idIn(listOf(2L, 3L))))
        })
    }

    @Test
    fun `identifier IN beyond the planning bound retains the original index path`() {
        val query = query(idIn((1L..1025L).toList()).and(broadIndex()))
        context.resetWork()
        assertEquals((1L..127L).toList(), execute(automatic, query))
        assertEquals(127, query.resultsCount)
        assertEquals(1, context.indexLookups)
        assertEquals(127L, context.materializedPostings)
        assertEquals(0, context.fullTableScans)
        assertEquals(0, context.referenceFilterScans)
    }

    @Test
    fun `ordered pages retain total count and repeated execution preserves criteria`() {
        val identifiers = listOf(2L, 3L, 4L, 5L, 6L)
        assertSelectiveEquivalent(identifiers, criteria = { broadIndex().and(idIn(identifiers)) }) {
            queryOrders = listOf(QueryOrder("id", false))
            firstRow = 1
            maxResults = 2
        }
        val query = query(broadIndex().and(idIn(identifiers)))
        query.getAllCriteria()
        val shape = criteriaShape(query.criteria!!)
        repeat(2) {
            context.resetWork()
            assertEquals(identifiers, execute(automatic, query))
            assertEquals(identifiers.size, query.resultsCount)
            assertEquals(shape, criteriaShape(query.criteria!!))
            assertSelectiveWork()
        }
    }

    @Test
    fun `public eager cached lazy and count queries keep selective work and pagination`() {
        val identifiers = listOf(2L, 3L, 4L, 5L)
        fun criteria() = broadIndex().and(idIn(identifiers + 3L))
        for (cacheEnabled in listOf(false, true)) {
            repeat(2) {
                val query = query(criteria()).apply { cache = cacheEnabled }
                context.resetWork()
                assertEquals(identifiers, manager.executeQuery<SelectIdentifierTestEntity>(query).map { it.id })
                assertEquals(identifiers.size, query.resultsCount)
                assertSelectiveWork(requireReferenceFilter = !cacheEnabled || it == 0)
            }
        }
        val lazyQuery = query(criteria()).apply { firstRow = 1; maxResults = 2 }
        context.resetWork()
        assertEquals(listOf(3L, 4L), manager.executeLazyQuery<SelectIdentifierTestEntity>(lazyQuery).map { it.id })
        assertEquals(identifiers.size, lazyQuery.resultsCount)
        assertSelectiveWork()

        context.resetWork()
        assertEquals(identifiers.size.toLong(), manager.countForQuery(query(criteria())))
        assertSelectiveWork()
    }

    @Test
    fun `partitioned identifier seeds preserve requested and all partition domains`() {
        for (partition in listOf(7L, 8L)) {
            for (identifier in 1L..12L) {
                manager.saveEntity<IManagedEntity>(IndexPartitionEntity().apply {
                    id = identifier
                    partitionId = partition
                    indexVal = 1L
                })
            }
        }
        context.maxCardinality = 4
        for (partition in listOf(7L, QueryPartitionMode.ALL)) {
            val descriptor = context.getDescriptorForEntity(
                IndexPartitionEntity::class.java, if (partition === QueryPartitionMode.ALL) "" else partition
            )
            val indexed = DefaultQueryInteractor(descriptor, manager, context)
            val forced = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, context)
            for (identifierFirst in listOf(true, false)) {
                fun partitionQuery() = Query(
                    IndexPartitionEntity::class.java,
                    conjunction(
                        QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3L),
                        QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 1L),
                        identifierFirst
                    ), QueryOrder("partitionId", true)
                ).apply { this.partition = partition }
                fun executePartition(interactor: DefaultQueryInteractor, query: Query) =
                    interactor.getReferencesForQuery<IndexPartitionEntity>(query).results.map {
                        val entity = it as IndexPartitionEntity
                        entity.partitionId to entity.id
                    }
                context.resetWork()
                val indexedQuery = partitionQuery()
                val actual = executePartition(indexed, indexedQuery)
                assertSelectiveWork()
                val expected = if (partition === QueryPartitionMode.ALL) listOf(7L to 3L, 8L to 3L) else listOf(7L to 3L)
                assertEquals(expected, actual)
                context.resetWork()
                val fullQuery = partitionQuery()
                assertEquals(actual, executePartition(forced, fullQuery))
                assertEquals(expected.size, indexedQuery.resultsCount)
                assertEquals(fullQuery.resultsCount, indexedQuery.resultsCount)
                assertEquals(1, context.fullTableScans)
            }
        }
    }

    @Test
    fun `mixed type indexed equality preserves native index coercion`() {
        val query = query(idEquals(64L).and(QueryCriteria("index", QueryCriteriaOperator.EQUAL, 1.5)))
        context.resetWork()
        // Mixed operand types retain the original index normalization path.
        assertEquals(listOf(64L), execute(automatic, query))
        assertEquals(1, query.resultsCount)
        assertEquals(1, context.indexLookups)
        assertEquals(127L, context.materializedPostings)
        assertEquals(0, context.fullTableScans)
        assertEquals(0, context.referenceFilterScans)
    }

    @Test
    fun `nullable indexed equality and range retain native null exclusion`() {
        manager.saveEntity<IManagedEntity>(NullIndexEntity().apply { id = "null-row" })
        val descriptor = context.getDescriptorForEntity(NullIndexEntity::class.java, "")
        val interactor = DefaultQueryInteractor(descriptor, manager, context)
        for (leaf in listOf(
            QueryCriteria("longIndex", QueryCriteriaOperator.EQUAL, null),
            QueryCriteria("longIndex", QueryCriteriaOperator.LESS_THAN, 2L)
        )) {
            val query = Query(NullIndexEntity::class.java,
                QueryCriteria("id", QueryCriteriaOperator.EQUAL, "null-row").and(leaf))
            context.resetWork()
            assertEquals(emptyList(), interactor.getReferencesForQuery<NullIndexEntity>(query).results.toList())
            assertEquals(0, query.resultsCount)
            assertEquals(1, context.indexLookups)
            assertEquals(0, context.fullTableScans)
            assertEquals(0, context.referenceFilterScans)
        }
    }

    @Test
    fun `date indexed equality preserves native equality for custom hydrated values`() {
        manager.saveEntity<IManagedEntity>(IndexedAndDateEntity().apply {
            id = 1L
            recordedAt = Date(1000L)
        })
        val descriptor = context.getDescriptorForEntity(IndexedAndDateEntity::class.java, "")
        val interactor = DefaultQueryInteractor(descriptor, manager, context)
        fun dateQuery() = Query(IndexedAndDateEntity::class.java,
            QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L)
                .and(QueryCriteria("recordedAt", QueryCriteriaOperator.EQUAL, Date(1000L))))
        val query = dateQuery()
        context.resetWork()
        val results = interactor.getReferencesForQuery<IndexedAndDateEntity>(query).results
        assertEquals(listOf(1L), results.map { (it as IndexedAndDateEntity).id })
        assertTrue((results.single() as IndexedAndDateEntity).recordedAt is Timestamp)
        assertEquals(1, query.resultsCount)
        assertEquals(1, context.indexLookups)
        assertEquals(1L, context.materializedPostings)
        assertEquals(0, context.fullTableScans)
        assertEquals(0, context.referenceFilterScans)

        // Native date indexes compare milliseconds. Timestamp.equals(Date) is false, so
        // replacing the index predicate with entity comparison would lose the matching row.
        val forced = DefaultQueryInteractorTestBridge.forceFullTable(descriptor, manager, context)
        assertEquals(emptyList(), forced.getReferencesForQuery<IndexedAndDateEntity>(dateQuery()).results.toList())
    }

    private fun assertSelectiveEquivalent(
        expectedIds: List<Long>,
        criteria: () -> QueryCriteria,
        configure: Query.() -> Unit = {}
    ) {
        val indexedQuery = query(criteria()).apply(configure)
        context.resetWork()
        val actual = execute(automatic, indexedQuery)
        assertSelectiveWork()
        val sorted = if (indexedQuery.queryOrders!!.single().isAscending) expectedIds.sorted() else expectedIds.sortedDescending()
        val page = sorted.drop(indexedQuery.firstRow).let {
            if (indexedQuery.maxResults > 0) it.take(indexedQuery.maxResults) else it
        }
        assertEquals(page, actual)
        assertEquals(expectedIds.size, indexedQuery.resultsCount)

        context.resetWork()
        val fullQuery = query(criteria()).apply(configure)
        assertEquals(actual, execute(fullTable, fullQuery))
        assertEquals(fullQuery.resultsCount, indexedQuery.resultsCount)
        assertEquals(1, context.fullTableScans)
    }

    private fun assertSelectiveWork(requireReferenceFilter: Boolean = true) {
        assertEquals(0, context.indexLookups, "Identifier candidates must avoid secondary index enumeration")
        assertEquals(0L, context.materializedPostings)
        assertEquals(0, context.fullTableScans, "The selective path must not substitute a full table scan")
        if (requireReferenceFilter) assertTrue(context.referenceFilterScans > 0)
    }

    private fun query(criteria: QueryCriteria) = Query(SelectIdentifierTestEntity::class.java, criteria, QueryOrder("id", true))

    private fun execute(interactor: DefaultQueryInteractor, query: Query): List<Long> =
        interactor.getReferencesForQuery<SelectIdentifierTestEntity>(query).results.map { (it as SelectIdentifierTestEntity).id }

    private fun idEquals(id: Long) = QueryCriteria("id", QueryCriteriaOperator.EQUAL, id)
    private fun idIn(ids: List<Long>) = QueryCriteria("id", QueryCriteriaOperator.IN, ids)
    private fun broadIndex() = QueryCriteria("index", QueryCriteriaOperator.EQUAL, 1)
    private fun conjunction(identifier: QueryCriteria, index: QueryCriteria, identifierFirst: Boolean) =
        if (identifierFirst) identifier.and(index) else index.and(identifier)

    private fun criteriaShape(criteria: QueryCriteria): List<Any?> = listOf(
        criteria.attribute, criteria.operator, criteria.value, criteria.isAnd, criteria.isOr,
        criteria.isNot, criteria.flip, criteria.subCriteria.map(::criteriaShape)
    )
}

@Entity
class IndexedAndDateEntity : ManagedEntity() {
    @Identifier
    @Attribute
    var id: Long = 0L

    @Index
    @Attribute
    var recordedAt: Date = Date(0L)

    override fun read(buffer: BufferStream, context: SchemaContext?) {
        super.read(buffer, context)
        recordedAt = Timestamp(recordedAt.time)
    }
}
