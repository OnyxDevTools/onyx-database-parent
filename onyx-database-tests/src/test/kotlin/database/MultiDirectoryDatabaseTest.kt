package database

import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.manager.findById
import entities.MultiLocationEntity
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiDirectoryDatabaseTest {
    @Test
    @Ignore
    fun testMultipleLocationDatabase() {
        val localDatabase = EmbeddedPersistenceManagerFactory("C:/Sandbox/Onyx/Tests/multiLocation.oxd")
        localDatabase.initialize()

        val persistenceManager = localDatabase.persistenceManager
        val entity = MultiLocationEntity().apply {
            this.id = "myid"
        }
        persistenceManager.saveEntity(entity)

        val retrieved = persistenceManager.findById<MultiLocationEntity>("myid")
        assertEquals(retrieved?.id, entity.id, "Entity was not retrieved")

        assertTrue(File("C:/Sandbox/Onyx/Tests/multistorage/allAttribute.dat").exists(), "File was not stored in alternative path")
    }
}