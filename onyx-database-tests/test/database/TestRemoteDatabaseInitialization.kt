package database

import com.onyx.exception.InitializationException
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.application.impl.DatabaseServer
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.SimpleEntity
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Tests the initialization of the database
 */
class TestRemoteDatabaseInitialization : DatabaseBaseTest(RemotePersistenceManagerFactory::class) {

    @Test
    fun testPersistentConnection() {
        var server = DatabaseServer("C:/Sandbox/Onyx/Tests/server2.oxd")
        server.port = 8082
        server.start()

        val factory = RemotePersistenceManagerFactory(PERSIST_CONN_DATABASE_LOCATION)
        factory.setCredentials("admin", "admin")
        factory.initialize()

        val manager = factory.persistenceManager

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "MYIDYO"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        server.stop()

        server = DatabaseServer("C:/Sandbox/Onyx/Tests/server2.oxd")
        server.port = 8082
        server.start()

        val foundAfterClose = manager.findById<IManagedEntity>(SimpleEntity::class.java, simpleEntity.simpleId) as SimpleEntity?

        assertEquals(simpleEntity.simpleId, foundAfterClose!!.simpleId, "Failed to retrieve simpleEntity")

        factory.close()
        server.stop()
    }

    @Test
    @Throws(Exception::class)
    fun testTryConnectBeforeStart() {
        val server = DatabaseServer("C:/Sandbox/Onyx/Tests/server2.oxd")
        server.port = 8082

        val factory = RemotePersistenceManagerFactory(PERSIST_CONN_DATABASE_LOCATION)
        factory.setCredentials("admin", "admin")

        try {
            factory.initialize()
        } catch (e: InitializationException) { }

        val manager = factory.persistenceManager

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "MYIDYO"

        try {
            manager.saveEntity<IManagedEntity>(simpleEntity)
        } catch (queryException: InitializationException) { }

        server.start()

        manager.saveEntity<IManagedEntity>(simpleEntity)
        val foundAfterClose = manager.findById<IManagedEntity>(SimpleEntity::class.java, simpleEntity.simpleId) as SimpleEntity?

        assertEquals(simpleEntity.simpleId, foundAfterClose!!.simpleId, "Failed to retrieve entity")

        factory.close()
        server.stop()
    }

    @Test(expected = InitializationException::class)
    fun testPersistentConnectionNotReOpened() {
        var factory: RemotePersistenceManagerFactory? = null
        try {

            val server = DatabaseServer("C:/Sandbox/Onyx/Tests/server2.oxd")
            server.port = 8082
            server.start()

            factory = RemotePersistenceManagerFactory(PERSIST_CONN_DATABASE_LOCATION)
            factory.setCredentials("admin", "admin")
            factory.initialize()

            val manager = factory.persistenceManager

            val simpleEntity = SimpleEntity()
            simpleEntity.simpleId = "MYIDYO"

            manager.saveEntity<IManagedEntity>(simpleEntity)

            server.stop()

            val foundAfterClose = manager.findById<SimpleEntity>(SimpleEntity::class.java, simpleEntity.simpleId)

            assertEquals(simpleEntity.simpleId, foundAfterClose!!.simpleId, "Failed to retrieve entity")
        } finally {
            factory!!.close()
        }
    }

    /**
     * Positive test
     */
    @Test
    fun testInitializeDatabase() {
        val fac = RemotePersistenceManagerFactory(DATABASE_LOCATION)
        fac.setCredentials("admin", "admin")
        fac.initialize()
        fac.close()
    }

    /**
     * Negative Test for access violation
     */
    @Test(expected = InitializationException::class)
    fun testDataFileIsNotAccessible() {
        val fac = RemotePersistenceManagerFactory(INVALID_DATABASE_LOCATION)
        fac.initialize()
    }

    /**
     * Negative Test for invalid credentials
     */
    @Test(expected = InitializationException::class)
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
