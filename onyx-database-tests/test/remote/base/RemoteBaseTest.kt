package remote.base

import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.impl.RemoteSchemaContext
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.persistence.manager.PersistenceManager
import com.onyx.application.impl.DatabaseServer
import org.junit.AfterClass

import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.SecureRandom

import org.junit.Assert.fail

/**
 * Created by timothy.osborn on 12/11/14.
 */
open class RemoteBaseTest {

    protected var manager: PersistenceManager? = null
    protected var context: RemoteSchemaContext? = null

    protected var random = SecureRandom()
    protected var factory: RemotePersistenceManagerFactory? = null

    /**
     * Initialize Database
     */
    @Throws(InitializationException::class)
    protected fun initialize() {
        if (context == null) {
            factory = RemotePersistenceManagerFactory(DATABASE_LOCATION)
            factory!!.setCredentials("admin", "admin")
            factory!!.initialize()

            context = factory!!.schemaContext as RemoteSchemaContext
            manager = factory!!.persistenceManager
        }
    }

    @Throws(IOException::class)
    protected fun shutdown() {
        if (factory != null)
            factory!!.close()
        context = null
        factory = null
        manager = null
    }

    fun save(entity: IManagedEntity) {
        try {
            manager!!.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            e.printStackTrace()
            fail("Error saving entity")
        }

    }

    fun find(entity: IManagedEntity): IManagedEntity? {
        try {
            return manager!!.find(entity)
        } catch (e: OnyxException) {
            e.printStackTrace()
            fail("Error finding entity")
        }

        return null
    }

    fun delete(entity: IManagedEntity) {
        try {
            manager!!.deleteEntity(entity)
        } catch (e: OnyxException) {
            e.printStackTrace()
            fail("Error deleting entity")
        }

    }

    fun initialize(entity: IManagedEntity, attribute: String) {
        try {
            manager!!.initialize(entity, attribute)
        } catch (e: OnyxException) {
            e.printStackTrace()
            fail("Error saving entity")
        }

    }

    protected val randomString: String
        get() = BigInteger(130, random).toString(32)

    protected val randomInteger: Int
        get() = BigInteger(10, random).toInt()

    companion object {

        var databaseServer: DatabaseServer? = null

        protected val DATABASE_LOCATION = "onx://localhost:8080"
        protected val LOCAL_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/server.oxd"

        private fun delete(f: File) {
            if (f.isDirectory) {
                for (c in f.listFiles()!!)
                    delete(c)
            }
            f.delete()
        }

        fun deleteDatabase() {
            val database = File(LOCAL_DATABASE_LOCATION)
            if (database != null && database.exists()) {
                delete(database)
            }
            database.delete()
        }

        @AfterClass
        fun afterClass() {

        }
    }
}
