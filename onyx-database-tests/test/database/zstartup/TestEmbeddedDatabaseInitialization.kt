package database.zstartup

import com.onyx.exception.InitializationException
import com.onyx.persistence.factory.PersistenceManagerFactory
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import database.base.DatabaseBaseTest
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Created by timothy.osborn on 12/11/14.
 *
 * Tests the initialization of the database
 */
class TestEmbeddedDatabaseInitialization {

    /**
     * Positive test to ensure a database can initialize
     */
    @Test
    fun testInitializeDatabase() {
        var fac: PersistenceManagerFactory = EmbeddedPersistenceManagerFactory(TMP_DATABASE_LOCATION)
        fac.setCredentials("tim", "osborn")
        fac.initialize()
        fac.close()

        fac = EmbeddedPersistenceManagerFactory(TMP_DATABASE_LOCATION)
        fac.setCredentials("tim", "osborn")
        fac.initialize()

        assertEquals(TMP_DATABASE_LOCATION, fac.databaseLocation)
        assertTrue(File(TMP_DATABASE_LOCATION).exists())

        fac.close()
    }

    /**
     * Negative Test for access violation
     */
    @Test(expected = InitializationException::class)
    fun testDataFileIsNotAccessible() {
        val fac = EmbeddedPersistenceManagerFactory(INVALID_DATABASE_LOCATION)
        fac.initialize()
    }

    /**
     * Negative Test for invalid credentials
     */
    @Test(expected = InitializationException::class)
    fun testInvalidCredentials() {
        val fac = EmbeddedPersistenceManagerFactory(TMP_DATABASE_LOCATION)
        fac.setCredentials("bill", "tom")
        fac.initialize()
    }

    companion object {
        val INVALID_DATABASE_LOCATION = "/Users/some_user_that_does_not_exist/database.onx"
        val TMP_DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/embedded_init_test.onx"

        @Suppress("MemberVisibilityCanPrivate")
        @BeforeClass
        @JvmStatic
        fun deleteDatabase() = DatabaseBaseTest.deleteDatabase(TMP_DATABASE_LOCATION)

        @AfterClass
        @JvmStatic
        fun after() = deleteDatabase()
    }
}
