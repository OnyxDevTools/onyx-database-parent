package database.partition

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.CacheManagerFactory
import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.Execution
import entities.partition.IndexPartitionEntity
import org.junit.Assume
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
        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L
        assertNotNull(manager.saveEntity<IManagedEntity>(indexPartitionEntity), "Saved partition entity should not be null")
    }

    @Test
    fun bTestQueryPartitionEntityWithIndex() {
        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity)

        val indexPartitionEntity2 = IndexPartitionEntity()
        indexPartitionEntity2.id = 2L
        indexPartitionEntity2.partitionId = 2L
        indexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.partition = 2L

        val results = manager.executeQuery<Any>(query)
        assertEquals(1, results.size, "Expected 1 result(s)")
    }

    @Test
    fun bTestQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity)

        val indexPartitionEntity2 = IndexPartitionEntity()
        indexPartitionEntity2.id = 2L
        indexPartitionEntity2.partitionId = 2L
        indexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))

        val results = manager.executeQuery<Any>(query)
        assertEquals(2, results.size, "Expected 2 result(s)")
    }

    @Test
    fun cTestQueryFindQueryPartitionEntityWithIndex() {
        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity)

        val indexPartitionEntity2 = IndexPartitionEntity()
        indexPartitionEntity2.id = 2L
        indexPartitionEntity2.partitionId = 2L
        indexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager.executeQuery<Any>(query)
        assertEquals(1, results.size, "Expected 1 result(s)")
    }

    @Test
    fun dTestDeleteQueryPartitionEntity() {
        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity)

        val indexPartitionEntity2 = IndexPartitionEntity()
        indexPartitionEntity2.id = 2L
        indexPartitionEntity2.partitionId = 2L
        indexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager.executeDelete(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestDeleteQueryPartitionEntityWithIndex() {
        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity)

        val indexPartitionEntity2 = IndexPartitionEntity()
        indexPartitionEntity2.id = 2L
        indexPartitionEntity2.partitionId = 2L
        indexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.partition = 2L

        val results = manager.executeDelete(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestDeleteQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity)

        val indexPartitionEntity2 = IndexPartitionEntity()
        indexPartitionEntity2.id = 2L
        indexPartitionEntity2.partitionId = 2L
        indexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))

        val results = manager.executeDelete(query)
        assertEquals(2, results, "Expected 2 result(s)")
    }


    @Test
    fun dTestUpdateQueryPartitionEntity() {
        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity)

        val indexPartitionEntity2 = IndexPartitionEntity()
        indexPartitionEntity2.id = 2L
        indexPartitionEntity2.partitionId = 2L
        indexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestUpdateQueryPartitionEntityWithIndex() {
        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity)

        val indexPartitionEntity2 = IndexPartitionEntity()
        indexPartitionEntity2.id = 2L
        indexPartitionEntity2.partitionId = 2L
        indexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))
        query.partition = 2L

        val results = manager.executeUpdate(query)
        assertEquals(1, results, "Expected 1 result(s)")
    }

    @Test
    fun bTestUpdateQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity)

        val indexPartitionEntity2 = IndexPartitionEntity()
        indexPartitionEntity2.id = 2L
        indexPartitionEntity2.partitionId = 2L
        indexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(2, results, "Expected 2 result(s)")
    }

    @Test
    fun bTestUpdatePartitionField() {

        Assume.assumeFalse("Ignore for In Memory", factory is CacheManagerFactory)

        val indexPartitionEntity = IndexPartitionEntity()
        indexPartitionEntity.id = 1L
        indexPartitionEntity.partitionId = 3L
        indexPartitionEntity.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity)

        val indexPartitionEntity2 = IndexPartitionEntity()
        indexPartitionEntity2.id = 2L
        indexPartitionEntity2.partitionId = 2L
        indexPartitionEntity2.indexVal = 5L

        manager.saveEntity<IManagedEntity>(indexPartitionEntity2)

        val query = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("partitionId", 5L), AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        assertEquals(2, results, "Expected 2 result(s)")

        val query2 = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 6L))
        query2.partition = 5L
        val result = manager.executeQuery<Any>(query2)

        assertEquals(2, result.size, "Expected 2 result(s)")
    }

    @Test
    fun testExecutionPartition() {

        for(p in 0..10) {
            for (i in 0..100) {
                manager.saveEntity(
                    Execution().apply {
                        this.executionId = "1$randomString"
                        this.tradeSimulationId = p
                        this.isTop50 = random.nextBoolean()
                        this.isGain = random.nextBoolean()
                        this.bullScore = randomInteger.toDouble()
                        this.purchaseTime = Date(random.nextLong())
                        this.profitLoss = random.nextFloat()
                        this.marketMean = random.nextDouble()
                        this.volume = random.nextDouble()
                        this.netChange = random.nextDouble()
                    }
                )
            }
        }

        val results = manager.select(
                                count("executionId"),
                                "tradeSimulationId",
                                avg("netChange"),
                                sum("profitLoss"),
                                avg("bullScore"),
                                avg("marketMean"),
                                avg("volume"),
                                avg("rsi"),
                                avg("marketGapPercent"),
                                "weekYear",
                                "strategy",
                                "dayYear",
                                max("purchaseTime")
                            ).from(Execution::class)
                            .where("netChange" gt 0.0)
                            .and("netChange" lt Double.MAX_VALUE / 2)
                            .and("tradeSimulationId" gt 5)
                            .orderBy("tradeSimulationId".desc(), "purchaseTime".desc())
                            .groupBy("tradeSimulationId")
                            .list<Any>()

        assertEquals(results.size, 5)
    }
}
