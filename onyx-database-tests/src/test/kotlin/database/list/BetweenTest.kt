package database.list

import com.onyx.persistence.query.between
import com.onyx.persistence.query.from
import database.base.PrePopulatedDatabaseTest
import entities.AllAttributeForFetch
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import com.onyx.persistence.query.*
import com.onyx.persistence.IManagedEntity
import entities.AllAttributeForFetchSequenceGen
import entities.partition.IndexPartitionEntity

@RunWith(Parameterized::class)
class BetweenTest(override var factoryClass: KClass<*>) : PrePopulatedDatabaseTest(factoryClass) {

    @Test
    fun testStringIDBetween() {
        val results = manager.from<AllAttributeForFetch>()
            .where("id" between Pair("FIRST", "LAST"))
            .list<AllAttributeForFetch>()
        assertEquals(6, results.size, "Expected 6 results from query")
    }

    @Test
    fun testStringIDNotBetween() {
        val results = manager.from<AllAttributeForFetch>()
            .where(!("id" between Pair("FIRST", "LAST")))
            .list<AllAttributeForFetch>()
        assertEquals(0, results.size, "Expected no results outside the range")
    }

    @Test
    fun testDateBetween() {
        val start = Date(1001)
        val end = Date(1022)
        val results = manager.from<AllAttributeForFetch>()
            .where("dateValue" between Pair(start, end))
            .list<AllAttributeForFetch>()
        assertEquals(3, results.size, "Expected 3 results")
    }

    @Test
    fun testDateNotBetween() {
        val start = Date(1001)
        val end = Date(1022)
        val results = manager.from<AllAttributeForFetch>()
            .where(!("dateValue" between Pair(start, end)))
            .list<AllAttributeForFetch>()
        assertEquals(3, results.size, "Expected 3 results outside the date range")
    }

    @Test
    fun testSequenceGenIndexBetween() {
        manager.from(AllAttributeForFetchSequenceGen::class).delete()
        val entities = (1..5).map {
            AllAttributeForFetchSequenceGen().apply { indexVal = it }
        }
        manager.saveEntities(entities)
        val results = manager.from<AllAttributeForFetchSequenceGen>()
            .where("indexVal" between Pair(2, 4))
            .list<AllAttributeForFetchSequenceGen>()
        assertEquals(3, results.size, "Expected 3 results for indexed between")
    }

    @Test
    fun testSequenceGenIndexNotBetween() {
        manager.from(AllAttributeForFetchSequenceGen::class).delete()
        val entities = (1..5).map {
            AllAttributeForFetchSequenceGen().apply { indexVal = it }
        }
        manager.saveEntities(entities)
        val results = manager.from<AllAttributeForFetchSequenceGen>()
            .where(!("indexVal" between Pair(2, 4)))
            .list<AllAttributeForFetchSequenceGen>()
        assertEquals(2, results.size, "Expected 2 results for indexed not between")
    }

    @Test
    fun testPartitionIndexBetween() {
        manager.from(IndexPartitionEntity::class).delete()
        val e1 = IndexPartitionEntity().apply { id = 1L; partitionId = 1L; indexVal = 1L }
        val e2 = IndexPartitionEntity().apply { id = 2L; partitionId = 1L; indexVal = 2L }
        val e3 = IndexPartitionEntity().apply { id = 3L; partitionId = 2L; indexVal = 2L }
        manager.saveEntity<IManagedEntity>(e1)
        manager.saveEntity<IManagedEntity>(e2)
        manager.saveEntity<IManagedEntity>(e3)
        val criteria = QueryCriteria("indexVal", QueryCriteriaOperator.BETWEEN, Pair(1L, 2L))
        val query = Query(IndexPartitionEntity::class.java, criteria)
        query.partition = 1L
        val results = manager.executeQuery<IndexPartitionEntity>(query)
        assertEquals(2, results.size, "Expected 2 results for partition index between")
    }

    @Test
    fun testPartitionIndexNotBetween() {
        manager.from(IndexPartitionEntity::class).delete()
        val e1 = IndexPartitionEntity().apply { id = 1L; partitionId = 1L; indexVal = 1L }
        val e2 = IndexPartitionEntity().apply { id = 2L; partitionId = 1L; indexVal = 2L }
        val e3 = IndexPartitionEntity().apply { id = 3L; partitionId = 2L; indexVal = 2L }
        manager.saveEntity<IManagedEntity>(e1)
        manager.saveEntity<IManagedEntity>(e2)
        manager.saveEntity<IManagedEntity>(e3)
        val criteria = QueryCriteria("indexVal", QueryCriteriaOperator.NOT_BETWEEN, Pair(1L, 2L))
        val query = Query(IndexPartitionEntity::class.java, criteria)
        query.partition = 1L
        val results = manager.executeQuery<IndexPartitionEntity>(query)
        assertEquals(0, results.size, "Expected no results for partition index not between")
    }
}
