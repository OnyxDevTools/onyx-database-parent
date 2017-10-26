package web

import category.WebServerTests
import com.onyx.exception.InitializationException
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.junit.experimental.categories.Category
import web.base.BaseTest

import java.io.File

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Tests the initialization of the database
 */
@Category(WebServerTests::class)
class TestDatabaseInitialization : BaseTest() {

    /**
     * Positive test
     *
     * @throws Exception
     */
    @Test
    @Throws(Exception::class)
    fun testInitializeDatabase() {
        var fac: PersistenceManagerFactory = EmbeddedPersistenceManagerFactory(TMP_DATABASE_LOCATION)
        fac.setCredentials("tim", "osborn")
        fac.initialize()

        manager = EmbeddedPersistenceManager(fac.schemaContext)
        fac.close()

        fac = EmbeddedPersistenceManagerFactory(TMP_DATABASE_LOCATION)
        fac.setCredentials("tim", "osborn")
        fac.initialize()

        Assert.assertTrue(fac.databaseLocation === TMP_DATABASE_LOCATION)
        Assert.assertTrue(File(TMP_DATABASE_LOCATION).exists())

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
        val fac = EmbeddedPersistenceManagerFactory(INVALID_DATABASE_LOCATION)
        fac.initialize()

        manager = EmbeddedPersistenceManager(null!!)
        manager.context = fac.schemaContext
    }

    /**
     * Negative Test for invalid credentials
     *
     * @throws Exception
     */
    @Test(expected = InitializationException::class)
    @Throws(Exception::class)
    fun testInvalidCredentials() {
        val fac = EmbeddedPersistenceManagerFactory(TMP_DATABASE_LOCATION)
        fac.setCredentials("bill", "tom")
        fac.initialize()

        manager = EmbeddedPersistenceManager(null!!)
        manager.context = fac.schemaContext
    }

    companion object {
        val INVALID_DATABASE_LOCATION = "/Users/ashley.hampshire"
        val TMP_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/tmpdatbase"

        @BeforeClass
        fun deleteDatabase() {
            val database = File(TMP_DATABASE_LOCATION)
            if (database.exists()) {
                BaseTest.delete(database)
            }
            database.delete()
        }

        @AfterClass
        fun after() {
            File(TMP_DATABASE_LOCATION + "/tmp").delete()
            File(TMP_DATABASE_LOCATION + "/lock").delete()
            File(TMP_DATABASE_LOCATION).delete()
        }
    }
}
