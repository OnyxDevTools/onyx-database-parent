package remote.partition

import category.RemoteServerTests
import com.onyx.application.impl.DatabaseServer
import com.onyx.exception.OnyxException
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.AttributeUpdate
import entities.partition.FullTablePartitionEntity
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters

import java.util.Arrays

/**
 * Created by timothy.osborn on 2/12/15.
 */

@Category(RemoteServerTests::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FullTableScanPartitionTest : BasePartitionTest() {

    @Test
    fun aTestSavePartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)
    }

    @Test
    @Throws(OnyxException::class)
    fun bTestQueryPartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 2L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.partition = 2L

        val results = manager!!.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun cTestQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 2L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))

        val results = manager!!.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 2)
    }

    @Test
    @Throws(OnyxException::class)
    fun dTestQueryFindQueryPartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 2L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager!!.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun eTestDeleteQueryPartitionEntity() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 2L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager!!.executeDelete(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun fTestDeleteQueryPartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 2L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.partition = 2L

        val results = manager!!.executeDelete(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun gTestDeleteQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        manager!!.executeDelete(query)

        val entity = FullTablePartitionEntity()
        entity.id = 1L
        entity.partitionId = 3L
        entity.indexVal = 5L

        save(entity)

        val entity2 = FullTablePartitionEntity()
        entity2.id = 2L
        entity2.partitionId = 2L
        entity2.indexVal = 5L

        save(entity2)

        val results = manager!!.executeDelete(query)
        Assert.assertEquals(2, results.toLong())
    }


    @Test
    @Throws(OnyxException::class)
    fun hTestUpdateQueryPartitionEntity() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 2L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun iTestUpdateQueryPartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 2L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))
        query.partition = 2L

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun jTestUpdateQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 2L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 2)
    }

    @Test
    @Throws(OnyxException::class)
    fun kTestUpdatePartitionField() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 2L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("partitionId", 5L), AttributeUpdate("indexVal", 6L))

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 2)

        val query2 = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 6L))
        query2.partition = 5L
        val result = manager!!.executeQuery<Any>(query2)

        Assert.assertTrue(result.size == 2)
    }


}
