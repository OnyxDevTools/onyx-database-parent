package database.zstartup

import com.onyx.interactors.transaction.data.SaveTransaction
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.AttributeUpdate
import database.base.DatabaseBaseTest
import entities.AllAttributeEntity
import org.junit.*
import org.junit.runners.MethodSorters

import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Created by Tim Osborn on 3/25/16.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TestDatabaseRecovery : DatabaseBaseTest(EmbeddedPersistenceManagerFactory::class) {

    @Before
    override fun initialize() {
        if (context == null) {
            factory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_BASE)
            (factory as EmbeddedPersistenceManagerFactory).isEnableJournaling = true
            factory.initialize()
            context = factory.schemaContext
            manager = factory.persistenceManager
        }
    }

    @Test
    fun aTestDatabaseRecovery() {
        this.populateTransactionData()

        val newFactory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_RECOVERED)
        newFactory.initialize()

        val newContext = newFactory.schemaContext
        val newManager = newFactory.persistenceManager

        newContext.transactionInteractor.recoverDatabase(DATABASE_LOCATION_BASE + File.separator + "wal") { _ -> true }

        assertNull(newManager.findById(AllAttributeEntity::class.java, "_ASDF_ASDF100020"))
        assertNull(newManager.findById(AllAttributeEntity::class.java, "_ASDF_ASDF100"))

        val deleteQuery = Query(AllAttributeEntity::class.java, QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 5000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 4000))
        var results: List<*> = newManager.executeQuery<Any>(deleteQuery)
        assertTrue(results.isEmpty())

        val updateQuery = Query(AllAttributeEntity::class.java, QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 90000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 80000)
                .and("doubleValue", QueryCriteriaOperator.EQUAL, 99.0))

        results = newManager.executeQuery<Any>(updateQuery)
        expectedUpdatedEntitiesAfterRecovery = results.size
        assertEquals(9999, results.size)

        val existsQuery = Query()
        existsQuery.entityType = AllAttributeEntity::class.java
        results = manager.executeQuery<Any>(existsQuery)

        expectedEntitiesAfterRecovery = results.size

        results = newManager.executeQuery<Any>(existsQuery)

        assertEquals(expectedEntitiesAfterRecovery, results.size, "Number of entities does not match expected")

        factory.close()
        newFactory.close()

    }

    /**
     * This unit test illustrates how to filter transactions when applying a wal log
     */
    @Test
    fun bTestDatabaseApplyTransactions() {

        val newFactory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_AMENDED)
        newFactory.initialize()

        val newContext = newFactory.schemaContext
        val newManager = newFactory.persistenceManager

        newContext.transactionInteractor.applyTransactionLog(DATABASE_LOCATION_BASE + File.separator + "wal" + File.separator + "0.wal") { transaction ->
            transaction is SaveTransaction
        }

        val existsQuery = Query()
        existsQuery.entityType = AllAttributeEntity::class.java
        val results = newManager.executeLazyQuery<IManagedEntity>(existsQuery)

        assertEquals(101000, results.size, "Filtering transactions did not work")

        newFactory.close()
    }

    private fun populateTransactionData() {

        var allAttributeEntity: AllAttributeEntity?

        for (i in 0..99999) {
            allAttributeEntity = AllAttributeEntity()
            allAttributeEntity.doubleValue = 23.0
            allAttributeEntity.id = "_ASDF_ASDF" + i
            allAttributeEntity.dateValue = Date()
            allAttributeEntity.intValue = i
            manager.saveEntity<IManagedEntity>(allAttributeEntity)
        }

        allAttributeEntity = AllAttributeEntity()
        allAttributeEntity.id = "_ASDF_ASDF100"

        manager.deleteEntity(allAttributeEntity)

        for (i in 100000..100999) {
            allAttributeEntity = AllAttributeEntity()
            allAttributeEntity.doubleValue = 23.0
            allAttributeEntity.id = "_ASDF_ASDF" + i
            allAttributeEntity.dateValue = Date()
            allAttributeEntity.intValue = i
            manager.saveEntity<IManagedEntity>(allAttributeEntity)
        }

        allAttributeEntity = AllAttributeEntity()
        allAttributeEntity.id = "_ASDF_ASDF100020"

        manager.deleteEntity(allAttributeEntity)

        val deleteQuery = Query(AllAttributeEntity::class.java, QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 5000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 4000))
        manager.executeDelete(deleteQuery)

        val updateQuery = Query(AllAttributeEntity::class.java, QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 90000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 80000))
        updateQuery.updates = arrayListOf(AttributeUpdate("doubleValue", 99.0))
        manager.executeUpdate(updateQuery)

    }

    companion object {

        internal var expectedEntitiesAfterRecovery: Int = 0
        internal var expectedUpdatedEntitiesAfterRecovery: Int = 0

        private val DATABASE_LOCATION_RECOVERED = "C:/Sandbox/Onyx/Tests/recovered.oxd"
        private val DATABASE_LOCATION_AMENDED = "C:/Sandbox/Onyx/Tests/amended.oxd"
        private val DATABASE_LOCATION_BASE = "C:/Sandbox/Onyx/Tests/base.oxd"

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            deleteDatabase(DATABASE_LOCATION_RECOVERED)
            deleteDatabase(DATABASE_LOCATION_AMENDED)
            deleteDatabase(DATABASE_LOCATION_BASE)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            deleteDatabase(DATABASE_LOCATION_RECOVERED)
            deleteDatabase(DATABASE_LOCATION_AMENDED)
            deleteDatabase(DATABASE_LOCATION_BASE)
        }
    }

}
