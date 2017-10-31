package remote.partition

import category.RemoteServerTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.AttributeUpdate
import entities.partition.IndexPartitionEntity
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
class IndexPartitionTest : BasePartitionTest() {

    @Test
    fun aTestSavePartitionEntityWithIndex() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)
    }

    @Test
    @Throws(OnyxException::class)
    fun bTestQueryPartitionEntityWithIndex() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        save(IndexPartitionEntity2)

        val query = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.partition = 2L

        val results = manager!!.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun cTestQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        save(IndexPartitionEntity2)

        val query = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))

        val results = manager!!.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 2)
    }

    @Test
    @Throws(OnyxException::class)
    fun dTestQueryFindQueryPartitionEntityWithIndex() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        save(IndexPartitionEntity2)

        val query = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager!!.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun eTestDeleteQueryPartitionEntity() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        save(IndexPartitionEntity2)

        val query = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager!!.executeDelete(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun fTestDeleteQueryPartitionEntityWithIndex() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        save(IndexPartitionEntity2)

        val query = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.partition = 2L

        val results = manager!!.executeDelete(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun gTestDeleteQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        save(IndexPartitionEntity2)

        val query = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))

        val results = manager!!.executeDelete(query)
        Assert.assertTrue(results == 2)
    }


    @Test
    @Throws(OnyxException::class)
    fun hTestUpdateQueryPartitionEntity() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        save(IndexPartitionEntity2)

        val query = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun iTestUpdateQueryPartitionEntityWithIndex() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        save(IndexPartitionEntity2)

        val query = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))
        query.partition = 2L

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun jTestUpdateQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        save(IndexPartitionEntity2)

        val query = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 2)
    }

    @Test
    @Throws(OnyxException::class)
    fun kTestUpdatePartitionField() {
        val IndexPartitionEntity = IndexPartitionEntity()
        IndexPartitionEntity.id = 1L
        IndexPartitionEntity.partitionId = 3L
        IndexPartitionEntity.indexVal = 5L

        save(IndexPartitionEntity)

        val IndexPartitionEntity2 = IndexPartitionEntity()
        IndexPartitionEntity2.id = 2L
        IndexPartitionEntity2.partitionId = 2L
        IndexPartitionEntity2.indexVal = 5L

        save(IndexPartitionEntity2)

        val query = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5L))
        query.updates = Arrays.asList(AttributeUpdate("partitionId", 5L), AttributeUpdate("indexVal", 6L))

        val results = manager!!.executeUpdate(query)
        Assert.assertTrue(results == 2)

        val query2 = Query(entities.partition.IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 6L))
        query2.partition = 5L
        val result = manager!!.executeQuery<Any>(query2)

        Assert.assertTrue(result.size == 2)
    }

}