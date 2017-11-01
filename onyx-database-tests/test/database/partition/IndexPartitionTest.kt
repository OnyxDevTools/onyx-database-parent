package database.partition

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.DatabaseBaseTest
import entities.partition.IndexPartitionEntity
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class IndexPartitionTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun aTestSavePartitionEntityWithIndex() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L
        assertNotNull(manager.saveEntity<IManagedEntity>(IndexPartitionEntity), "Saved partition entity should not be null")
    }

    @Test
    fun bTestQueryPartitionEntityWithIndex() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.partition = 2L

        val results = manager.executeQuery<Any>(query)
        assertEquals(1, results.size, "Expected 1 result(s)")
    }

    @Test
    fun bTestQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))

        val results = manager.executeQuery<Any>(query)
        assertEquals(2, results.size, "Expected 2 result(s)")
    }

    @Test
    fun cTestQueryFindQueryPartitionEntityWithIndex() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager.executeQuery<Any>(query)
        assertEquals(1, results.size, "Expected 1 result(s)")
    }

    @Test
    fun dTestDeleteQueryPartitionEntity() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager.executeDelete(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestDeleteQueryPartitionEntityWithIndex() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.partition = 2L

        val results = manager.executeDelete(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestDeleteQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))

        val results = manager.executeDelete(query)
        assertEquals(2, results, "Expected 2 result(s)")
    }


    @Test
    fun dTestUpdateQueryPartitionEntity() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestUpdateQueryPartitionEntityWithIndex() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))
        query.partition = 2L

        val results = manager.executeUpdate(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestUpdateQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(2, results, "Expected 2 result(s)")
    }

    @Test
    fun bTestUpdatePartitionField() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(IndexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("partitionId", 5L), AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(2, results, "Expected 2 result(s)")

        val query2 = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 6L))
        query2.partition = 5L
        val result = manager.executeQuery<Any>(query2)

        assertEquals(2, result.size, "Expected 2 result(s)")
    }
}
