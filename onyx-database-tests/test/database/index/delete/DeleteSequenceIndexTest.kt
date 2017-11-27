package database.index.delete

import com.onyx.exception.NoResultsException
import com.onyx.exception.OnyxException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.ArrayList
import kotlin.reflect.KClass
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Created by timothy.osborn on 11/3/14.
 */
@RunWith(Parameterized::class)
class DeleteSequenceIndexEntityTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun testAddDeleteSequence() {
        val entity = ImmutableSequenceIdentifierEntityForDelete()
        entity.correlation = 1
        manager.saveEntity<IManagedEntity>(entity)

        assertTrue(entity.identifier > 0, "Identifier was not generated")

        manager.deleteEntity(entity)
        assertFalse(manager.exists(entity), "Entity was not deleted")

        var pass = false
        try {
            manager.find<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        assertTrue(pass, "Entity still exist when finding")
    }

    @Test
    fun testSequenceBatchDelete() {
        val entity = ImmutableSequenceIdentifierEntityForDelete()
        entity.correlation = 1
        manager.saveEntity<IManagedEntity>(entity)

        val entity2 = ImmutableSequenceIdentifierEntityForDelete()
        entity2.correlation = 1
        manager.saveEntity<IManagedEntity>(entity2)

        val entity3 = ImmutableSequenceIdentifierEntityForDelete()
        entity3.correlation = 1
        manager.saveEntity<IManagedEntity>(entity3)

        val entity4 = ImmutableSequenceIdentifierEntityForDelete()
        entity4.correlation = 1
        manager.saveEntity<IManagedEntity>(entity4)

        val entity5 = ImmutableSequenceIdentifierEntityForDelete()
        entity5.correlation = 5
        manager.saveEntity<IManagedEntity>(entity5)

        val entity6 = ImmutableSequenceIdentifierEntityForDelete()
        entity.correlation = 6
        manager.saveEntity<IManagedEntity>(entity6)

        assertTrue(entity.identifier > 0, "First entity's identifier was not generated")
        assertTrue(entity6.identifier > 0, "Last entity's identifier was not generated")

        val entitiesToDelete = ArrayList<IManagedEntity>()
        //entitiesToDelete.add(entity);
        entitiesToDelete.add(entity2)
        entitiesToDelete.add(entity3)
        entitiesToDelete.add(entity4)
        entitiesToDelete.add(entity5)
        entitiesToDelete.add(entity6)

        manager.deleteEntities(entitiesToDelete)


        for (deletedEntity in entitiesToDelete) {
            var pass = false

            try {
                manager.find<IManagedEntity>(deletedEntity)
            } catch (e: NoResultsException) {
                pass = true
            }

            assertTrue(pass, "Failed to delete entity")
        }

        val entity7 = ImmutableSequenceIdentifierEntityForDelete()
        entity.correlation = 7
        manager.saveEntity<IManagedEntity>(entity7)

        manager.find<IManagedEntity>(entity7)
        manager.find<IManagedEntity>(entity)
    }

    @Test
    fun testSequenceSkip() {
        val entity = ImmutableSequenceIdentifierEntityForDelete()
        entity.correlation = 1
        manager.saveEntity<IManagedEntity>(entity)
        val id1 = entity.identifier

        val entity2 = ImmutableSequenceIdentifierEntityForDelete()
        entity2.correlation = 1
        entity2.identifier = id1 + 100
        manager.saveEntity<IManagedEntity>(entity2)

        val entity3 = ImmutableSequenceIdentifierEntityForDelete()
        entity3.correlation = 1
        manager.saveEntity<IManagedEntity>(entity3)


        Assert.assertTrue(entity.identifier > 0)
        Assert.assertTrue(entity2.identifier > entity.identifier)
        Assert.assertTrue(entity2.identifier - entity.identifier == 100L)

        manager.deleteEntity(entity2)

        manager.find<IManagedEntity>(entity)
        manager.find<IManagedEntity>(entity3)

        var pass = false
        try {
            manager.find<IManagedEntity>(entity2)
        } catch (e: NoResultsException) {
            pass = true
        }

        assertTrue(pass, "Failure to delete entity when skipping an index")

    }
}
