package remote.queries

import category.RemoteServerTests
import com.onyx.application.impl.DatabaseServer
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.query.Query
import com.onyx.persistence.query.QueryListener
import entities.SimpleEntity
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.experimental.categories.Category
import remote.base.RemoteBaseTest

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by Tim Osborn on 3/27/17.
 *
 * This unit test covers registered query listeners and the events
 */
@Category(value = RemoteServerTests::class)
class QueryListenerTest : RemoteBaseTest() {

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
        manager!!.saveEntity<IManagedEntity>(simpleEntity)
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

        manager!!.executeQuery<Any>(query)

    }

    /**
     * Test a query is un-subscribed correctly
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testUnsubscribe() {
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(items: IManagedEntity) {
                assert(false)
            }

            override fun onItemAdded(items: IManagedEntity) {
                assert(false)
            }

            override fun onItemRemoved(items: IManagedEntity) {
                assert(false)
            }
        }

        manager!!.executeQuery<Any>(query)

        manager!!.removeChangeListener(query)


        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "23"
        simpleEntity.name = "23"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)

        // Ensure it is not duplicating listeners
        manager!!.executeQuery<Any>(query)

        Thread.sleep(1000)


    }

    /**
     * Test an entity correctly hits an insert listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testInsert() {
        val pass = AtomicBoolean(false)
        val countDownLatch = CountDownLatch(1)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemAdded(entity: IManagedEntity) {
                pass.set(true)
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "22")
                assert(entity.name == "22")
                countDownLatch.countDown()
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                assert(false)
            }
        }

        manager!!.executeQuery<Any>(query)

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "22"
        simpleEntity.name = "22"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)

        countDownLatch.await(5, TimeUnit.SECONDS)

        assert(pass.get())
    }

    /**
     * Test an entity correctly hits an update listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testUpdate() {
        val countDownLatch = CountDownLatch(1)

        val pass = AtomicBoolean(false)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                pass.set(true)
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "1")
                assert(entity.name == "2")
                countDownLatch.countDown()
            }

            override fun onItemAdded(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                assert(false)
            }
        }

        manager!!.executeQuery<Any>(query)

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "1"
        simpleEntity.name = "2"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)

        countDownLatch.await(5, TimeUnit.SECONDS)

        assert(pass.get())
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testDelete() {
        val countDownLatch = CountDownLatch(1)

        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "55"
        simpleEntity.name = "2"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)

        val pass = AtomicBoolean(false)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemAdded(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                pass.set(true)
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "55")
                assert(entity.name == "2")
                countDownLatch.countDown()
            }
        }

        manager!!.executeQuery<Any>(query)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "55"
        simpleEntity.name = "2"
        manager!!.deleteEntity(simpleEntity)

        countDownLatch.await(5, TimeUnit.SECONDS)

        assert(pass.get())
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

        manager!!.listen(query)

    }

    /**
     * Test a query is un-subscribed correctly
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testUnsubscribeListen() {
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(items: IManagedEntity) {
                assert(false)
            }

            override fun onItemAdded(items: IManagedEntity) {
                assert(false)
            }

            override fun onItemRemoved(items: IManagedEntity) {
                assert(false)
            }
        }

        manager!!.listen(query)

        manager!!.removeChangeListener(query)

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "23"
        simpleEntity.name = "23"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)


        Thread.sleep(1000)

    }

    /**
     * Test an entity correctly hits an insert listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testInsertListen() {
        val countDownLatch = CountDownLatch(1)

        val pass = AtomicBoolean(false)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemAdded(entity: IManagedEntity) {
                pass.set(true)
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "220")
                assert(entity.name == "22")
                countDownLatch.countDown()
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                assert(false)
            }
        }

        manager!!.listen(query)

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "220"
        simpleEntity.name = "22"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)

        assert(countDownLatch.await(1, TimeUnit.SECONDS))

        assert(pass.get())

    }

    /**
     * Test an entity correctly hits an update listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testUpdateListen() {
        val countDownLatch = CountDownLatch(1)
        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "11"
        simpleEntity.name = "2"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)

        val pass = AtomicBoolean(false)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                pass.set(true)
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "11")
                assert(entity.name == "2")
                countDownLatch.countDown()
            }

            override fun onItemAdded(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                assert(false)
            }
        }

        manager!!.listen(query)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "11"
        simpleEntity.name = "2"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)

        assert(countDownLatch.await(1, TimeUnit.SECONDS))
        assert(pass.get())
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    @Throws(OnyxException::class, InterruptedException::class)
    fun testDeleteListen() {
        val countDownLatch = CountDownLatch(1)

        var simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "12"
        simpleEntity.name = "2"
        manager!!.saveEntity<IManagedEntity>(simpleEntity)

        val pass = AtomicBoolean(false)
        val query = Query(SimpleEntity::class.java)
        query.changeListener = object : QueryListener<IManagedEntity> {

            override fun onItemUpdated(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemAdded(entity: IManagedEntity) {
                assert(false)
            }

            override fun onItemRemoved(entity: IManagedEntity) {
                pass.set(true)
                assert(entity is SimpleEntity)
                assert((entity as SimpleEntity).simpleId == "12")
                assert(entity.name == "2")
                countDownLatch.countDown()
            }
        }

        manager!!.listen(query)

        simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "12"
        simpleEntity.name = "2"
        manager!!.deleteEntity(simpleEntity)

        assert(countDownLatch.await(1, TimeUnit.SECONDS))

        assert(pass.get())
    }
}

