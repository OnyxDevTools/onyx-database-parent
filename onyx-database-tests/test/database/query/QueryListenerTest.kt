package database.query

import com.onyx.extension.*
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.CacheManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.persistence.query.*
import database.base.DatabaseBaseTest
import entities.PageAnalytic
import entities.SimpleEntity
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class QueryListenerTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Before
    fun seedData() {
        manager.from(SimpleEntity::class).delete()

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "1"
        simpleEntity.name = "1"
        manager.saveEntity<IManagedEntity>(simpleEntity)
    }

    /**
     * Test a query is subscribed correctly upon execution
     */
    @Test
    fun testSubscribe() {

        // Ignore query cache interactor method unit tests for RemotePersistenceManager
        Assume.assumeFalse(factory is RemotePersistenceManagerFactory)

        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {
            override fun onItemUpdated(item: IManagedEntity) {}
            override fun onItemAdded(item: IManagedEntity) {}
            override fun onItemRemoved(item: IManagedEntity) {}
        }

        manager.executeQuery<Any>(query)

        val results = manager.context.queryCacheInteractor.getCachedQueryResults(query)
        assertEquals(1, results?.listeners?.size, "There should be a subscriber")

        // Ensure it is not duplicating listeners
        manager.executeQuery<Any>(query)
        assertEquals(1, results?.listeners?.size, "There should still only be 1 subscriber")
    }

    /**
     * Test a query is un-subscribed correctly
     */
    @Test
    fun testUnSubscribe() {

        Assume.assumeFalse(factory is RemotePersistenceManagerFactory)

        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {
            override fun onItemUpdated(item: IManagedEntity) { }
            override fun onItemAdded(item: IManagedEntity) { }
            override fun onItemRemoved(item: IManagedEntity) { }
        }

        manager.executeQuery<Any>(query)

        val results = manager.context.queryCacheInteractor.getCachedQueryResults(query)
        assertEquals(1, results!!.listeners.size, "Query should have been subscribed")

        manager.removeChangeListener(query)

        assertEquals(0, results.listeners.size, "Query should have been un-subscribed")

        // Ensure it is not duplicating listeners
        manager.executeQuery<Any>(query)
        assertEquals(1, results.listeners.size, "Query should have been re-subscribed")
    }

    /**
     * Test an entity correctly hits an insert listener
     */
    @Test
    fun testInsert() {
        val pass = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<SimpleEntity> {

            override fun onItemUpdated(item: SimpleEntity) {
                assertTrue(false, "onItemUpdated should not have been executed")
            }

            override fun onItemAdded(item: SimpleEntity) {
                assertEquals("22", item.simpleId, "simpleId was not assigned properly")
                assertEquals("22", item.name, "name was not assigned")
                pass.countDown()
            }

            override fun onItemRemoved(item: SimpleEntity) {
                assertTrue(false, "onItemRemoved should not have been executed")
            }
        }

        manager.executeQuery<Any>(query)

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "22"
        simpleEntity.name = "22"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assertTrue(pass.await(1, TimeUnit.SECONDS), "Listener was not fired")

    }

    /**
     * Test an entity correctly hits an update listener
     */
    @Test
    fun testUpdate() {
        val pass = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<SimpleEntity> {

            override fun onItemUpdated(item: SimpleEntity) {
                assertEquals( "1", item.simpleId)
                assertEquals( "2", item.name)
                pass.countDown()
            }

            override fun onItemAdded(item: SimpleEntity) {
                assertTrue(false)
            }

            override fun onItemRemoved(item: SimpleEntity) {
                assertTrue(false)
            }
        }

        manager.executeQuery<Any>(query)

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "1"
        simpleEntity.name = "2"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assertTrue(pass.await(1, TimeUnit.SECONDS))
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    fun testDelete() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "55"
        simpleEntity.name = "2"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        val pass = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<SimpleEntity> {

            override fun onItemUpdated(item: SimpleEntity) {
                assertTrue(false, "onItemUpdated should not have been fired")
            }

            override fun onItemAdded(item: SimpleEntity) {
                assertTrue(false, "onItemAdded should not have been fired")
            }

            override fun onItemRemoved(item: SimpleEntity) {
                assertEquals("55", item.simpleId, "simpleId was not assigned")
                assertEquals("2", item.name, "name was not assigned")
                pass.countDown()
            }
        }

        manager.executeQuery<Any>(query)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "55"
        simpleEntity.name = "2"
        manager.deleteEntity(simpleEntity)

        assertTrue(pass.await(1, TimeUnit.SECONDS), "Listener was not fired")
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    fun testExecuteDelete() {
        val analytic = PageAnalytic()
        analytic.agent = "ME"
        analytic.httpStatus = 200
        analytic.ipAddress = "2929292.234234"
        analytic.loadTime = 211
        analytic.monthYear = "20173"
        analytic.path = "/path"

        manager.saveEntity<IManagedEntity>(analytic)

        val pass = CountDownLatch(1)

        manager.from(PageAnalytic::class).where(
                    ("path" notCont ".css")
                    .and("path" notCont ".js")
                    .and("ipAddress" notStartsWith "not-found")
                    .and("agent" notCont "bot")
                    .and("monthYear" eq "20173")
                    .and("requestDate" gt Date(1492819200000L)))
                .orderBy("pageLoadId".desc())
                .onItemDeleted<PageAnalytic> { pass.countDown() }
                .onItemUpdated<PageAnalytic> { assertTrue(false, "onItemUpdated should not be fired") }
                .onItemAdded<PageAnalytic> { assertTrue(false, "onItemAdded should not be fired") }
                .list<PageAnalytic>()

        manager.from(PageAnalytic::class).where(
                    ("path" notCont ".css")
                    .and("path" notCont ".js")
                    .and("ipAddress" notStartsWith "not-found")
                    .and("agent" notCont "bot")
                    .and("monthYear" eq "20173")
                    .and("requestDate" gt Date(1492819200000L)))
               .orderBy("pageLoadId".desc())
               .delete()

        val results = manager.from(PageAnalytic::class).where(
                             ("path" notCont ".css")
                                     .and("path" notCont ".js")
                                     .and("ipAddress" notStartsWith "not-found")
                                     .and("agent" notCont "bot")
                                     .and("monthYear" eq "20173")
                                     .and("requestDate" gt Date(1492819200000L)))
                             .orderBy("pageLoadId".desc())
                             .list<PageAnalytic>()

        assertTrue(results.isEmpty(), "Query should not have given results")
        assertTrue(pass.await(1, TimeUnit.SECONDS), "Deleted event was not fired")
    }


    /**
     * Test a query is subscribed correctly
     */
    @Test
    fun testSubscribeListen() {

        Assume.assumeFalse(factory is RemotePersistenceManagerFactory)

        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {
            override fun onItemUpdated(item: IManagedEntity) { }
            override fun onItemAdded(item: IManagedEntity) { }
            override fun onItemRemoved(item: IManagedEntity) { }
        }

        manager.listen(query)

        val results = manager.context.queryCacheInteractor.getCachedQueryResults(query)
        assertEquals(1, results!!.listeners.size, "Query was not subscribed")

        // Ensure it is not duplicating listeners
        manager.executeQuery<IManagedEntity>(query)
        assertEquals(1, results.listeners.size, "Listener was not retained")
    }

    /**
     * Test a query is un-subscribed correctly
     */
    @Test
    fun testUnSubscribeListen() {

        Assume.assumeFalse(factory is RemotePersistenceManagerFactory)

        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {
            override fun onItemUpdated(item: IManagedEntity) { }
            override fun onItemAdded(item: IManagedEntity) { }
            override fun onItemRemoved(item: IManagedEntity) { }
        }

        manager.listen(query)

        val results = manager.context.queryCacheInteractor.getCachedQueryResults(query)
        assertEquals(1, results?.listeners?.size, "Query was not subscribed")

        manager.removeChangeListener(query)

        assertEquals(0, results?.listeners?.size, "Listener was not removed")

        // Ensure it is not duplicating listeners
        manager.executeQuery<Any>(query)
        assertEquals(1, results?.listeners?.size, "Listener was not added again")

    }

    /**
     * Test an entity correctly hits an insert listener
     */
    @Test
    fun testInsertListen() {
        val pass = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<SimpleEntity> {

            override fun onItemUpdated(item: SimpleEntity) {
                assertTrue(false, "onItemUpdated should not have been fired")
            }

            override fun onItemAdded(item: SimpleEntity) {
                assertEquals("220", item.simpleId, "simpleId was not assigned")
                assertEquals("22", item.name, "name was not assigned")
                pass.countDown()
            }

            override fun onItemRemoved(item: SimpleEntity) {
                assertTrue(false, "onItemRemoved should not have been fired")
            }
        }

        manager.listen(query)

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "220"
        simpleEntity.name = "22"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assertTrue(pass.await(1, TimeUnit.SECONDS), "onItemAdded was not fired")
    }

    /**
     * Test an entity correctly hits an update listener
     */
    @Test
    fun testUpdateListen() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "11"
        simpleEntity.name = "2"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        val pass = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<SimpleEntity> {

            override fun onItemUpdated(item: SimpleEntity) {
                pass.countDown()
                assertEquals("11", item.simpleId, "simpleId not set")
                assertEquals("2", item.name, "name not set")
            }

            override fun onItemAdded(item: SimpleEntity) {
                assertTrue(false, "onItemAdded should not be invoked")
            }

            override fun onItemRemoved(item: SimpleEntity) {
                assertTrue(false, "onItemRemoved should not be invoked")
            }
        }

        manager.listen(query)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "11"
        simpleEntity.name = "2"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assertTrue(pass.await(1, TimeUnit.SECONDS), "Listener not invoked")
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    fun testDeleteListen() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "12"
        simpleEntity.name = "2"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        val pass = CountDownLatch(1)

        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<SimpleEntity> {

            override fun onItemUpdated(item: SimpleEntity) {
                assertTrue(false, "onItemUpdated should not be invoked")
            }

            override fun onItemAdded(item: SimpleEntity) {
                assertTrue(false, "onItemAdded should not be invoked")
            }

            override fun onItemRemoved(item: SimpleEntity) {
                assertEquals("12", item.simpleId, "simpleId not set")
                assertEquals("2", item.name, "name not set")
                pass.countDown()
            }
        }

        manager.listen(query)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "12"
        simpleEntity.name = "2"
        manager.deleteEntity(simpleEntity)

        assertTrue(pass.await(1, TimeUnit.SECONDS), "Listener not invoked")
    }

    companion object {

        /**
         * Overridden because query listeners are not supported for WebPersistenceManager as it is not a feature for web
         * services
         */
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(EmbeddedPersistenceManagerFactory::class, CacheManagerFactory::class, RemotePersistenceManagerFactory::class)
    }
}
