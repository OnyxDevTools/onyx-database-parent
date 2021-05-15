package database.exceptions

import com.onyx.exception.EntityClassNotFoundException
import com.onyx.exception.EntityTypeMatchException
import com.onyx.exception.InvalidIdentifierException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.SimpleEntity
import entities.exception.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.ArrayList
import kotlin.reflect.KClass
import kotlin.test.assertNull

@RunWith(Parameterized::class)
class TestEntitySaveException(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test(expected = EntityClassNotFoundException::class)
    fun testNoEntitySave() {
        val entity = NoEntityAnnotationClass()
        entity.id = "Hiya"
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test(expected = InvalidIdentifierException::class)
    fun testNoIDEntity() {
        val entity = NoIdEntity()
        entity.attr = 3
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test
    fun testInvalidIDEntity() {
        val entity = InvalidIDEntity()
        entity.id = 23.3
        manager.saveEntity<IManagedEntity>(entity)
        manager.find<IManagedEntity>(entity)
    }

    @Test(expected = InvalidIdentifierException::class)
    fun testInvalidGenerator() {
        val entity = InvalidIDGeneratorEntity()
        entity.id = "ASDF"
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test(expected = EntityClassNotFoundException::class)
    fun testNoInterfaceException() {
        val entity = EntityNoIPersistedEntity()
        entity.id = "ASDF"
        val entities = ArrayList<Any>()
        entities.add(entity)
        @Suppress("UNCHECKED_CAST") // Suppressed because we want to replicate the scenario of passing wrong type in
        manager.saveEntities(entities as ArrayList<IManagedEntity>)
    }

    @Test(expected = EntityTypeMatchException::class)
    fun testInvalidAttributeType() {
        val entity = InvalidAttributeTypeEntity()
        entity.id = "ASDF"
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test
    fun testInvalidFindById() {
        // Negative test to ensure error is not thrown when running findById
        val entity = SimpleEntity()
        entity.simpleId = "1"
        entity.name = "Chris"
        manager.saveEntity<IManagedEntity>(entity)
        val savedEntity = manager.findById<IManagedEntity>(entity.javaClass, 20) as SimpleEntity?
        assertNull(savedEntity, "Entity should not exist with that identifier")
    }
}