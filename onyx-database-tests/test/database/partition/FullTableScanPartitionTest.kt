package database.partition

import com.onyx.extension.from
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.DatabaseBaseTest
import entities.partition.FullTablePartitionEntity
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
class FullTableScanPartitionTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun removeData() {
        manager.from(FullTablePartitionEntity::class).delete()
    }

    @Test
    fun aTestSavePartitionEntityWithIndex() {
        val fullTablePartitionEntity = FullTablePartitionEntity()
        fullTablePartitionEntity.id = 1L
        fullTablePartitionEntity.partitionId = 3L
        fullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity)
    }

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

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.partition = 2L

        val results = manager.executeQuery<Any>(query)
        assertEquals(1, results.size, "Expected 1 result(s)")
    }

    @Test
    fun bTestQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(FullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))

        val results = manager.executeQuery<Any>(query)
        assertEquals(2, results.size, "Expected 2 result(s)")
    }

    @Test
    fun cTestQueryFindQueryPartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(FullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager.executeQuery<Any>(query)
        assertEquals(1, results.size, "Expected 1 result(s)")
    }

    @Test
    fun dTestDeleteQueryPartitionEntity() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(FullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager.executeDelete(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestDeleteQueryPartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(FullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
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
        entity2.id = 2L
        entity2.partitionId = 2L
        entity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(entity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))

        val results = manager.executeDelete(query)
        assertEquals(2, results, "Expected 2 result(s)")
    }


    @Test
    fun dTestUpdateQueryPartitionEntity() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(FullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestUpdateQueryPartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(FullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))
        query.partition = 2L

        val results = manager.executeUpdate(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestUpdateQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(FullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(2, results, "Expected 2 result(s)")
    }

    @Test
    fun bTestUpdatePartitionField() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(FullTablePartitionEntity)

        val fullTablePartitionEntity2 = FullTablePartitionEntity()
        fullTablePartitionEntity2.id = 2L
        fullTablePartitionEntity2.partitionId = 2L
        fullTablePartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(fullTablePartitionEntity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("partitionId", 5L), AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(2, results, "Expected 2 result(s)")

        val query2 = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 6L))
        query2.partition = 5L
        val result = manager.executeQuery<Any>(query2)

        assertEquals(2, result.size, "Expected 2 result(s)")
    }
}
