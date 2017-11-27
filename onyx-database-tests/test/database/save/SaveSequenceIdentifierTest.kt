package database.save

import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory
import com.onyx.persistence.factory.impl.WebPersistenceManagerFactory
import database.base.DatabaseBaseTest
import entities.identifiers.ImmutableIntSequenceIdentifierEntity
import entities.identifiers.ImmutableSequenceIdentifierEntity
import entities.identifiers.MutableIntSequenceIdentifierEntity
import entities.identifiers.MutableSequenceIdentifierEntity
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
@Suppress("MemberVisibilityCanPrivate")
class SaveSequenceIdentifierTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun aTestSaveMutableSequenceIdentifierEntity() {

        val entity = MutableSequenceIdentifierEntity()
        entity.correlation = 5
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = MutableSequenceIdentifierEntity()
        entity2.identifier = 1L
        entity2 = manager.find(entity2)

        assertTrue(entity.identifier!! > 0L, "Sequence Identifier should be greater than 0")
        assertEquals(5, entity.correlation, "Correlation does not match expected")
        assertEquals(entity.correlation, entity2.correlation, "Correlation does not match expected")
    }

    @Test
    fun bTestSaveImmutableSequenceIdentifierEntity() {

        val entity = ImmutableSequenceIdentifierEntity()
        entity.correlation = 6
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 1L
        entity2 = manager.find(entity2)

        assertTrue(entity.identifier > 0, "Sequence Identifier should be greater than 0")
        assertEquals(6, entity.correlation, "Correlation does not match expected")
        assertEquals(entity.correlation, entity2.correlation, "Correlation does not match expected")
    }

    @Test
    fun cTestSaveMutableSequenceIdentifierEntityNext() {

        val entity = MutableSequenceIdentifierEntity()
        entity.correlation = 7

        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = MutableSequenceIdentifierEntity()
        entity2.identifier = 2L
        entity2 = manager.find(entity2)

        assertTrue(entity.identifier!! > 1L, "Sequence Identifier should be greater than 1")
        assertEquals(7, entity.correlation, "Correlation does not match expected")
        assertEquals(entity.correlation, entity2.correlation, "Correlation does not match expected")
    }

    @Test
    fun dTestSaveImmutableSequenceIdentifierEntityNext() {

        val entity = ImmutableSequenceIdentifierEntity()
        entity.correlation = 9
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 2L
        entity2 = manager.find(entity2)

        assertTrue(entity.identifier > 1, "Sequence Identifier should be greater than 1")
        assertEquals(9, entity.correlation, "Correlation does not match expected")
        assertEquals(entity.correlation, entity2.correlation, "Correlation does not match expected")
    }

    @Test
    fun eTestSaveMutableSequenceIdentifierEntityUserDefined() {

        val entity = MutableSequenceIdentifierEntity()
        entity.correlation = 8
        entity.identifier = 3L
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = MutableSequenceIdentifierEntity()
        entity2.identifier = 3L
        entity2 = manager.find(entity2)

        assertEquals(3L,entity.identifier!!, "Sequence Identifier should be greater than 1")
        assertEquals(8, entity.correlation, "Correlation does not match expected")
        assertEquals(entity.correlation, entity2.correlation, "Correlation does not match expected")
    }

    @Test
    fun fTestSaveImmutableSequenceIdentifierEntityUserDefined() {

        val entity = ImmutableSequenceIdentifierEntity()
        entity.correlation = 9
        entity.identifier = 3
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 3L
        entity2 = manager.find(entity2)

        assertEquals(3L, entity.identifier, "Sequence Identifier should be greater equal to 3")
        assertEquals(9, entity.correlation, "Correlation does not match expected")
        assertEquals(entity.correlation, entity2.correlation, "Correlation does not match expected")
    }

    @Test
    fun gTestSaveImmutableSequenceIdentifierEntityUserDefinedSkip() {

        var entity = ImmutableSequenceIdentifierEntity()
        entity.correlation = 11
        entity.identifier = 5
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 5L
        entity2 = manager.find(entity2)

        assertEquals(5L, entity2.identifier, "Sequence Identifier should be greater equal to 5")
        assertEquals(11, entity.correlation, "Correlation does not match expected")
        assertEquals(entity.correlation, entity2.correlation, "Correlation does not match expected")

        entity = ImmutableSequenceIdentifierEntity()
        entity.correlation = 12
        manager.saveEntity<IManagedEntity>(entity)

        entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 6L
        entity2 = manager.find(entity2)

        assertEquals(6L, entity2.identifier, "Sequence Identifier should be equal to 6")
        assertEquals(12, entity.correlation, "Correlation does not match expected")
        assertEquals(entity.correlation, entity2.correlation, "Correlation does not match expected")

    }

    @Test
    fun hTestSaveMutableIntSequenceIdentifierEntity() {

        val entity = MutableIntSequenceIdentifierEntity()
        entity.correlation = 5
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = MutableIntSequenceIdentifierEntity()
        entity2.identifier = 1
        entity2 = manager.find(entity2)

        assertTrue(entity.identifier!! > 0L, "Sequence Identifier should be greater than 0")
        assertEquals(5, entity.correlation, "Correlation does not match expected")
        assertEquals(entity.correlation, entity2.correlation, "Correlation does not match expected")
    }

    @Test
    fun iTestSaveImmutableIntSequenceIdentifierEntity() {

        val entity = ImmutableIntSequenceIdentifierEntity()
        entity.correlation = 6
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = ImmutableIntSequenceIdentifierEntity()
        entity2.identifier = 1
        entity2 = manager.find(entity2)

        assertTrue(entity.identifier > 0, "Sequence Identifier should be greater than 0")
        assertEquals(6, entity.correlation, "Correlation does not match expected")
        assertEquals(entity.correlation, entity2.correlation, "Correlation does not match expected")
    }

    @Test
    fun jTestFindLastItem() {

        var entity = ImmutableSequenceIdentifierEntity()
        entity.identifier = 6
        entity = manager.find(entity)

        assertEquals(6, entity.identifier, "Identifier is not assigned from explicit setting")
        assertEquals(12, entity.correlation, "Correlation does not match expected")
    }

    @Test
    fun kTestFindFirstItem() {

        var entity = ImmutableSequenceIdentifierEntity()
        entity.identifier = 1
        entity = manager.find(entity)

        assertEquals(1, entity.identifier)
        assertEquals(6, entity.correlation)
    }

    @Test
    fun lTestUpdateImmutableSequenceIdentifierEntity() {

        var entity = ImmutableSequenceIdentifierEntity()
        // This should be 9
        entity.correlation = 1
        entity.identifier = 3
        entity = manager.find(entity)

        assertEquals(9, entity.correlation, "Correlation does not match expected")

        val entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 3L
        entity2.correlation = 88
        manager.saveEntity<IManagedEntity>(entity2)

        entity = manager.find(entity)

        assertEquals(88, entity.correlation, "Correlation does not match expected")
    }

    @Test
    fun mTestUpdateMutableSequenceIdentifierEntity() {

        var entity = MutableSequenceIdentifierEntity()
        // This should be 9
        entity.correlation = 1
        entity.identifier = 3L
        entity = manager.find(entity)

        assertEquals(8, entity.correlation)

        val entity2 = MutableSequenceIdentifierEntity()
        entity2.identifier = 3L
        entity2.correlation = 87
        manager.saveEntity<IManagedEntity>(entity2)

        entity = manager.find(entity)

        assertEquals(87, entity.correlation)
    }

    @Test
    fun nTestInsertInTheMiddle() {

        val entity = MutableSequenceIdentifierEntity()
        entity.correlation = 1
        entity.identifier = 100L
        manager.saveEntity<IManagedEntity>(entity)

        manager.find<IManagedEntity>(entity)

        assertEquals(1, entity.correlation)

        val entity2 = MutableSequenceIdentifierEntity()
        entity2.identifier = 90L
        entity2.correlation = 87
        manager.saveEntity<IManagedEntity>(entity2)
        manager.find<IManagedEntity>(entity)

        manager.find<IManagedEntity>(entity2)
        assertEquals(87, entity2.correlation, "Correlation does not match expected")
    }

    companion object {
        /**
         * Overridden so that the Cache manager factory executes for these tests.  CacheManagerFactory does not retain
         * state so most of the tests to not apply.
         */
        @JvmStatic
        @Parameterized.Parameters
        fun persistenceManagersToTest(): Collection<KClass<*>> = arrayListOf(EmbeddedPersistenceManagerFactory::class, WebPersistenceManagerFactory::class, RemotePersistenceManagerFactory::class)
    }
}
