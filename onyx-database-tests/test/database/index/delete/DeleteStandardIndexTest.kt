package database.index.delete

import com.onyx.exception.OnyxException
import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.AllAttributeEntity
import entities.identifiers.IntegerIdentifierEntity
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Date
import kotlin.reflect.KClass
import kotlin.test.assertTrue

/**
 * Created by timothy.osborn on 11/3/14.
 */
@RunWith(Parameterized::class)
class DeleteStandardIndexTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test(expected = NoResultsException::class)
    fun testDeleteEntity() {
        val entity = AllAttributeEntity()
        entity.id = "ThisIsSomeIDOfAnEntityToDelete"
        entity.booleanValue = false
        entity.doubleValue = 234.3
        entity.dateValue = Date()
        entity.stringValue = "Hiya"
        manager.saveEntity<IManagedEntity>(entity)

        manager.deleteEntity(entity)
        manager.find<IManagedEntity>(entity)
    }

    @Test
    fun testDeleteHashAndReSave() {
        var entity = IntegerIdentifierEntity()
        entity.identifier = 1
        manager.saveEntity<IManagedEntity>(entity)
        manager.find<IManagedEntity>(entity)
        manager.deleteEntity(entity)
        var pass = false
        try {
             manager.find<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        assertTrue(pass, "Failed to Delete IntegerIdentifierEntity with ID 1")

        entity = IntegerIdentifierEntity()
        entity.identifier = 1
        manager.saveEntity<IManagedEntity>(entity)

        try {
            manager.find<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = false
            }
        }

        assertTrue(pass, "Failed to re-manager.saveEntity<IManagedEntity> IntegerIdentifierEntity with ID 1")
    }

    @Test
    fun testDeleteRoot() {
        val entity1 = IntegerIdentifierEntity()
        entity1.identifier = 1
        manager.saveEntity<IManagedEntity>(entity1)

        val entity2 = IntegerIdentifierEntity()
        entity2.identifier = 2
        manager.saveEntity<IManagedEntity>(entity2)

        val entity3 = IntegerIdentifierEntity()
        entity3.identifier = -1
        manager.saveEntity<IManagedEntity>(entity3)

        val entity4 = IntegerIdentifierEntity()
        entity4.identifier = 8
        manager.saveEntity<IManagedEntity>(entity4)

        val entity5 = IntegerIdentifierEntity()
        entity5.identifier = 6
        manager.saveEntity<IManagedEntity>(entity5)

        val entity6 = IntegerIdentifierEntity()
        entity6.identifier = 10
        manager.saveEntity<IManagedEntity>(entity6)

        manager.deleteEntity(entity1)
        manager.find<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity3)
        manager.find<IManagedEntity>(entity4)
        manager.find<IManagedEntity>(entity5)
        manager.find<IManagedEntity>(entity6)

        var pass = false
        try {
            manager.find<IManagedEntity>(entity1)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        assertTrue(pass, "Failed to delete a root index entity")
    }

    @Test
    fun testDeleteParent() {
        val entity1 = IntegerIdentifierEntity()
        entity1.identifier = 1
        manager.saveEntity<IManagedEntity>(entity1)

        val entity2 = IntegerIdentifierEntity()
        entity2.identifier = 2
        manager.saveEntity<IManagedEntity>(entity2)

        val entity3 = IntegerIdentifierEntity()
        entity3.identifier = -1
        manager.saveEntity<IManagedEntity>(entity3)

        val entity4 = IntegerIdentifierEntity()
        entity4.identifier = 8
        manager.saveEntity<IManagedEntity>(entity4)

        val entity5 = IntegerIdentifierEntity()
        entity5.identifier = 6
        manager.saveEntity<IManagedEntity>(entity5)

        val entity6 = IntegerIdentifierEntity()
        entity6.identifier = 10
        manager.saveEntity<IManagedEntity>(entity6)

        manager.deleteEntity(entity5)
        manager.find<IManagedEntity>(entity1)
        manager.find<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity3)
        manager.find<IManagedEntity>(entity4)
        manager.find<IManagedEntity>(entity6)

        var pass = false
        try {
            manager.find<IManagedEntity>(entity5)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        assertTrue(pass, "Failed to delete a head node index")
    }

    @Test
    fun testDeleteLeft() {
        val entity1 = IntegerIdentifierEntity()
        entity1.identifier = 1
        manager.saveEntity<IManagedEntity>(entity1)

        val entity2 = IntegerIdentifierEntity()
        entity2.identifier = 2
        manager.saveEntity<IManagedEntity>(entity2)

        val entity3 = IntegerIdentifierEntity()
        entity3.identifier = 4
        manager.saveEntity<IManagedEntity>(entity3)

        val entity7 = IntegerIdentifierEntity()
        entity7.identifier = 3
        manager.saveEntity<IManagedEntity>(entity7)

        val entity4 = IntegerIdentifierEntity()
        entity4.identifier = 8
        manager.saveEntity<IManagedEntity>(entity4)

        val entity5 = IntegerIdentifierEntity()
        entity5.identifier = 6
        manager.saveEntity<IManagedEntity>(entity5)

        val entity6 = IntegerIdentifierEntity()
        entity6.identifier = 10
        manager.saveEntity<IManagedEntity>(entity6)


        manager.deleteEntity(entity3)
        manager.find<IManagedEntity>(entity1)
        manager.find<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity4)
        manager.find<IManagedEntity>(entity5)
        manager.find<IManagedEntity>(entity6)
        manager.find<IManagedEntity>(entity7)

        var pass = false
        try {
            manager.find<IManagedEntity>(entity3)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        assertTrue(pass, "Failed to delete entity")
    }

    @Test
    fun testDeleteRight() {
        val entity1 = IntegerIdentifierEntity()
        entity1.identifier = 1
        manager.saveEntity<IManagedEntity>(entity1)

        val entity2 = IntegerIdentifierEntity()
        entity2.identifier = 2
        manager.saveEntity<IManagedEntity>(entity2)


        val entity4 = IntegerIdentifierEntity()
        entity4.identifier = 8
        manager.saveEntity<IManagedEntity>(entity4)

        val entity5 = IntegerIdentifierEntity()
        entity5.identifier = 6
        manager.saveEntity<IManagedEntity>(entity5)

        val entity6 = IntegerIdentifierEntity()
        entity6.identifier = 10
        manager.saveEntity<IManagedEntity>(entity6)

        val entity8 = IntegerIdentifierEntity()
        entity8.identifier = 11
        manager.saveEntity<IManagedEntity>(entity8)


        val entity3 = IntegerIdentifierEntity()
        entity3.identifier = 4
        manager.saveEntity<IManagedEntity>(entity3)

        val entity7 = IntegerIdentifierEntity()
        entity7.identifier = 3
        manager.saveEntity<IManagedEntity>(entity7)

        manager.deleteEntity(entity6)
        manager.find<IManagedEntity>(entity1)
        manager.find<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity3)
        manager.find<IManagedEntity>(entity4)
        manager.find<IManagedEntity>(entity7)
        manager.find<IManagedEntity>(entity8)

        var pass = false
        try {
            manager.find<IManagedEntity>(entity6)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        assertTrue(pass, "Failed to delete entity")
    }

}
