package remote.callbacks

import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.IManagedEntity
import entities.EntityWithCallbacks
import entities.SequencedEntityWithCallbacks
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import remote.base.RemoteBaseTest

import java.io.IOException

/**
 * Created by Chris Osborn on 12/29/2014.
 */
class EntityWithCallbacksTest : RemoteBaseTest() {


    @Before
    @Throws(InitializationException::class, InterruptedException::class)
    fun before() {
        initialize()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        shutdown()
    }

    @Test
    @Throws(OnyxException::class)
    fun testPrePersistCallbacksForHashIndex() {
        //Create new entity
        val entity = EntityWithCallbacks()
        entity.id = "1"
        entity.name = "INSERT"
        manager!!.saveEntity<IManagedEntity>(entity)

        //retrieve the entity
        val savedEntity = EntityWithCallbacks()
        savedEntity.id = "1"
        manager!!.find<IManagedEntity>(savedEntity)

        //Update the entity
        savedEntity.name = savedEntity.name!! + "&UPDATE"
        manager!!.saveEntity<IManagedEntity>(savedEntity)

        //Assert
        Assert.assertTrue(savedEntity.name!!.contains("INSERT"))
        Assert.assertTrue(savedEntity.name!!.contains("UPDATE"))
        Assert.assertTrue(savedEntity.name!!.contains("_PrePersist"))
        Assert.assertTrue(savedEntity.name!!.contains("_PreInsert"))
        Assert.assertTrue(savedEntity.name!!.contains("_PreUpdate"))
    }

    @Test
    @Throws(OnyxException::class)
    fun testPrePersistCallbacksForSequenceIndex() {
        //Create new entity
        var entity = SequencedEntityWithCallbacks()
        entity.name = "INSERT"
        entity = manager!!.saveEntity(entity)

        //retrieve the entity
        val savedEntity = manager!!.find<IManagedEntity>(entity) as SequencedEntityWithCallbacks

        //Update the entity
        savedEntity.name = savedEntity.name + "&UPDATE"
        manager!!.saveEntity<IManagedEntity>(savedEntity)

        //Assert
        Assert.assertTrue(savedEntity.name.contains("INSERT"))
        Assert.assertTrue(savedEntity.name.contains("UPDATE"))
        Assert.assertTrue(savedEntity.name.contains("_PrePersist"))
        Assert.assertTrue(savedEntity.name.contains("_PreInsert"))
        Assert.assertTrue(savedEntity.name.contains("_PreUpdate"))
    }

    @Test
    @Throws(OnyxException::class)
    fun testPostPersistCallbacksForHashIndex() {
        //Create new entity
        val entity = EntityWithCallbacks()
        entity.id = "1"
        entity.name = "INSERT"

        try {
            manager!!.deleteEntity(entity)

        } catch (e: OnyxException) {
        }

        manager!!.saveEntity<IManagedEntity>(entity)

        Assert.assertTrue(entity.name!!.contains("_PostInsert"))

        //retrieve the entity
        val savedEntity = EntityWithCallbacks()
        savedEntity.id = "1"
        manager!!.find<IManagedEntity>(savedEntity)

        //Update the entity
        savedEntity.name = savedEntity.name!! + "&UPDATE"
        manager!!.saveEntity<IManagedEntity>(savedEntity)

        //Assert
        Assert.assertTrue(savedEntity.name!!.contains("INSERT"))
        Assert.assertTrue(savedEntity.name!!.contains("UPDATE"))
        Assert.assertTrue(savedEntity.name!!.contains("_PostPersist"))
        Assert.assertTrue(savedEntity.name!!.contains("_PostUpdate"))
    }

    @Test
    @Throws(OnyxException::class)
    fun testPostPersistCallbacksForSequenceIndex() {
        //Create new entity
        var entity = SequencedEntityWithCallbacks()
        entity.name = "INSERT"
        entity = manager!!.saveEntity(entity)

        Assert.assertTrue(entity.name.contains("_PostInsert"))

        //retrieve the entity
        val savedEntity = manager!!.find<IManagedEntity>(entity) as SequencedEntityWithCallbacks

        //Update the entity
        savedEntity.name = savedEntity.name + "&UPDATE"
        manager!!.saveEntity<IManagedEntity>(savedEntity)

        //Assert
        Assert.assertTrue(savedEntity.name.contains("INSERT"))
        Assert.assertTrue(savedEntity.name.contains("UPDATE"))
        Assert.assertTrue(savedEntity.name.contains("_PostPersist"))
        Assert.assertTrue(savedEntity.name.contains("_PostUpdate"))
    }

}
