package embedded.partition

/**
 * Created by timothy.osborn on 3/19/15.
 */

import category.EmbeddedDatabaseTests
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

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(EmbeddedDatabaseTests::class)
class IdentifierScanPartitionTest : BasePartitionTest() {

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

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L))
        query.partition = 3L

        val results = manager.executeQuery<Any>(query)
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

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.IN, Arrays.asList(1L, 2L)))

        val results = manager.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 2)
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

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L).and("partitionId", QueryCriteriaOperator.EQUAL, 3L))

        val results = manager.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 1)
    }

    @Test
    @Throws(OnyxException::class)
    fun caTestQueryFindQueryPartitionEntityWithIndex() {
        val FullTablePartitionEntity = FullTablePartitionEntity()
        FullTablePartitionEntity.id = 1L
        FullTablePartitionEntity.partitionId = 3L
        FullTablePartitionEntity.indexVal = 5L

        save(FullTablePartitionEntity)

        var query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L).and("partitionId", QueryCriteriaOperator.EQUAL, 3L).and("indexVal", QueryCriteriaOperator.LESS_THAN, 6L))

        // By adding this, it will test our cache and ensure we are properly handling partitions
        var results: List<*> = manager.executeQuery<Any>(query)

        val FullTablePartitionEntity2 = FullTablePartitionEntity()
        FullTablePartitionEntity2.id = 1L
        FullTablePartitionEntity2.partitionId = 3L
        FullTablePartitionEntity2.indexVal = 6L

        save(FullTablePartitionEntity2)

        query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L).and("partitionId", QueryCriteriaOperator.EQUAL, 3L).and("indexVal", QueryCriteriaOperator.LESS_THAN, 6L))

        results = manager.executeQuery<Any>(query)
        Assert.assertTrue(results.size == 0)
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

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))

        val results = manager.executeDelete(query)
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

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1L))
        query.partition = 2L

        val results = manager.executeDelete(query)
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

        val results = manager.executeDelete(query)
        Assert.assertTrue(results == 2)
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

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L).and("partitionId", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
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

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("indexVal", 6L))
        query.partition = 2L

        val results = manager.executeUpdate(query)
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

        val query = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L))
        query.updates = Arrays.asList(AttributeUpdate("partitionId", 5L), AttributeUpdate("indexVal", 6L))

        val results = manager.executeUpdate(query)
        Assert.assertTrue(results == 2)

        val query2 = Query(FullTablePartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L))
        query2.partition = 5L
        val result = manager.executeQuery<Any>(query2)

        Assert.assertTrue(result.size == 1)
    }


}
