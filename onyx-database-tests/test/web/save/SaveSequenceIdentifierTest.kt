package web.save

import category.WebServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.persistence.IManagedEntity
import entities.identifiers.ImmutableIntSequenceIdentifierEntity
import entities.identifiers.ImmutableSequenceIdentifierEntity
import entities.identifiers.MutableIntSequenceIdentifierEntity
import entities.identifiers.MutableSequenceIdentifierEntity
import junit.framework.Assert
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters
import web.base.BaseTest

import java.io.IOException

import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(WebServerTests::class)
class SaveSequenceIdentifierTest : BaseTest() {

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
    fun aTestSaveMutableSequenceIdentifierEntity() {

        val entity = MutableSequenceIdentifierEntity()
        entity.correlation = 5

        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = MutableSequenceIdentifierEntity()
        entity2.identifier = 1L
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertTrue("Sequence Identifier should be greater than 0", entity.identifier!! > 0L)
        assertEquals(entity.correlation, 5)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun bTestSaveImmutableSequenceIdentifierEntity() {

        val entity = ImmutableSequenceIdentifierEntity()
        entity.correlation = 6
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 1L
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertTrue("Sequence Identifier should be greater than 0", entity.identifier!! > 0)
        assertEquals(entity.correlation, 6)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun cTestSaveMutableSequenceIdentifierEntityNext() {

        val entity = MutableSequenceIdentifierEntity()
        entity.correlation = 7

        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = MutableSequenceIdentifierEntity()
        entity2.identifier = 2L
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertTrue("Sequence Identifier should be greater than 1", entity.identifier!! > 1)
        assertEquals(entity.correlation, 7)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun dTestSaveImmutableSequenceIdentifierEntityNext() {

        val entity = ImmutableSequenceIdentifierEntity()
        entity.correlation = 9
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 2L
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertTrue("Sequence Identifier should be greater than 1", entity.identifier > 1L)
        assertEquals(entity.correlation, 9)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun eTestSaveMutableSequenceIdentifierEntityUserDefined() {

        val entity = MutableSequenceIdentifierEntity()
        entity.correlation = 8
        entity.identifier = 3L
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = MutableSequenceIdentifierEntity()
        entity2.identifier = 3L
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertTrue("Sequence Identifier should be greater than 1", entity.identifier!! === 3L)
        assertEquals(entity.correlation, 8)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun fTestSaveImmutableSequenceIdentifierEntityUserDefined() {

        val entity = ImmutableSequenceIdentifierEntity()
        entity.correlation = 9
        entity.identifier = 3
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 3L
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertTrue("Sequence Identifier should be greater equal to 3", entity.identifier == 3L)
        assertEquals(entity.correlation, 9)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun gTestSaveImmutableSequenceIdentifierEntityUserDefinedSkip() {

        var entity = ImmutableSequenceIdentifierEntity()
        entity.correlation = 11
        entity.identifier = 5
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 5L
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertTrue("Sequence Identifier should be greater equal to 5", entity2.identifier == 5L)
        assertEquals(entity.correlation, 11)
        assertEquals(entity.correlation, entity2.correlation)

        entity = ImmutableSequenceIdentifierEntity()
        entity.correlation = 12
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 6L
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertTrue("Sequence Identifier should be equal to 6", entity2.identifier == 6L)
        assertEquals(entity.correlation, 12)
        assertEquals(entity.correlation, entity2.correlation)

    }

    @Test
    fun hTestSaveMutableIntSequenceIdentifierEntity() {

        val entity = MutableIntSequenceIdentifierEntity()
        entity.correlation = 5

        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = MutableIntSequenceIdentifierEntity()
        entity2.identifier = 1
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertTrue("Sequence Identifier should be greater than 0", entity.identifier!! > 0L)
        assertEquals(entity.correlation, 5)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun iTestSaveImmutableIntSequenceIdentifierEntity() {

        val entity = ImmutableIntSequenceIdentifierEntity()
        entity.correlation = 6
        try {
            manager.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        var entity2 = ImmutableIntSequenceIdentifierEntity()
        entity2.identifier = 1
        try {
            entity2 = manager.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        assertTrue("Sequence Identifier should be greater than 0", entity.identifier > 0)
        assertEquals(entity.correlation, 6)
        assertEquals(entity.correlation, entity2.correlation)
    }

    @Test
    fun jTestFindLastItem() {

        var entity = ImmutableSequenceIdentifierEntity()
        entity.identifier = 6

        try {
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            Assert.fail("Failure to find entity")
        }

        assertEquals(entity.identifier, 6)
        assertEquals(entity.correlation, 12)
    }

    @Test
    fun kTestFindFirstItem() {

        var entity = ImmutableSequenceIdentifierEntity()
        entity.identifier = 1

        try {
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            Assert.fail("Failure to find entity")
        }

        assertEquals(entity.identifier, 1)
        assertEquals(entity.correlation, 6)
    }

    @Test
    fun lTestUpdateImmutableSequenceIdentifierEntity() {

        var entity = ImmutableSequenceIdentifierEntity()
        // This should be 9
        entity.correlation = 1
        entity.identifier = 3

        try {
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        assertEquals(entity.correlation, 9)

        val entity2 = ImmutableSequenceIdentifierEntity()
        entity2.identifier = 3L
        entity2.correlation = 88
        try {
            manager.saveEntity<IManagedEntity>(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        try {
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        assertEquals(entity.correlation, 88)
    }

    @Test
    fun mTestUpdateMutableSequenceIdentifierEntity() {

        var entity = MutableSequenceIdentifierEntity()
        // This should be 9
        entity.correlation = 1
        entity.identifier = 3L

        try {
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        assertEquals(entity.correlation, 8)

        val entity2 = MutableSequenceIdentifierEntity()
        entity2.identifier = 3L
        entity2.correlation = 87
        try {
            manager.saveEntity<IManagedEntity>(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        try {
            entity = manager.find(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        assertEquals(entity.correlation, 87)
    }

    @Test
    fun nTestInsertInTheMiddle() {

        val entity = MutableSequenceIdentifierEntity()
        entity.correlation = 1
        entity.identifier = 100L

        save(entity)

        try {
            manager.find<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        assertEquals(entity.correlation, 1)

        val entity2 = MutableSequenceIdentifierEntity()
        entity2.identifier = 90L
        entity2.correlation = 87
        try {
            manager.saveEntity<IManagedEntity>(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        try {
            manager.find<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }


        try {
            manager.find<IManagedEntity>(entity2)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        assertEquals(entity2.correlation, 87)

    }
}
