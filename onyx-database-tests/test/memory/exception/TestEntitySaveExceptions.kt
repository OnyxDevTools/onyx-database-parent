package memory.exception

import category.InMemoryDatabaseTests
import com.onyx.exception.*
import com.onyx.persistence.IManagedEntity
import entities.SimpleEntity
import entities.exception.*
import junit.framework.Assert
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.util.ArrayList

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category(InMemoryDatabaseTests::class)
class TestEntitySaveExceptions : memory.base.BaseTest() {

    @Before
    @Throws(InitializationException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @get:Synchronized private var z = 0

    @Synchronized protected fun increment() {
        z++
    }

    @Test(expected = EntityClassNotFoundException::class)
    @Throws(OnyxException::class)
    fun testNoEntitySave() {
        val entity = NoEntityAnnotationClass()
        entity.id = "Hiya"

        manager.saveEntity<IManagedEntity>(entity)
    }


    @Test(expected = InvalidIdentifierException::class)
    @Throws(OnyxException::class)
    fun testNoIDEntity() {
        val entity = NoIdEntity()
        entity.attr = 3
        manager.saveEntity<IManagedEntity>(entity)
    }

    // Changed to validate you can have a double as an id
    @Test
    @Throws(OnyxException::class)
    fun testInvalidIDEntity() {
        val entity = InvalidIDEntity()
        entity.id = 23.3
        manager.saveEntity<IManagedEntity>(entity)

        manager.find<IManagedEntity>(entity)
    }

    @Test(expected = InvalidIdentifierException::class)
    @Throws(OnyxException::class)
    fun testInvalidGenerator() {
        val entity = InvalidIDGeneratorEntity()
        entity.id = "ASDF"
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test(expected = EntityClassNotFoundException::class)
    @Throws(OnyxException::class)
    fun testNoInterfaceException() {
        val entity = EntityNoIPersistedEntity()
        entity.id = "ASDF"
        val entities = ArrayList<Any>()
        entities.add(entity)

        @Suppress("UNCHECKED_CAST")
        manager.saveEntities(entities as ArrayList<IManagedEntity>)
    }

    @Test(expected = EntityTypeMatchException::class)
    @Throws(OnyxException::class)
    fun testInvalidAttributeType() {
        val entity = InvalidAttributeTypeEntity()
        entity.id = "ASDF"
        manager.saveEntity<IManagedEntity>(entity)
    }

    @Test
    @Throws(OnyxException::class)
    fun testInvalidFindById() {
        //Save entity
        val entity = SimpleEntity()
        entity.simpleId = "1"
        entity.name = "Chris"
        manager.saveEntity<IManagedEntity>(entity)
        //Retreive entity using findById method using the wrong data type for id
        val savedEntity = manager.findById<IManagedEntity>(entity.javaClass, 1) as SimpleEntity?
        Assert.assertNull(savedEntity)

    }

}
