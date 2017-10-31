package database.save

import com.onyx.persistence.IManagedEntity
import database.base.DatabaseBaseTest
import entities.identifiers.DateIdentifierEntity
import entities.identifiers.IntegerIdentifierEntity
import entities.identifiers.MutableIntegerIdentifierEntity
import entities.identifiers.StringIdentifierEntity
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import java.util.*
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized::class)
@Suppress("MemberVisibilityCanPrivate")
class SaveStandardIdentifierTest(override var factoryClass: KClass<*>) : DatabaseBaseTest(factoryClass) {

    @Test
    fun aTestSaveStringHashIndex() {
        val entity = StringIdentifierEntity()
        entity.identifier = "ABSCStringID1"
        entity.correlation = 1
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = StringIdentifierEntity()
        entity2.identifier = "ABSCStringID1"
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier, entity2.identifier, "String Identifier Entity Not saved")
        assertEquals(1, entity.correlation, "Correlation not mapped")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun bTestUpdateStringHashIndex2() {
        this.aTestSaveStringHashIndex()

        var entity = StringIdentifierEntity()
        entity.identifier = "ABSCStringID1"
        entity.correlation = 2
        entity = manager.find(entity)

        assertEquals(1, entity.correlation, "Correlation does not match")

        val entity2 = StringIdentifierEntity()
        entity2.identifier = "ABSCStringID1"
        entity2.correlation = 3
        manager.saveEntity<IManagedEntity>(entity2)
        entity = manager.find(entity)

        assertEquals(entity.identifier, entity2.identifier, "String Identifier Entity Not saved")
        assertEquals(3, entity.correlation, "Correlation not mapped")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun cTestSaveStringHashIndex2() {
        val entity = StringIdentifierEntity()
        entity.identifier = "ASDVF*32234"
        entity.correlation = 2
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = StringIdentifierEntity()
        entity2.identifier = "ASDVF*32234"
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier, entity2.identifier, "String Identifier Entity Not saved")
        assertEquals(2, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }


    @Test
    fun dTestSaveIntegerHashIndex() {
        val entity = IntegerIdentifierEntity()
        entity.identifier = 2
        entity.correlation = 5
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = IntegerIdentifierEntity()
        entity2.identifier = 2
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier, entity2.identifier, "Integer Identifier Entity Not saved")
        assertEquals(5, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun eTestSaveIntegerHashIndex() {
        val entity = IntegerIdentifierEntity()
        entity.identifier = 4
        entity.correlation = 6
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = IntegerIdentifierEntity()
        entity2.identifier = 4
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier, entity2.identifier, "Integer Identifier Entity Not saved")
        assertEquals(6, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun fTestSaveIntegerHashIndex() {
        val entity = IntegerIdentifierEntity()
        entity.identifier = 1
        entity.correlation = 7
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = IntegerIdentifierEntity()
        entity2.identifier = 1
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier, entity2.identifier, "Integer Identifier Entity Not saved")
        assertEquals(7, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun gTestUpdateIntegerHashIndex() {
        this.aTestSaveStringHashIndex()
        this.bTestUpdateStringHashIndex2()
        this.cTestSaveStringHashIndex2()
        this.dTestSaveIntegerHashIndex()
        this.eTestSaveIntegerHashIndex()
        this.fTestSaveIntegerHashIndex()

        var entity = IntegerIdentifierEntity()
        entity.identifier = 4
        entity.correlation = 2
        entity = manager.find(entity)

        assertEquals(6, entity.correlation)

        val entity2 = IntegerIdentifierEntity()
        entity2.identifier = 4
        entity2.correlation = 22
        manager.saveEntity<IManagedEntity>(entity2)
        entity = manager.find(entity)

        assertEquals(entity.identifier, entity2.identifier,"Integer Identifier Entity Not saved")
        assertEquals(22, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun hTestSaveIntegerHashIndex() {
        val entity = MutableIntegerIdentifierEntity()
        entity.identifier = 2
        entity.correlation = 5
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = MutableIntegerIdentifierEntity()
        entity2.identifier = 2
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier!!, entity2.identifier!!, "Integer Identifier Entity Not saved")
        assertEquals(entity.correlation, 5, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun iTestSaveIntegerHashIndex() {
        val entity = MutableIntegerIdentifierEntity()
        entity.identifier = 4
        entity.correlation = 6
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = MutableIntegerIdentifierEntity()
        entity2.identifier = 4
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier!!, entity2.identifier!!, "Integer Identifier Entity Not saved")
        assertEquals(6, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun jTestSaveIntegerHashIndex() {
        val entity = MutableIntegerIdentifierEntity()
        entity.identifier = 1
        entity.correlation = 7
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = MutableIntegerIdentifierEntity()
        entity2.identifier = 1
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier, entity2.identifier, "Integer Identifier Entity Not saved")
        assertEquals(7, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun kTestUpdateIntegerHashIndex() {
        this.aTestSaveStringHashIndex()
        this.bTestUpdateStringHashIndex2()
        this.cTestSaveStringHashIndex2()
        this.dTestSaveIntegerHashIndex()
        this.eTestSaveIntegerHashIndex()
        this.fTestSaveIntegerHashIndex()
        this.gTestUpdateIntegerHashIndex()
        this.hTestSaveIntegerHashIndex()
        this.iTestSaveIntegerHashIndex()

        var entity = MutableIntegerIdentifierEntity()
        entity.identifier = 4
        entity.correlation = 2
        entity = manager.find(entity)

        assertEquals(entity.correlation, 6)
        val entity2 = MutableIntegerIdentifierEntity()
        entity2.identifier = 4
        entity2.correlation = 22
        manager.saveEntity<IManagedEntity>(entity2)
        entity = manager.find(entity)

        assertEquals(entity.identifier!!, entity2.identifier!!, "Integer Identifier Entity Not saved")
        assertEquals(22, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }


    @Test
    fun lTestSaveIntegerHashIndex() {
        val entity = DateIdentifierEntity()
        entity.identifier = Date(1483736355234L)
        entity.correlation = 5
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = DateIdentifierEntity()
        entity2.identifier = Date(entity.identifier!!.time)
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier, entity2.identifier, "Integer Identifier Entity Not saved")
        assertEquals(5, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun mTestSaveIntegerHashIndex() {
        val entity = DateIdentifierEntity()
        entity.identifier = Date(823482)
        entity.correlation = 6
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = DateIdentifierEntity()
        entity2.identifier = Date(entity.identifier!!.time)
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier, entity2.identifier, "Integer Identifier Entity Not saved")
        assertEquals(6, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun nTestSaveIntegerHashIndex() {
        val entity = DateIdentifierEntity()
        entity.identifier = Date(23827)
        entity.correlation = 7
        manager.saveEntity<IManagedEntity>(entity)

        var entity2 = DateIdentifierEntity()
        entity2.identifier = Date(entity.identifier!!.time)
        entity2 = manager.find(entity2)

        assertEquals(entity.identifier, entity2.identifier, "Integer Identifier Entity Not saved")
        assertEquals(7, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }

    @Test
    fun oTestUpdateIntegerHashIndex() {
        this.aTestSaveStringHashIndex()
        this.bTestUpdateStringHashIndex2()
        this.cTestSaveStringHashIndex2()
        this.dTestSaveIntegerHashIndex()
        this.eTestSaveIntegerHashIndex()
        this.fTestSaveIntegerHashIndex()
        this.gTestUpdateIntegerHashIndex()
        this.hTestSaveIntegerHashIndex()
        this.iTestSaveIntegerHashIndex()
        this.jTestSaveIntegerHashIndex()
        this.kTestUpdateIntegerHashIndex()
        this.lTestSaveIntegerHashIndex()
        this.mTestSaveIntegerHashIndex()
        this.nTestSaveIntegerHashIndex()

        var entity = DateIdentifierEntity()
        entity.identifier = Date(823482)
        entity.correlation = 2
        entity = manager.find(entity)

        assertEquals(6, entity.correlation)
        val entity2 = DateIdentifierEntity()
        entity2.identifier = Date(823482)
        entity2.correlation = 22
        manager.saveEntity<IManagedEntity>(entity2)
        entity = manager.find(entity)

        assertEquals(entity.identifier, entity2.identifier, "Integer Identifier Entity Not saved")
        assertEquals(22, entity.correlation, "Correlation not matching")
        assertEquals(entity.correlation, entity2.correlation, "Correlation not matching")
    }
}
