package database.base

import com.onyx.application.impl.DatabaseServer
import com.onyx.application.impl.WebDatabaseServer
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
    protected var context: SchemaContext? = null

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
            CacheManagerFactory::class ->               CacheManagerFactory()
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

        protected val EMBEDDED_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/embeddedOnyx.oxd"
        protected val REMOTE_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/remoteOnyx.oxd"
        protected val WEB_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/webOnyx.oxd"
        protected val REMOTE_DATABASE_ENDPOINT = "onx://localhost:8095"

        /**
         * Delete all databases prior to running unit test
         */
        private fun deleteAllDatabases() {
            deleteDatabase(EMBEDDED_DATABASE_LOCATION)
            deleteDatabase(REMOTE_DATABASE_LOCATION)
            deleteDatabase(WEB_DATABASE_LOCATION)
        }

        private var remoteServer:DatabaseServer? = null

        /**
         * Start servers for the persistence manager factories that require services
         */
        private fun startServers() {
            startRemoteDatabase()
        }

        private fun startRemoteDatabase() {
            if(remoteServer == null) {
                remoteServer = DatabaseServer(REMOTE_DATABASE_LOCATION)
                remoteServer!!.port = 8095
/*                remoteServer!!.sslKeystoreFilePath = "securesocket.jks"
                remoteServer!!.sslTrustStoreFilePath = "securesocket.jks"
                remoteServer!!.sslKeystorePassword = "inc0rrect"
                remoteServer!!.sslTrustStorePassword = "mu\$tch8ng3"
                remoteServer!!.sslStorePassword = "mu\$tch8ng3"*/
                remoteServer!!.setCredentials("admin", "admin")
                remoteServer!!.start()
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
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(CacheManagerFactory::class, EmbeddedPersistenceManagerFactory::class, RemotePersistenceManagerFactory::class)
    }
}