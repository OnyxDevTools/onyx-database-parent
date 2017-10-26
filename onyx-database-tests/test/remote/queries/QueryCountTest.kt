package remote.queries

import category.RemoteServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.QueryPartitionMode
import entities.SimpleEntity
import entities.partition.BasicPartitionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import remote.base.RemoteBaseTest

import java.io.IOException

/**
 * This test will address the countForQuery() method
 * within the PersistenceManager API added in 1.3.0
 */
@Category(RemoteServerTests::class)
class QueryCountTest : RemoteBaseTest() {
    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(InitializationException::class)
    fun before() {
        initialize()
    }

    /**
     * Tests an entity without a partition
     */
    @Test
    @Throws(OnyxException::class)
    fun testQueryCountForNonPartitionEntity() {
        var query = Query()
        query.entityType = SimpleEntity::class.java
        manager!!.executeDelete(query)

        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDF"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDFL"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDF"
        manager!!.deleteEntity(simpleEntity)

        query = Query()
        query.entityType = SimpleEntity::class.java
        assert(manager!!.countForQuery(query) == 1L)
    }

    /**
     * Tests an entity with a partition.  The count uses a specific partition
     */
    @Test
    @Throws(OnyxException::class)
    fun testQueryCountForPartitionEntity() {
        var basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 3L
        basicPartitionEntity.id = 1L

        manager!!.saveEntity<IManagedEntity>(basicPartitionEntity)

        basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 4L
        basicPartitionEntity.id = 2L

        manager!!.saveEntity<IManagedEntity>(basicPartitionEntity)

        val query = Query()
        query.entityType = BasicPartitionEntity::class.java
        query.partition = 3L
        assert(manager!!.countForQuery(query) == 1L)
    }


    /**
     * Tests an entity with a partition whereas the query addresses the entire
     * partition set.
     */
    @Test
    @Throws(OnyxException::class)
    fun testQueryCountForAllPartitions() {

        var query = Query()
        query.entityType = BasicPartitionEntity::class.java
        query.partition = QueryPartitionMode.ALL
        manager!!.executeDelete(query)

        var basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 3L
        basicPartitionEntity.id = 1L

        manager!!.saveEntity<IManagedEntity>(basicPartitionEntity)

        basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 4L
        basicPartitionEntity.id = 2L

        manager!!.saveEntity<IManagedEntity>(basicPartitionEntity)

        query = Query()
        query.entityType = BasicPartitionEntity::class.java
        query.partition = QueryPartitionMode.ALL
        assert(manager!!.countForQuery(query) == 2L)
    }

    /**
     * Tests a custom query rather than the entire data set
     */
    @Test
    @Throws(OnyxException::class)
    fun testQueryCountForCustomQuery() {

        var query = Query()
        query.entityType = BasicPartitionEntity::class.java
        query.partition = QueryPartitionMode.ALL
        manager!!.executeDelete(query)

        var basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 3L
        basicPartitionEntity.id = 1L

        manager!!.saveEntity<IManagedEntity>(basicPartitionEntity)

        basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 4L
        basicPartitionEntity.id = 2L

        manager!!.saveEntity<IManagedEntity>(basicPartitionEntity)

        query = Query(BasicPartitionEntity::class.java, QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 1L))
        assert(manager!!.countForQuery(query) == 1L)
    }
}
