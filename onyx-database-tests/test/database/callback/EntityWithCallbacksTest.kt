package database.callback

import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.EntityWithCallbacks
import entities.SequencedEntityWithCallbacks
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class EntityWithCallbacksTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun testPrePersistCallbacksForHashIndex() {
        //Create new entity
        val entity = EntityWithCallbacks()
        entity.id = "1"
        entity.name = "INSERT"
        manager.saveEntity<IManagedEntity>(entity)

        //retrieve the entity
        val savedEntity = EntityWithCallbacks()
        savedEntity.id = "1"
        manager.find<IManagedEntity>(savedEntity)

        //Update the entity
        savedEntity.name = savedEntity.name!! + "&UPDATE"
        manager.saveEntity<IManagedEntity>(savedEntity)

        assertTrue(savedEntity.name!!.contains("INSERT"), "Insert callback not invoked")
        assertTrue(savedEntity.name!!.contains("UPDATE"), "UPDATE callback not invoked")
        assertTrue(savedEntity.name!!.contains("_PrePersist"), "_PrePersist callback not invoked")
        assertTrue(savedEntity.name!!.contains("_PreInsert"), "_PreInsert callback not invoked")
        assertTrue(savedEntity.name!!.contains("_PreUpdate"), "_PreUpdate callback not invoked")
    }

    @Test
    fun testPrePersistCallbacksForSequenceIndex() {
        //Create new entity
        var entity = SequencedEntityWithCallbacks()
        entity.name = "INSERT"
        entity = manager.saveEntity(entity)

        //retrieve the entity
        val savedEntity = manager.find<IManagedEntity>(entity) as SequencedEntityWithCallbacks

        //Update the entity
        savedEntity.name = savedEntity.name + "&UPDATE"
        manager.saveEntity<IManagedEntity>(savedEntity)

        assertTrue(savedEntity.name.contains("INSERT"), "Insert callback not invoked")
        assertTrue(savedEntity.name.contains("UPDATE"), "UPDATE callback not invoked")
        assertTrue(savedEntity.name.contains("_PrePersist"), "_PrePersist callback not invoked")
        assertTrue(savedEntity.name.contains("_PreInsert"), "_PreInsert callback not invoked")
        assertTrue(savedEntity.name.contains("_PreUpdate"), "_PreUpdate callback not invoked")
    }

    @Test
    fun testPostPersistCallbacksForStandardIndex() {
        //Create new entity
        val entity = EntityWithCallbacks()
        entity.id = "1"
        entity.name = "INSERT"

        manager.deleteEntity(entity)
        manager.saveEntity<IManagedEntity>(entity)

        assertTrue(entity.name!!.contains("_PostInsert"), "Post insert callback not insert")

        //retrieve the entity
        val savedEntity = EntityWithCallbacks()
        savedEntity.id = "1"
        manager.find<IManagedEntity>(savedEntity)

        //Update the entity
        savedEntity.name = savedEntity.name!! + "&UPDATE"
        manager.saveEntity<IManagedEntity>(savedEntity)

        assertTrue(savedEntity.name!!.contains("INSERT"), "Insert callback not invoked")
        assertTrue(savedEntity.name!!.contains("UPDATE"), "UPDATE callback not invoked")
        assertTrue(savedEntity.name!!.contains("_PrePersist"), "_PrePersist callback not invoked")
        assertTrue(savedEntity.name!!.contains("_PreUpdate"), "_PreUpdate callback not invoked")
    }

    @Test
    fun testPostPersistCallbacksForSequenceIndex() {
        //Create new entity
        var entity = SequencedEntityWithCallbacks()
        entity.name = "INSERT"
        entity = manager.saveEntity(entity)

        assertTrue(entity.name.contains("_PostInsert"), "Post insert callback not invoked")

        //retrieve the entity
        val savedEntity = manager.find<IManagedEntity>(entity) as SequencedEntityWithCallbacks

        //Update the entity
        savedEntity.name = savedEntity.name + "&UPDATE"
        manager.saveEntity<IManagedEntity>(savedEntity)

        assertTrue(savedEntity.name.contains("INSERT"), "Insert callback not invoked")
        assertTrue(savedEntity.name.contains("UPDATE"), "UPDATE callback not invoked")
        assertTrue(savedEntity.name.contains("_PrePersist"), "_PrePersist callback not invoked")
        assertTrue(savedEntity.name.contains("_PreUpdate"), "_PreUpdate callback not invoked")
    }
}
