package database.query

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.from
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
class LazyQueryTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun removeTestData() {
        manager.from(SimpleEntity::class).delete()
        manager.from(BasicPartitionEntity::class).delete()
    }

    /**
     * Tests an entity without a partition
     */
    @Test
    fun testLazyQuery() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDF"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL3"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assertEquals(3, manager.from(SimpleEntity::class).lazy<IManagedEntity>().count(), "Expected 3 results")
    }

    /**
     * Tests an entity without a partition
     */
    @Test
    fun testLazyQueryFirstRow() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDF"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL3"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assertEquals(1, manager.from(SimpleEntity::class).first(2).lazy<IManagedEntity>().count(), "Expected 3 results")
    }

    /**
     * Tests an entity without a partition
     */
    @Test
    fun testLazyQueryFirstRowOOB() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDF"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL3"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assertEquals(0, manager.from(SimpleEntity::class).first(4).lazy<IManagedEntity>().count(), "Expected 0  results")
    }

    /**
     * Tests an entity without a partition
     */
    @Test
    fun testLazyQueryFirstRowOOBWithMaxResults() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDF"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL3"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assertEquals(0, manager.from(SimpleEntity::class).first(4).limit(2).lazy<IManagedEntity>().count(), "Expected 0  results")
    }

    /**
     * Tests an entity without a partition
     */
    @Test
    fun testLazyQueryMaxResultsWithFirst() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDF"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL3"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL4"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL5"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assertEquals(2, manager.from(SimpleEntity::class).first(1).limit(2).lazy<IManagedEntity>().count(), "Expected 2 results")
    }

    /**
     * Tests an entity without a partition
     */
    @Test
    fun testLazyQueryAtMax() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ASDF"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL3"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL4"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "ABDUL5"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assertEquals(5, manager.from(SimpleEntity::class).first(0).limit(5).lazy<IManagedEntity>().count(), "Expected 2 results")
    }

}