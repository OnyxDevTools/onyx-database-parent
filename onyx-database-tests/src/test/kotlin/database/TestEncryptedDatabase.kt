package database

import com.onyx.diskmap.store.StoreType
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import entities.SimpleEntity
import org.junit.After
import org.junit.Test
import java.io.File
import kotlin.test.assertFalse

class TestEncryptedDatabase {

    @After
    fun cleanup() {
        File("C:/encrypted.onx").deleteRecursively()
    }

    @Test
    fun `Test Database is Encrypted`() {
        val factory = EmbeddedPersistenceManagerFactory("C:/encrypted.onx")
        factory.encryptDatabase = true
        factory.initialize()

        val simpleEntity = SimpleEntity()
        simpleEntity.name = "findme"
        factory.persistenceManager.saveEntity(simpleEntity)
        factory.close()

        val itemFound = File("C:/encrypted.onx/data.dat").readText().contains("findme")
        assertFalse(itemFound, "Item should not have been located in encrypted data")
    }

    @Test
    fun `Test Memory Mapped store is Encrypted`() {
        val factory = EmbeddedPersistenceManagerFactory("C:/encrypted.onx")
        factory.encryptDatabase = true
        factory.storeType = StoreType.MEMORY_MAPPED_FILE
        factory.initialize()

        val simpleEntity = SimpleEntity()
        simpleEntity.name = "findme"
        factory.persistenceManager.saveEntity(simpleEntity)
        factory.close()

        val itemFound = File("C:/encrypted.onx/data.dat").readText().contains("findme")
        assertFalse(itemFound, "Item should not have been located in encrypted data")
    }


    @Test
    fun `Test File Channel store is Encrypted`() {
        val factory = EmbeddedPersistenceManagerFactory("C:/encrypted.onx")
        factory.encryptDatabase = true
        factory.storeType = StoreType.FILE
        factory.initialize()

        val simpleEntity = SimpleEntity()
        simpleEntity.name = "findme"
        factory.persistenceManager.saveEntity(simpleEntity)
        factory.close()

        val itemFound = File("C:/encrypted.onx/data.dat").readText().contains("findme")
        assertFalse(itemFound, "Item should not have been located in encrypted data")
    }
}