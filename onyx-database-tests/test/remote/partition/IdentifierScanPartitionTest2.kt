package remote.partition

/**
 * Created by timothy.osborn on 3/19/15.
 */

import category.RemoteServerTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.AttributeUpdate
import entities.partition.FullTablePartitionEntity
import org.junit.Assert
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
class IdentifierScanPartitionTest2 : BasePartitionTest() {

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

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L))
        query.partition = 3L

        val results = manager!!.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun bTestQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
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

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.IN, Arrays.asList(1L, 2L)))

        val results = manager!!.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 3)
    }

    @Test
    @Throws(OnyxException::class)
    fun cTestQueryFindQueryPartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 1L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L).and("partitionId", QueryCriteriaOperator.EQUAL, 3L))

        val results = manager!!.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun dTestDeleteQueryPartitionEntity() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 1L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager!!.executeDelete(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun bTestDeleteQueryPartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 1L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L))
        query.partition = 2L

        val results = manager!!.executeDelete(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun bTestDeleteQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val entity = FullTablePartitionEntity()
        entity.id = 1L
        entity.partitionId = 3L
        entity.indexVal = 5L

        save(entity)

        val entity2 = FullTablePartitionEntity()
        entity2.id = 1L
        entity2.partitionId = 2L
        entity2.indexVal = 5L

        save(entity2)

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L))

        val results = manager!!.executeDelete(query)
        Assert.assertTrue(results == 3) // Updated because we no longer delete the db before execution
    }


    @Test
    @Throws(OnyxException::class)
    fun dTestUpdateQueryPartitionEntity() {
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

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun bTestUpdateQueryPartitionEntityWithIndex() {
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

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))
        query.partition = 2L

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun bTestUpdatePartitionField() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 2L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 2L
        FullTablePartitionEntity2.partitionId = 2L
        FullTablePartitionEntity2.indexVal = 5L

        save(FullTablePartitionEntity2)

        val query = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("partitionId", 5L), AttributeUpdate("indexVal", 6L))

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 3)

        val query2 = Query(entities.partition.FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L))
        query2.partition = 5L
        val result = manager!!.executeQuery<Any>(query2)

        Assert.assertTrue(result.size == 1)
    }


}
