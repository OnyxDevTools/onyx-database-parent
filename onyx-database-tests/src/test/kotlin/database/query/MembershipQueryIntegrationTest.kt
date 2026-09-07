package database.query

import com.onyx.diskmap.store.StoreType
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
import com.onyx.persistence.query.QueryPartitionMode
import entities.partition.FullTablePartitionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class MembershipQueryIntegrationTest {
    private lateinit var directory: Path
    private lateinit var factory: EmbeddedPersistenceManagerFactory
    private lateinit var manager: PersistenceManager

    @Before
    fun initialize() {
        directory = Files.createTempDirectory("onyx-membership-query-")
        factory = EmbeddedPersistenceManagerFactory(directory.toString()).apply {
            storeType = StoreType.IN_MEMORY
            initialize()
        }
        manager = factory.persistenceManager
        for (id in 1L..64L) save(id, if (id == 64L) null else id.toInt())
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
    fun `full scans counts lazy pages and indexed residuals agree on membership`() {
        val expected = listOf(1L, 3L, 5L)
        for (operand in listOf(listOf(1, 3, 5), intArrayOf(1, 3, 5), arrayOf(1, 3, 5))) {
            for (indexed in listOf(false, true)) {
                val criterion = QueryCriteria("value", QueryCriteriaOperator.IN, operand)
                val query = query(if (indexed) QueryCriteria("bucket", QueryCriteriaOperator.EQUAL, 1).and(criterion) else criterion)
                assertEquals(expected, ids(query))
                assertEquals(3L, manager.countForQuery(query))
                query.maxResults = 2
                assertEquals(2, manager.executeQuery<MembershipEntity>(query).size)
                assertEquals(3, query.resultsCount)
                assertEquals(2, manager.executeLazyQuery<MembershipEntity>(query).size)
                assertEquals(3, query.resultsCount)
            }
        }
    }

    @Test
    fun `not in preserves null mixed numeric and empty operand semantics`() {
        assertEquals(listOf(1L, 3L, 64L), ids(query(QueryCriteria("value", QueryCriteriaOperator.IN, listOf(1L, 3.9, null)))))
        val excluded = setOf(1L, 3L, 64L)
        assertEquals((1L..64L).filterNot { it in excluded }, ids(query(QueryCriteria("value", QueryCriteriaOperator.NOT_IN, listOf(1L, 3.9, null)))))
        assertEquals(emptyList(), ids(query(QueryCriteria("value", QueryCriteriaOperator.IN, emptyList<Int>()))))
        assertEquals((1L..64L).toList(), ids(query(QueryCriteria("value", QueryCriteriaOperator.NOT_IN, emptyList<Int>()))))
    }

    @Test
    fun `reused queries observe edited lists arrays and replacement criteria operands`() {
        val criterion = QueryCriteria("value", QueryCriteriaOperator.IN, mutableListOf(1, 3))
        val query = query(criterion)
        assertEquals(listOf(1L, 3L), ids(query))
        @Suppress("UNCHECKED_CAST")
        (criterion.value as MutableList<Int>)[0] = 5
        assertEquals(listOf(3L, 5L), ids(query))
        val array = intArrayOf(2, 4)
        criterion.value = array
        assertEquals(listOf(2L, 4L), ids(query))
        array[0] = 6
        assertEquals(listOf(4L, 6L), ids(query))
        criterion.value = listOf(7)
        assertEquals(listOf(7L), ids(query))
    }

    @Test
    fun `cached membership remains correct after inserts updates and deletes`() {
        val query = query(QueryCriteria("value", QueryCriteriaOperator.IN, listOf(1, 3))).apply { cache = true }
        assertEquals(listOf(1L, 3L), ids(query))
        save(100L, 1)
        assertEquals(listOf(1L, 3L, 100L), ids(query))
        save(100L, 2)
        assertEquals(listOf(1L, 3L), ids(query))
        manager.deleteEntity(manager.findById(MembershipEntity::class.java, 1L)!!)
        assertEquals(listOf(3L), ids(query))
    }

    @Test
    fun `parallel partition scans share membership and refresh between executions`() {
        for (partition in 1L..4L) {
            for (value in 1L..16L) {
                manager.saveEntity(FullTablePartitionEntity().apply {
                    partitionId = partition
                    indexVal = value
                })
            }
        }
        val values = longArrayOf(1L, 3L, 5L)
        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.IN, values)).apply {
            partition = QueryPartitionMode.ALL
        }
        assertEquals(12L, manager.countForQuery(query))
        values[0] = 100L
        assertEquals(8L, manager.countForQuery(query))
    }

    private fun save(id: Long, value: Int?) = manager.saveEntity(MembershipEntity().apply {
        this.id = id
        this.value = value
        bucket = (id % 2L).toInt()
    })

    private fun query(criterion: QueryCriteria) = Query(MembershipEntity::class.java, criterion).apply { cache = false }
    private fun ids(query: Query) = manager.executeQuery<MembershipEntity>(query).map { it.id }.sorted()
}

@Entity
class MembershipEntity : ManagedEntity() {
    @Identifier
    @Attribute
    var id: Long = 0L

    @Attribute
    @Index
    var bucket: Int = 0

    @Attribute
    var value: Int? = null
}
