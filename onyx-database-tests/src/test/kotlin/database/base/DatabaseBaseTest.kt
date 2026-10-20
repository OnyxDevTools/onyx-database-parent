package database.base

import com.onyx.application.impl.DatabaseServer
import com.onyx.exception.InitializationException
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.factory.impl.CacheManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.runners.Parameterized
import java.io.File
import java.math.BigInteger
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import kotlin.reflect.KClass

/**
 * This class is a base database class for the base unit test.  It will iterate through
 * the PersistenceManager factories
 *
 * To override which persistence manager factory classes that apply to a given unit test,
 * override the persistenceManagersToTest method
 *
 */
open class DatabaseBaseTest constructor(open var factoryClass: KClass<*>) {

    protected lateinit var manager: PersistenceManager
    protected lateinit var factory: PersistenceManagerFactory
    private var context: SchemaContext? = null

    /**
     * Initialize Database
     * @throws InitializationException
     */
    @Before
    open fun initialize() {
        factory = when (factoryClass) {
            EmbeddedPersistenceManagerFactory::class -> EmbeddedPersistenceManagerFactory(EMBEDDED_DATABASE_LOCATION)
            RemotePersistenceManagerFactory::class ->   {
                val factory = RemotePersistenceManagerFactory(REMOTE_DATABASE_ENDPOINT)
/*                factory.sslKeystoreFilePath = "securesocket.jks"
                factory.sslTrustStoreFilePath = "securesocket.jks"
                factory.sslKeystorePassword = "inc0rrect"
                factory.sslTrustStorePassword = "mu\$tch8ng3"
                factory.sslStorePassword = "mu\$tch8ng3"*/
                factory
            }
            CacheManagerFactory::class -> CacheManagerFactory()
            else -> CacheManagerFactory()
        }
        factory.setCredentials("admin", "admin")
        factory.initialize()
        context = factory.schemaContext
        manager = factory.persistenceManager
    }

    @After
    open fun shutdown() {
        factory.close()
        context = null
    }

    companion object {

        const val EMBEDDED_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/embeddedOnyx.oxd"
        const val REMOTE_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/remoteOnyx.oxd"
        const val WEB_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/webOnyx.oxd"
        val REMOTE_DATABASE_ENDPOINT: String
            get() = "onx://localhost:${checkNotNull(remoteServer) { "Test server has not started" }.port}"

        /**
         * Delete all databases prior to running unit test
         */
        protected fun deleteAllDatabases() {
            deleteDatabase(EMBEDDED_DATABASE_LOCATION)
            deleteDatabase(REMOTE_DATABASE_LOCATION)
            deleteDatabase(WEB_DATABASE_LOCATION)
        }

        var remoteServer:DatabaseServer? = null

        /**
         * Start servers for the persistence manager factories that require services
         */
        private fun startServers() {
            startRemoteDatabase()
        }

        private fun startRemoteDatabase() {
            if(remoteServer == null) {
                val server = DatabaseServer(REMOTE_DATABASE_LOCATION)
                server.port = ServerSocket(0).use { it.localPort }
                server.setCredentials("admin", "admin")
                try {
                    server.start()
                    remoteServer = server
                } catch (failure: Throwable) {
                    runCatching { server.stop() }.exceptionOrNull()?.let(failure::addSuppressed)
                    throw failure
                }
            }
        }

        fun deleteDatabase(location:String) {
            val database = File(location)
            if (database.exists()) {
                delete(database)
            }
            database.delete()
            Contexts.get(location)?.let { Contexts.remove(it) }
        }

        /**
         * Delete directory recursively
         */
        fun delete(f: File) {
            if (f.isDirectory) {
                for (c in f.listFiles()!!)
                    delete(c)
            }
            f.delete()
        }

        var random = SecureRandom()

        var databasesStarted = false

        val randomString: String
            get() = BigInteger(130, random).toString(32)

        val randomInteger: Int
            get() = BigInteger(100, random).toInt()

        @AfterClass
        @JvmStatic
        fun afterClass() = Unit

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            if(!databasesStarted) {
                deleteAllDatabases()
                startServers()
                databasesStarted = true
            }
        }

        /**
         * Helper to run async on an executor
         */
        fun <T> async(executor:ExecutorService, block: () -> T): Future<T> = executor.submit<T> { block.invoke() }

        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(EmbeddedPersistenceManagerFactory::class)
    }
}
