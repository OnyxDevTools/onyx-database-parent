package embedded.save

import category.EmbeddedDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.IManagedEntity
import embedded.base.BaseTest
import entities.identifiers.DateIdentifierEntity
import entities.identifiers.IntegerIdentifierEntity
import entities.identifiers.MutableIntegerIdentifierEntity
import entities.identifiers.StringIdentifierEntity
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters

import java.io.IOException
import java.util.Date

import junit.framework.Assert.assertEquals
import org.junit.Assert.fail

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(EmbeddedDatabaseTests::class)
class SaveHashIdentifierTest : BaseTest() {

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
    fun aTestSaveStringHashIndex() {
        val entity = StringIdentifierEntity()
        entity.identifier = "ABSCStringID1"
        entity.correlation = 1
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = StringIdentifierEntity()
        entity2.identifier = "ABSCStringID1"
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("String Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 1)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun bTestUpdateStringHashIndex2() {
        var entity = StringIdentifierEntity()
        entity.identifier = "ABSCStringID1"
        entity.correlation = 2
        try {
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        assertEquals(entity.correlation, 1)

        val entity2 = StringIdentifierEntity()
        entity2.identifier = "ABSCStringID1"
        entity2.correlation = 3
        try {
            manager.saveEntity<IManagedEntity>(entity2)
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("String Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 3)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun cTestSaveStringHashIndex2() {
        val entity = StringIdentifierEntity()
        entity.identifier = "ASDVF*32234"
        entity.correlation = 2
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = StringIdentifierEntity()
        entity2.identifier = "ASDVF*32234"
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("String Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 2)
        assertEquals(entity.correlation, entity2.correlation)
    }


    @Test
    fun dTestSaveIntegerHashIndex() {
        val entity = IntegerIdentifierEntity()
        entity.identifier = 2
        entity.correlation = 5
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = IntegerIdentifierEntity()
        entity2.identifier = 2
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 5)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun eTestSaveIntegerHashIndex() {
        val entity = IntegerIdentifierEntity()
        entity.identifier = 4
        entity.correlation = 6
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = IntegerIdentifierEntity()
        entity2.identifier = 4
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 6)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun fTestSaveIntegerHashIndex() {
        val entity = IntegerIdentifierEntity()
        entity.identifier = 1
        entity.correlation = 7
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = IntegerIdentifierEntity()
        entity2.identifier = 1
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 7)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun gTestUpdateIntegerHashIndex() {
        var entity = IntegerIdentifierEntity()
        entity.identifier = 4
        entity.correlation = 2
        try {
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        assertEquals(entity.correlation, 6)
        val entity2 = IntegerIdentifierEntity()
        entity2.identifier = 4
        entity2.correlation = 22
        try {
            manager.saveEntity<IManagedEntity>(entity2)
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 22)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun hTestSaveIntegerHashIndex() {
        val entity = MutableIntegerIdentifierEntity()
        entity.identifier = 2
        entity.correlation = 5
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = MutableIntegerIdentifierEntity()
        entity2.identifier = 2
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier!!, entity2.identifier!!)
        assertEquals(entity.correlation, 5)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun iTestSaveIntegerHashIndex() {
        val entity = MutableIntegerIdentifierEntity()
        entity.identifier = 4
        entity.correlation = 6
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = MutableIntegerIdentifierEntity()
        entity2.identifier = 4
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier!!, entity2.identifier!!)
        assertEquals(entity.correlation, 6)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun jTestSaveIntegerHashIndex() {
        val entity = MutableIntegerIdentifierEntity()
        entity.identifier = 1
        entity.correlation = 7
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = MutableIntegerIdentifierEntity()
        entity2.identifier = 1
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier as Int, entity2.identifier as Int)
        assertEquals(entity.correlation, 7)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun kTestUpdateIntegerHashIndex() {
        var entity = MutableIntegerIdentifierEntity()
        entity.identifier = 4
        entity.correlation = 2
        try {
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        assertEquals(entity.correlation, 6)
        val entity2 = MutableIntegerIdentifierEntity()
        entity2.identifier = 4
        entity2.correlation = 22
        try {
            manager.saveEntity<IManagedEntity>(entity2)
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier!!, entity2.identifier!!)
        assertEquals(entity.correlation, 22)
        assertEquals(entity.correlation, entity2.correlation)
    }


    @Test
    fun lTestSaveIntegerHashIndex() {
        val entity = DateIdentifierEntity()
        entity.identifier = Date(1483736355234L)
        entity.correlation = 5
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = DateIdentifierEntity()
        entity2.identifier = Date(entity.identifier!!.time)
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 5)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun mTestSaveIntegerHashIndex() {
        val entity = DateIdentifierEntity()
        entity.identifier = Date(823482)
        entity.correlation = 6
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = DateIdentifierEntity()
        entity2.identifier = Date(entity.identifier!!.time)
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 6)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun nTestSaveIntegerHashIndex() {
        val entity = DateIdentifierEntity()
        entity.identifier = Date(23827)
        entity.correlation = 7
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = DateIdentifierEntity()
        entity2.identifier = Date(entity.identifier!!.time)
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 7)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun oTestUpdateIntegerHashIndex() {
        var entity = DateIdentifierEntity()
        entity.identifier = Date(823482)
        entity.correlation = 2
        try {
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        assertEquals(entity.correlation, 6)
        val entity2 = DateIdentifierEntity()
        entity2.identifier = Date(823482)
        entity2.correlation = 22
        try {
            manager.saveEntity<IManagedEntity>(entity2)
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier)
        assertEquals(entity.correlation, 22)
        assertEquals(entity.correlation, entity2.correlation)
    }

}
