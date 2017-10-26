package remote

import category.RemoteServerTests
import com.onyx.exception.InitializationException
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import com.onyx.application.impl.DatabaseServer
import com.onyx.persistence.IManagedEntity
import entities.SimpleEntity
import org.junit.Test
import org.junit.experimental.categories.Category
import remote.base.RemoteBaseTest

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Tests the initialization of the database
 */
@org.junit.Ignore
@Category(RemoteServerTests::class)
class TestDatabaseInitialization : RemoteBaseTest() {

    @Test
    @Throws(Exception::class)
    fun testPersistantConnection() {
        var dbServer = DatabaseServer("C:/Sandbox/Onyx/Tests/server2.oxd")
        dbServer.port = 8082
        dbServer.start()

        val fac = RemotePersistenceManagerFactory(PERSIST_CONN_DATABASE_LOCATION)
        fac.setCredentials("admin", "admin")

        fac.initialize()

        val mgr = fac.persistenceManager

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "MYIDYO"

        mgr.saveEntity<IManagedEntity>(simpleEntity)

        dbServer.stop()

        dbServer = DatabaseServer("C:/Sandbox/Onyx/Tests/server2.oxd")
        dbServer.port = 8082
        dbServer.start()

        val foundAfterClose = mgr.findById<IManagedEntity>(SimpleEntity::class.java, simpleEntity.simpleId) as SimpleEntity?

        assert(foundAfterClose!!.simpleId == simpleEntity.simpleId)

        fac.close()
        dbServer.stop()
    }

    @Test
    @Throws(Exception::class)
    fun testTryConnectBeforeStart() {
        val dbServer = DatabaseServer("C:/Sandbox/Onyx/Tests/server2.oxd")
        dbServer.port = 8082

        val fac = RemotePersistenceManagerFactory(PERSIST_CONN_DATABASE_LOCATION)
        fac.setCredentials("admin", "admin")

        try {
            fac.initialize()
        } catch (`in`: InitializationException) {
        }

        val mgr = fac.persistenceManager

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "MYIDYO"


        try {
            mgr.saveEntity<IManagedEntity>(simpleEntity)
        } catch (queryException: InitializationException) {
        }

        dbServer.start()

        mgr.saveEntity<IManagedEntity>(simpleEntity)
        val foundAfterClose = mgr.findById<IManagedEntity>(SimpleEntity::class.java, simpleEntity.simpleId) as SimpleEntity?

        assert(foundAfterClose!!.simpleId == simpleEntity.simpleId)

        fac.close()
        dbServer.stop()
    }

    @Test(expected = InitializationException::class)
    @Throws(Exception::class)
    fun testPersistantConnectionNotReOpened() {
        var fac: RemotePersistenceManagerFactory? = null
        try {

            val dbServer = DatabaseServer("C:/Sandbox/Onyx/Tests/server2.oxd")
            dbServer.port = 8082
            dbServer.start()

            fac = RemotePersistenceManagerFactory(PERSIST_CONN_DATABASE_LOCATION)
            fac.setCredentials("admin", "admin")

            val time = System.currentTimeMillis()
            fac.initialize()

            val mgr = fac.persistenceManager

            val simpleEntity = SimpleEntity()
            simpleEntity.simpleId = "MYIDYO"


            mgr.saveEntity<IManagedEntity>(simpleEntity)

            dbServer.stop()

            val foundAfterClose = mgr.findById<IManagedEntity>(SimpleEntity::class.java, simpleEntity.simpleId) as SimpleEntity?

            assert(foundAfterClose!!.simpleId == simpleEntity.simpleId)
        } finally {
            fac!!.close()
        }

    }

    /**
     * Positive test
     *
     * @throws Exception
     */
    @Test
    @Throws(Exception::class)
    fun testInitializeDatabase() {
        val fac = RemotePersistenceManagerFactory(DATABASE_LOCATION)
        fac.setCredentials("admin", "admin")

        val time = System.currentTimeMillis()
        fac.initialize()
        println("Done in " + (System.currentTimeMillis() - time))

        val mgr = fac.persistenceManager

        fac.close()
    }

    /**
     * Negative Test for access violation
     *
     * @throws Exception
     */
    @Test(expected = InitializationException::class)
    @Throws(Exception::class)
    fun testDataFileIsNotAccessible() {
        val fac = RemotePersistenceManagerFactory(INVALID_DATABASE_LOCATION)
        fac.initialize()

        val mgr = EmbeddedPersistenceManager(null!!)
        mgr.context = fac.schemaContext
    }

    /**
     * Negative Test for invalid credentials
     *
     * @throws Exception
     */
    @Test(expected = InitializationException::class)
    @Throws(Exception::class)
    fun testInvalidCredentials() {
        val fac = RemotePersistenceManagerFactory(DATABASE_LOCATION)
        fac.setCredentials("bill", "tom")
        fac.initialize()
        fac.close()
    }

    companion object {
        val INVALID_DATABASE_LOCATION = "onx://localhost:8081"
        val DATABASE_LOCATION = "onx://localhost:8080"
        val PERSIST_CONN_DATABASE_LOCATION = "onx://localhost:8082"
    }


}
