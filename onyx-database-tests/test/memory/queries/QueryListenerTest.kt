package memory.queries

import category.InMemoryDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.interactors.cache.data.CachedResults
import com.onyx.persistence.query.QueryListener
import entities.SimpleEntity
import memory.base.BaseTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by Tim Osborn on 3/27/17.
 *
 * This unit test covers registered query listeners and the events
 */
@Category(value = InMemoryDatabaseTests::class)
class QueryListenerTest : BaseTest() {

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Before
    @Throws(OnyxException::class)
    fun before() {
        initialize()
        seedData()
    }

    @Throws(OnyxException::class)
    fun seedData() {
        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "1"
        simpleEntity.name = "1"
        manager.saveEntity<IManagedEntity>(simpleEntity)
    }

    /**
     * Test a query is subscribed correctly
     */
    @Test
    @Throws(OnyxException::class)
    fun testSubscribe() {
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(items: IManagedEntity) {

            }

            override fun onItemAdded(items: IManagedEntity) {

            }

            override fun onItemRemoved(items: IManagedEntity) {

            }
        }

        manager.executeQuery<Any>(query)

        val results = manager.context.queryCacheInteractor.getCachedQueryResults(query)
        assert(results!!.listeners.size == 1)

        // Ensure it is not duplicating listeners
        manager.executeQuery<Any>(query)
        assert(results.listeners.size == 1)
    }

    /**
     * Test a query is un-subscribed correctly
     */
    @Test
    @Throws(OnyxException::class)
    fun testUnsubscribe() {
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(items: IManagedEntity) {

            }

            override fun onItemAdded(items: IManagedEntity) {

            }

            override fun onItemRemoved(items: IManagedEntity) {

            }
        }

        manager.executeQuery<Any>(query)

        val results = manager.context.queryCacheInteractor.getCachedQueryResults(query)
        assert(results!!.listeners.size == 1)

        manager.removeChangeListener(query)

        assert(results.listeners.size == 0)

        // Ensure it is not duplicating listeners
        manager.executeQuery<Any>(query)
        assert(results.listeners.size == 1)

    }

    /**
     * Test an entity correctly hits an insert listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testInsert() {
        val pass = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemAdded(entity: IManagedEntity) {
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "22")
                assert(entity.name == "22")
                pass.countDown()
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                assert(false)
            }
        }

        manager.executeQuery<Any>(query)

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "22"
        simpleEntity.name = "22"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assert(pass.await(1, TimeUnit.SECONDS))

    }

    /**
     * Test an entity correctly hits an update listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testUpdate() {
        val pass = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "1")
                assert(entity.name == "2")
                pass.countDown()
            }

            override fun onItemAdded(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                assert(false)
            }
        }

        manager.executeQuery<Any>(query)

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "1"
        simpleEntity.name = "2"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assert(pass.await(1, TimeUnit.SECONDS))
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testDelete() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "55"
        simpleEntity.name = "2"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        val pass = CountDownLatch(1)

        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemAdded(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "55")
                assert(entity.name == "2")
                pass.countDown()
            }
        }

        manager.executeQuery<Any>(query)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "55"
        simpleEntity.name = "2"
        manager.deleteEntity(simpleEntity)

        assert(pass.await(1, TimeUnit.SECONDS))
    }


    /**
     * Test a query is subscribed correctly
     */
    @Test
    @Throws(OnyxException::class)
    fun testSubscribeListen() {
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(items: IManagedEntity) {

            }

            override fun onItemAdded(items: IManagedEntity) {

            }

            override fun onItemRemoved(items: IManagedEntity) {

            }
        }

        manager.listen(query)

        val results = manager.context.queryCacheInteractor.getCachedQueryResults(query)
        assert(results!!.listeners.size == 1)

        // Ensure it is not duplicating listeners
        manager.executeQuery<Any>(query)
        assert(results.listeners.size == 1)
    }

    /**
     * Test a query is un-subscribed correctly
     */
    @Test
    @Throws(OnyxException::class)
    fun testUnsubscribeListen() {
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(items: IManagedEntity) {

            }

            override fun onItemAdded(items: IManagedEntity) {

            }

            override fun onItemRemoved(items: IManagedEntity) {

            }
        }

        manager.listen(query)

        val results = manager.context.queryCacheInteractor.getCachedQueryResults(query)
        assert(results!!.listeners.size == 1)

        manager.removeChangeListener(query)

        assert(results.listeners.size == 0)

        // Ensure it is not duplicating listeners
        manager.executeQuery<Any>(query)
        assert(results.listeners.size == 1)

    }

    /**
     * Test an entity correctly hits an insert listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testInsertListen() {
        val pass = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemAdded(entity: IManagedEntity) {
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "220")
                assert(entity.name == "22")
                pass.countDown()
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                assert(false)
            }
        }

        manager.listen(query)

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "220"
        simpleEntity.name = "22"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assert(pass.await(1, TimeUnit.SECONDS))

    }

    /**
     * Test an entity correctly hits an update listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testUpdateListen() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "11"
        simpleEntity.name = "2"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        val pass = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "11")
                assert(entity.name == "2")
                pass.countDown()
            }

            override fun onItemAdded(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                assert(false)
            }
        }

        manager.listen(query)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "11"
        simpleEntity.name = "2"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        assert(pass.await(1, TimeUnit.SECONDS))
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testDeleteListen() {
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "12"
        simpleEntity.name = "2"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        val pass = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemAdded(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "12")
                assert(entity.name == "2")
                pass.countDown()
            }
        }

        manager.listen(query)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "12"
        simpleEntity.name = "2"
        manager.deleteEntity(simpleEntity)

        assert(pass.await(1, TimeUnit.SECONDS))
    }
}
