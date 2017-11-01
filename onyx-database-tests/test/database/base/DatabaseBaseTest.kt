package database.base

import com.onyx.application.impl.DatabaseServer
import com.onyx.application.impl.WebDatabaseServer
import com.onyx.exception.InitializationException
import com.onyx.extension.common.delay
import com.onyx.persistence.context.Contexts
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.factory.impl.CacheManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.persistence.factory.impl.WebPersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.runners.Parameterized
import java.io.File
import java.math.BigInteger
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
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
    protected var context: SchemaContext? = null

    /**
     * Initialize Database
     * @throws InitializationException
     */
    @Before
    open fun initialize() {
        factory = when (factoryClass) {
            EmbeddedPersistenceManagerFactory::class -> EmbeddedPersistenceManagerFactory(EMBEDDED_DATABASE_LOCATION)
            RemotePersistenceManagerFactory::class ->   RemotePersistenceManagerFactory(REMOTE_DATABASE_ENDPOINT)
            CacheManagerFactory::class ->               CacheManagerFactory()
            WebPersistenceManagerFactory::class ->      WebPersistenceManagerFactory(WEB_DATABASE_ENDPOINT)
            else -> CacheManagerFactory()
        }
        factory.setCredentials("admin", "admin")
        factory.initialize()
        context = factory.schemaContext
        manager = factory.persistenceManager
    }

    @After
    fun shutdown() {
        factory.close()
        context = null
    }

    companion object {

        protected val EMBEDDED_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/embeddedOnyx.oxd"
        protected val REMOTE_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/remoteOnyx.oxd"
        protected val WEB_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/webOnyx.oxd"
        protected val WEB_DATABASE_ENDPOINT = "http://localhost:8090"
        protected val REMOTE_DATABASE_ENDPOINT = "onx://localhost:8080"

        /**
         * Delete all databases prior to running unit test
         */
        private fun deleteAllDatabases() {
            deleteDatabase(EMBEDDED_DATABASE_LOCATION)
            deleteDatabase(REMOTE_DATABASE_LOCATION)
            deleteDatabase(WEB_DATABASE_LOCATION)
        }

        private var webServer:WebDatabaseServer? = null
        private var remoteServer:DatabaseServer? = null

        /**
         * Start servers for the persistence manager factories that require services
         */
        private fun startServers() {
            startRemoteDatabase()
            startWebDatabase()
        }

        private fun startRemoteDatabase() {
            if(remoteServer == null) {
                remoteServer = DatabaseServer(REMOTE_DATABASE_LOCATION)
                remoteServer!!.port = 8080
                remoteServer!!.setCredentials("admin", "admin")
                remoteServer!!.start()
                delay(1000, TimeUnit.MILLISECONDS)
            }
        }

        private fun startWebDatabase() {
            if(webServer == null) {
                webServer = WebDatabaseServer(WEB_DATABASE_LOCATION)
                webServer!!.port = 4555
                webServer!!.webServicePort = 8090
                webServer!!.setCredentials("admin", "admin")
                webServer!!.start()
            }
        }

        fun deleteDatabase(location:String) {
            val database = File(location)
            if (database.exists()) {
                delete(database)
            }
            database.delete()
            Contexts.clear()
        }

        /**
         * Delete directory recursively
         */
        private fun delete(f: File) {
            if (f.isDirectory) {
                for (c in f.listFiles()!!)
                    delete(c)
            }
            f.delete()
        }

        protected var random = SecureRandom()

        private var databasesStarted = false

        val randomString: String
            get() = BigInteger(130, random).toString(32)

        val randomInteger: Int
            get() = BigInteger(10, random).toInt()

        @AfterClass
        @JvmStatic
        fun afterClass() {}

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
         * Helper to run asyn on an executor
         */
        fun <T> async(executor:ExecutorService, block: () -> T): Future<T> = executor.submit<T> { block.invoke() }

        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(CacheManagerFactory::class, EmbeddedPersistenceManagerFactory::class, WebPersistenceManagerFactory::class, RemotePersistenceManagerFactory::class)
    }
}