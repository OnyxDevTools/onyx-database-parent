package zstartup

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.AttributeUpdate
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import database.base.DatabaseBaseTest.Companion.deleteDatabase
import entities.Person
import entities.partition.BasicPartitionEntity
import entities.partition.IndexPartitionEntity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestPartitionedDatabaseRecovery {

    @Before
    fun before() {
        deleteDatabase(DATABASE_LOCATION_BASE)
        deleteDatabase(DATABASE_LOCATION_RECOVERED)
    }

    @After
    fun after() {
        deleteDatabase(DATABASE_LOCATION_BASE)
        deleteDatabase(DATABASE_LOCATION_RECOVERED)
    }

    @Test
    fun testPartitionedEntityReplayFromWal() {
        val factory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_BASE)
        factory.isEnableJournaling = true
        factory.initialize()

        val entity = BasicPartitionEntity()
        entity.id = 42L
        entity.partitionId = 7L
        factory.persistenceManager.saveEntity<IManagedEntity>(entity)
        factory.close()

        val recoveredFactory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_RECOVERED)
        recoveredFactory.initialize()
        recoveredFactory.schemaContext.transactionInteractor.recoverDatabase(DATABASE_LOCATION_BASE + File.separator + "wal") { true }

        val recovered = recoveredFactory.persistenceManager.findByIdInPartition<BasicPartitionEntity>(BasicPartitionEntity::class.java, 42L, 7L)

        assertNotNull(recovered)
        assertEquals(42L, recovered.id)
        assertEquals(7L, recovered.partitionId)

        recoveredFactory.close()
    }

    @Test
    fun testNullPartitionedEntityReplayFromWal() {
        val factory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_BASE)
        factory.isEnableJournaling = true
        factory.initialize()

        val entity = BasicPartitionEntity()
        entity.id = 42L
        factory.persistenceManager.saveEntity<IManagedEntity>(entity)
        factory.close()

        val recoveredFactory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_RECOVERED)
        recoveredFactory.initialize()
        recoveredFactory.schemaContext.transactionInteractor.recoverDatabase(DATABASE_LOCATION_BASE + File.separator + "wal") { true }

        val recoveredById = recoveredFactory.persistenceManager.findById<BasicPartitionEntity>(BasicPartitionEntity::class.java, 42L)
        val recovered = recoveredFactory.persistenceManager.findByIdInPartition<BasicPartitionEntity>(BasicPartitionEntity::class.java, 42L, "")

        assertNotNull(recoveredById)
        assertNotNull(recovered)
        assertEquals(42L, recovered.id)
        assertNull(recovered.partitionId)

        recoveredFactory.close()
    }

    @Test
    fun testStringPartitionedEntityReplayFromWal() {
        val factory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_BASE)
        factory.isEnableJournaling = true
        factory.initialize()

        val entity = Person()
        entity.partitionVal = "tenant-a"
        entity.firstName = "Jane"
        entity.lastName = "Doe"
        factory.persistenceManager.saveEntity<IManagedEntity>(entity)
        val id = entity.id!!
        factory.close()

        val recoveredFactory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_RECOVERED)
        recoveredFactory.initialize()
        recoveredFactory.schemaContext.transactionInteractor.recoverDatabase(DATABASE_LOCATION_BASE + File.separator + "wal") { true }

        val recovered = recoveredFactory.persistenceManager.findByIdInPartition<Person>(Person::class.java, id, "tenant-a")

        assertNotNull(recovered)
        assertEquals(id, recovered.id)
        assertEquals("tenant-a", recovered.partitionVal)
        assertEquals("Jane", recovered.firstName)

        recoveredFactory.close()
    }

    @Test
    fun testPartitionedEntityDeleteReplayFromWal() {
        val factory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_BASE)
        factory.isEnableJournaling = true
        factory.initialize()

        val entity = BasicPartitionEntity()
        entity.id = 42L
        entity.partitionId = 7L
        factory.persistenceManager.saveEntity<IManagedEntity>(entity)
        factory.persistenceManager.deleteEntity(entity)
        factory.close()

        val recoveredFactory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_RECOVERED)
        recoveredFactory.initialize()
        recoveredFactory.schemaContext.transactionInteractor.recoverDatabase(DATABASE_LOCATION_BASE + File.separator + "wal") { true }

        val recovered = recoveredFactory.persistenceManager.findByIdInPartition<BasicPartitionEntity>(BasicPartitionEntity::class.java, 42L, 7L)

        assertNull(recovered)

        recoveredFactory.close()
    }

    @Test
    fun testPartitionedQueryReplayFromWal() {
        val factory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_BASE)
        factory.isEnableJournaling = true
        factory.initialize()

        val entity = IndexPartitionEntity()
        entity.id = 42L
        entity.partitionId = 7L
        entity.indexVal = 1L
        factory.persistenceManager.saveEntity<IManagedEntity>(entity)

        val otherPartitionEntity = IndexPartitionEntity()
        otherPartitionEntity.id = 42L
        otherPartitionEntity.partitionId = 8L
        otherPartitionEntity.indexVal = 1L
        factory.persistenceManager.saveEntity<IManagedEntity>(otherPartitionEntity)

        val updateQuery = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 1L))
        updateQuery.partition = 7L
        updateQuery.updates = arrayListOf(AttributeUpdate("indexVal", 2L))
        factory.persistenceManager.executeUpdate(updateQuery)

        val deleteQuery = Query(IndexPartitionEntity::class.java, QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 2L))
        deleteQuery.partition = 7L
        factory.persistenceManager.executeDelete(deleteQuery)
        factory.close()

        val recoveredFactory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_RECOVERED)
        recoveredFactory.initialize()
        recoveredFactory.schemaContext.transactionInteractor.recoverDatabase(DATABASE_LOCATION_BASE + File.separator + "wal") { true }

        val deleted = recoveredFactory.persistenceManager.findByIdInPartition<IndexPartitionEntity>(IndexPartitionEntity::class.java, 42L, 7L)
        val retained = recoveredFactory.persistenceManager.findByIdInPartition<IndexPartitionEntity>(IndexPartitionEntity::class.java, 42L, 8L)

        assertNull(deleted)
        assertNotNull(retained)
        assertEquals(1L, retained.indexVal)

        recoveredFactory.close()
    }

    companion object {
        private const val DATABASE_LOCATION_BASE = "C:/Sandbox/Onyx/Tests/partitionRecoveryBase.oxd"
        private const val DATABASE_LOCATION_RECOVERED = "C:/Sandbox/Onyx/Tests/partitionRecoveryRecovered.oxd"
    }
}
