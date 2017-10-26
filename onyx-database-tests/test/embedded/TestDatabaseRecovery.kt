package embedded

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.interactors.transaction.data.SaveTransaction
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.persistence.query.AttributeUpdate
import embedded.base.BaseTest
import entities.AllAttributeEntity
import org.junit.*
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters

import java.io.File
import java.util.Arrays
import java.util.Date

/**
 * Created by Tim Osborn on 3/25/16.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(EmbeddedDatabaseTests::class)
class TestDatabaseRecovery : BaseTest() {

    @Before
    @Throws(InitializationException::class, InterruptedException::class)
    fun before() {
        if (context == null) {
            factory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_BASE)
            (factory as EmbeddedPersistenceManagerFactory).isEnableJournaling = true
            factory!!.initialize()

            context = factory!!.schemaContext
            manager = factory!!.persistenceManager
        }
    }

    @Test
    @Throws(OnyxException::class)
    fun atestDatabaseRecovery() {
        this.populateTransactionData()

        val newFactory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_RECOVERED)
        newFactory.initialize()

        val newContext = newFactory.schemaContext
        val newManager = newFactory.persistenceManager

        newContext.transactionInteractor.recoverDatabase(DATABASE_LOCATION_BASE + File.separator + "wal") { transaction -> true }

        Assert.assertTrue(newManager.findById<IManagedEntity>(AllAttributeEntity::class.java, "ASDFASDF100020") == null)
        Assert.assertTrue(newManager.findById<IManagedEntity>(AllAttributeEntity::class.java, "ASDFASDF100") == null)

        val deleteQuery = Query(AllAttributeEntity::class.java, QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 5000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 4000))
        var results: List<*> = newManager.executeQuery<Any>(deleteQuery)
        Assert.assertTrue(results.size == 0)

        val updateQuery = Query(AllAttributeEntity::class.java, QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 90000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 80000)
                .and("doubleValue", QueryCriteriaOperator.EQUAL, 99.0))
        results = newManager.executeQuery<Any>(updateQuery)
        expectedUpdatedEntitiesAfterRecovery = results.size
        Assert.assertTrue(results.size == 9999)

        val existsQuery = Query()
        existsQuery.entityType = AllAttributeEntity::class.java
        results = manager.executeQuery<Any>(existsQuery)

        expectedEntitiesAfterRecovery = results.size

        results = newManager.executeQuery<Any>(existsQuery)

        Assert.assertTrue(expectedEntitiesAfterRecovery == results.size)

        factory!!.close()
        newFactory.close()

    }

    @Test
    @Throws(OnyxException::class)
    fun btestDatabaseApplyTransactions() {

        val newFactory = EmbeddedPersistenceManagerFactory(DATABASE_LOCATION_AMMENDED)
        newFactory.initialize()

        val newContext = newFactory.schemaContext
        val newManager = newFactory.persistenceManager

        newContext.transactionInteractor.applyTransactionLog(DATABASE_LOCATION_BASE + File.separator + "wal" + File.separator + "0.wal") { transaction ->
            transaction is SaveTransaction

        }

        val existsQuery = Query()
        existsQuery.entityType = AllAttributeEntity::class.java
        val results = newManager.executeLazyQuery<IManagedEntity>(existsQuery)

        Assert.assertTrue(results.size == 101000)

        newFactory.close()
    }

    @Throws(OnyxException::class)
    protected fun populateTransactionData() {

        var allAttributeEntity: AllAttributeEntity? = null

        for (i in 0..99999) {
            allAttributeEntity = AllAttributeEntity()
            allAttributeEntity.doubleValue = 23.0
            allAttributeEntity.id = "ASDFASDF" + i
            allAttributeEntity.dateValue = Date()
            allAttributeEntity.intValue = i
            manager.saveEntity<IManagedEntity>(allAttributeEntity)
        }

        allAttributeEntity = AllAttributeEntity()
        allAttributeEntity.id = "ASDFASDF100"

        manager.deleteEntity(allAttributeEntity)

        for (i in 100000..100999) {
            allAttributeEntity = AllAttributeEntity()
            allAttributeEntity.doubleValue = 23.0
            allAttributeEntity.id = "ASDFASDF" + i
            allAttributeEntity.dateValue = Date()
            allAttributeEntity.intValue = i
            manager.saveEntity<IManagedEntity>(allAttributeEntity)
        }

        allAttributeEntity = AllAttributeEntity()
        allAttributeEntity.id = "ASDFASDF100020"

        manager.deleteEntity(allAttributeEntity)

        val deleteQuery = Query(AllAttributeEntity::class.java, QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 5000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 4000))
        manager.executeDelete(deleteQuery)

        val updateQuery = Query(AllAttributeEntity::class.java, QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 90000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 80000))
        updateQuery.updates = Arrays.asList(AttributeUpdate("doubleValue", 99.0))
        manager.executeUpdate(updateQuery)

    }

    companion object {

        protected val DATABASE_LOCATION_RECOVERED = "C:/Sandbox/Onyx/Tests/recovered.oxd"
        protected val DATABASE_LOCATION_AMMENDED = "C:/Sandbox/Onyx/Tests/ammended.oxd"
        protected val DATABASE_LOCATION_BASE = "C:/Sandbox/Onyx/Tests/base.oxd"

        @BeforeClass
        fun beforeClass() {
            BaseTest.delete(File(DATABASE_LOCATION_RECOVERED))
            BaseTest.delete(File(DATABASE_LOCATION_AMMENDED))
            BaseTest.delete(File(DATABASE_LOCATION_BASE))
        }

        @AfterClass
        fun afterClass() {
            BaseTest.delete(File(DATABASE_LOCATION_RECOVERED))
            BaseTest.delete(File(DATABASE_LOCATION_AMMENDED))
            BaseTest.delete(File(DATABASE_LOCATION_BASE))
        }

        internal var expectedEntitiesAfterRecovery: Int = 0
        internal var expectedUpdatedEntitiesAfterRecovery: Int = 0
    }

}
