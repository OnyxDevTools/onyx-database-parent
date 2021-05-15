package zstartup

import com.onyx.exception.InitializationException
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.application.impl.DatabaseServer
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.SimpleEntity
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.test.assertEquals

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Tests the initialization of the database
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TestRemoteDatabaseInitialization {

    @Before
    fun deleteDatabase() = DatabaseBaseTest.deleteDatabase(REMOTE_DATABASE_LOCATION)

    companion object {
        val REMOTE_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/remoteOnyxInitialize.oxd"
        val REMOTE_DATABASE_ENDPOINT = "onx://localhost:8012"
        val INVALID_DATABASE_LOCATION = "onx://localhost:8081"
        val PERSIST_CONN_DATABASE_LOCATION = "onx://localhost:8082"
        val CONN_BEFORE_START_LOCATION = "onx://localhost:8083"
        val PERSIST_CONN_NOT_REOPEN_DATABASE_LOCATION = "onx://localhost:8084"
    }

    /**
     * Positive test
     */
    @Test
    fun aTestInitializeDatabase() {
        val remoteServer = DatabaseServer(REMOTE_DATABASE_LOCATION)
        remoteServer.port = 8012
        remoteServer.setCredentials("admin", "admin")
        remoteServer.start()

        try {
            val fac = RemotePersistenceManagerFactory(REMOTE_DATABASE_ENDPOINT)
            fac.setCredentials("admin", "admin")
            fac.initialize()
            fac.close()
        } finally {
            remoteServer.stop()
        }
    }

    /**
     * Negative Test for invalid credentials
     */
    @Test(expected = InitializationException::class)
    fun bTestInvalidCredentials() {
        val remoteServer = DatabaseServer(REMOTE_DATABASE_LOCATION)
        remoteServer.port = 8012
        remoteServer.setCredentials("admin", "admin")
        remoteServer.start()

        try {
            val fac = RemotePersistenceManagerFactory(REMOTE_DATABASE_ENDPOINT)
            fac.setCredentials("bill", "tom")
            fac.initialize()
            fac.close()
        } finally {
            remoteServer.stop()
        }
    }

    @Test
    fun testPersistentConnection() {
        var server = DatabaseServer(REMOTE_DATABASE_LOCATION)
        server.port = 8082
        server.start()

        val factory = RemotePersistenceManagerFactory(PERSIST_CONN_DATABASE_LOCATION)
        factory.setCredentials("admin", "admin")
        factory.initialize()

        val manager = factory.persistenceManager

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "MY_ID_YO"
        manager.saveEntity<IManagedEntity>(simpleEntity)

        server.stop()

        server = DatabaseServer(REMOTE_DATABASE_LOCATION)
        server.port = 8082
        server.start()

        val foundAfterClose = manager.findById<SimpleEntity>(SimpleEntity::class.java, simpleEntity.simpleId)

        assertEquals(simpleEntity.simpleId, foundAfterClose!!.simpleId, "Failed to retrieve simpleEntity")

        factory.close()
        server.stop()
    }

    @Test
    fun testTryConnectBeforeStart() {
        val server = DatabaseServer(REMOTE_DATABASE_LOCATION)
        server.port = 8083

        val factory = RemotePersistenceManagerFactory(CONN_BEFORE_START_LOCATION)
        factory.setCredentials("admin", "admin")

        try {
            factory.initialize()
        } catch (e: InitializationException) { }

        val manager = factory.persistenceManager

        val simpleEntity = SimpleEntity()
        simpleEntity.simpleId = "MY_ID_YO"

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

            val server = DatabaseServer(REMOTE_DATABASE_LOCATION)
            server.port = 8084
            server.start()

            factory = RemotePersistenceManagerFactory(PERSIST_CONN_NOT_REOPEN_DATABASE_LOCATION)
            factory.setCredentials("admin", "admin")
            factory.initialize()

            val manager = factory.persistenceManager

            val simpleEntity = SimpleEntity()
            simpleEntity.simpleId = "MY_ID_YO"

            manager.saveEntity<IManagedEntity>(simpleEntity)

            server.stop()

            val foundAfterClose = manager.findById<SimpleEntity>(SimpleEntity::class.java, simpleEntity.simpleId)

            assertEquals(simpleEntity.simpleId, foundAfterClose!!.simpleId, "Failed to retrieve entity")
        } finally {
            factory!!.close()
        }
    }

    /**
     * Negative Test for access violation
     */
    @Test(expected = InitializationException::class)
    fun testDataFileIsNotAccessible() {
        val fac = RemotePersistenceManagerFactory(INVALID_DATABASE_LOCATION)
        fac.initialize()
    }

}
