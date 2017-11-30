package database.partition

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.DatabaseBaseTest
import entities.partition.FullTablePartitionEntity
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class IdentifierScanPartitionTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun bTestQueryPartitionEntityWithIndex() {
        val fullTablePartitionEntity = FullTablePartitionEntity()
        fullTablePartitionEntity.id = 1L
        fullTablePartitionEntity.partitionId = 3L
        fullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L))
        query.partition = 3L

        val results = manager.executeQuery<Any>(query)
        assertEquals(1, results.size, "Expected 1 result(s)")
    }

    @Test
    fun bTestQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val fullTablePartitionEntity = FullTablePartitionEntity()
        fullTablePartitionEntity.id = 1L
        fullTablePartitionEntity.partitionId = 3L
        fullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.IN, Arrays.asList(1L, 2L)))

        val results = manager.executeQuery<Any>(query)
        assertEquals(2, results.size, "Expected 2 result(s)")
    }

    @Test
    fun cTestQueryFindQueryPartitionEntityWithIndex() {
        val fullTablePartitionEntity = FullTablePartitionEntity()
        fullTablePartitionEntity.id = 1L
        fullTablePartitionEntity.partitionId = 3L
        fullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 1L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L).and("partitionId", QueryCriteriaOperator.EQUAL, 3L))

        val results = manager.executeQuery<Any>(query)
        assertEquals(1, results.size, "Expected 1 result(s)")
    }

    @Test
    fun caTestQueryFindQueryPartitionEntityWithIndex() {
        val fullTablePartitionEntity = FullTablePartitionEntity()
        fullTablePartitionEntity.id = 1L
        fullTablePartitionEntity.partitionId = 3L
        fullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity)

        var query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L).and("partitionId", QueryCriteriaOperator.EQUAL, 3L).and("indexVal", QueryCriteriaOperator.LESS_THAN, 6L))

        // By adding this, it will test our cache and ensure we are properly handling partitions
        manager.executeQuery<IManagedEntity>(query)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 1L
        fullTablePartitionEntity2.partitionId = 3L
        fullTablePartitionEntity2.indexVal = 6L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L).and("partitionId", QueryCriteriaOperator.EQUAL, 3L).and("indexVal", QueryCriteriaOperator.LESS_THAN, 6L))

        val results = manager.executeQuery<IManagedEntity>(query)
        assertTrue(results.isEmpty(), "Partitioned entity should have been deleted")
    }

    @Test
    fun dTestDeleteQueryPartitionEntity() {
        val fullTablePartitionEntity = FullTablePartitionEntity()
        fullTablePartitionEntity.id = 1L
        fullTablePartitionEntity.partitionId = 3L
        fullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 1L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager.executeDelete(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestDeleteQueryPartitionEntityWithIndex() {
        val fullTablePartitionEntity = FullTablePartitionEntity()
        fullTablePartitionEntity.id = 1L
        fullTablePartitionEntity.partitionId = 3L
        fullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 1L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L))
        query.partition = 2L

        val results = manager.executeDelete(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestDeleteQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val entity = FullTablePartitionEntity()
        entity.id = 1L
        entity.partitionId = 3L
        entity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(entity)

        val entity2 = FullTablePartitionEntity()
        entity2.id = 1L
        entity2.partitionId = 2L
        entity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(entity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L))

        val results = manager.executeDelete(query)
        assertEquals(2, results, "Expected 2 result(s)")
    }


    @Test
    fun dTestUpdateQueryPartitionEntity() {
        val fullTablePartitionEntity = FullTablePartitionEntity()
        fullTablePartitionEntity.id = 1L
        fullTablePartitionEntity.partitionId = 3L
        fullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestUpdateQueryPartitionEntityWithIndex() {
        val fullTablePartitionEntity = FullTablePartitionEntity()
        fullTablePartitionEntity.id = 1L
        fullTablePartitionEntity.partitionId = 3L
        fullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))
        query.partition = 2L

        val results = manager.executeUpdate(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestUpdatePartitionField() {
        val fullTablePartitionEntity = FullTablePartitionEntity()
        fullTablePartitionEntity.id = 2L
        fullTablePartitionEntity.partitionId = 3L
        fullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("partitionId", 5L), AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(2, results, "Expected 2 result(s)")

        val query2 = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L))
        query2.partition = 5L
        val result = manager.executeQuery<Any>(query2)

        assertEquals(1, result.size, "Expected 1 result(s)")
    }
}
