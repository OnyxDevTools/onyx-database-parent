package web.delete

import category.WebServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import web.base.BaseTest

import java.io.IOException
import java.util.ArrayList

import org.junit.Assert.assertFalse
import org.junit.Assert.fail

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category(WebServerTests::class)
class DeleteSequenceIndexEntityTest : BaseTest() {
    @Before
    @Throws(OnyxException::class)
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
    fun testAddDeleteSequence() {
        val entity = ImmutableSequenceIdentifierEntityForDelete()
        entity.correlation = 1
        save(entity)

        Assert.assertTrue(entity.identifier > 0)
        delete(entity)

        assertFalse(manager.exists(entity))

        var pass = false
        try {
            manager.find<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        Assert.assertTrue(pass)
    }

    @Test
    fun testSequenceBatchDelete() {
        val entity = ImmutableSequenceIdentifierEntityForDelete()
        entity.correlation = 1
        save(entity)
        val id1 = entity.identifier

        val entity2 = ImmutableSequenceIdentifierEntityForDelete()
        entity2.correlation = 1
        save(entity2)
        val id2 = entity2.identifier

        val entity3 = ImmutableSequenceIdentifierEntityForDelete()
        entity3.correlation = 1
        save(entity3)
        val id3 = entity3.identifier

        val entity4 = ImmutableSequenceIdentifierEntityForDelete()
        entity4.correlation = 1
        save(entity4)
        val id4 = entity4.identifier

        val entity5 = ImmutableSequenceIdentifierEntityForDelete()
        entity5.correlation = 5
        save(entity5)
        val id5 = entity.identifier

        val entity6 = ImmutableSequenceIdentifierEntityForDelete()
        entity.correlation = 6
        save(entity6)
        val id6 = entity6.identifier

        Assert.assertTrue(entity.identifier > 0)
        Assert.assertTrue(entity6.identifier > 0)

        val entitiesToDelete = ArrayList<IManagedEntity>()
        //entitiesToDelete.add(entity);
        entitiesToDelete.add(entity2)
        entitiesToDelete.add(entity3)
        entitiesToDelete.add(entity4)
        entitiesToDelete.add(entity5)
        entitiesToDelete.add(entity6)


        try {
            manager.deleteEntities(entitiesToDelete)
        } catch (e: OnyxException) {
            fail("Failure to execute delete batch")
        }

        for (deletedEntity in entitiesToDelete) {
            var pass = false
            try {
                manager.find<IManagedEntity>(deletedEntity as IManagedEntity)
            } catch (e: OnyxException) {
                if (e is NoResultsException) {
                    pass = true
                }
            }

            Assert.assertTrue(pass)
        }

        val entity7 = ImmutableSequenceIdentifierEntityForDelete()
        entity.correlation = 7
        save(entity7)
        val id7 = entity7.identifier

        find(entity7)
        find(entity)
    }

    @Test
    fun testSequenceSkip() {
        val entity = ImmutableSequenceIdentifierEntityForDelete()
        entity.correlation = 1
        save(entity)
        val id1 = entity.identifier

        val entity2 = ImmutableSequenceIdentifierEntityForDelete()
        entity2.correlation = 1
        entity2.identifier = id1 + 100
        save(entity2)
        val id2 = entity2.identifier

        val entity3 = ImmutableSequenceIdentifierEntityForDelete()
        entity3.correlation = 1
        save(entity3)
        val id3 = entity3.identifier


        Assert.assertTrue(entity.identifier > 0)
        Assert.assertTrue(entity2.identifier > entity.identifier)
        //        Assert.assertTrue(entity3.identifier > entity2.identifier);
        Assert.assertTrue(entity2.identifier - entity.identifier == 100L)

        delete(entity2)

        find(entity)
        find(entity3)

        var pass = false
        try {
            manager.find<IManagedEntity>(entity2)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        Assert.assertTrue(pass)

    }
}
