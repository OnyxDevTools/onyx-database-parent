package database.query

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.from
import com.onyx.persistence.query.gt
import database.base.DatabaseBaseTest
import entities.SimpleEntity
import entities.partition.BasicPartitionEntity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class QueryCountTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun removeTestData() {
        manager.from(SimpleEntity::class).delete()
        manager.from(BasicPartitionEntity::class).delete()
    }

    /**
     * Tests an entity without a partition
     */
    @Test
    fun testQueryCountForNonPartitionEntity() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDF"

        manager.saveEntity<IManagedEntity>(simpleEntity)
        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDF"
        manager.deleteEntity(simpleEntity)

        assertEquals(1, manager.from(SimpleEntity::class).count(), "Expected 1 result")
    }

    /**
     * Tests an entity with a partition.  The count uses a specific partition
     */
    @Test
    fun testQueryCountForPartitionEntity() {
        var basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 3L
        basicPartitionEntity.id = 1L

        manager.saveEntity<IManagedEntity>(basicPartitionEntity)

        basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 4L
        basicPartitionEntity.id = 2L

        manager.saveEntity<IManagedEntity>(basicPartitionEntity)

        val query = Query()
        query.entityType = BasicPartitionEntity::class.java
        query.partition = 3L
        assertEquals(1, manager.countForQuery(query), "Expected 1 result")
    }


    /**
     * Tests an entity with a partition whereas the query addresses the entire
     * partition set.
     */
    @Test
    fun testQueryCountForAllPartitions() {
        var basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 3L
        basicPartitionEntity.id = 1L

        manager.saveEntity<IManagedEntity>(basicPartitionEntity)

        basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 4L
        basicPartitionEntity.id = 2L

        manager.saveEntity<IManagedEntity>(basicPartitionEntity)

        assertEquals(2, manager.from(BasicPartitionEntity::class).count(), "Expected 2 results")
    }

    /**
     * Tests a custom query rather than the entire data set
     */
    @Test
    fun testQueryCountForCustomQuery() {
        var basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 3L
        basicPartitionEntity.id = 1L

        manager.saveEntity<IManagedEntity>(basicPartitionEntity)

        basicPartitionEntity = BasicPartitionEntity()
        basicPartitionEntity.partitionId = 4L
        basicPartitionEntity.id = 2L

        manager.saveEntity<IManagedEntity>(basicPartitionEntity)

        assertEquals(1, manager.from(BasicPartitionEntity::class).where("id" gt 1L).count(), "Expected 1 result")
    }
}
