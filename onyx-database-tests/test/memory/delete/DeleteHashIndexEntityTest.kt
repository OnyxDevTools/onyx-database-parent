package memory.delete

import category.InMemoryDatabaseTests
import com.onyx.exception.OnyxException
import com.onyx.exception.NoResultsException
import com.onyx.persistence.IManagedEntity
import entities.AllAttributeEntity
import entities.identifiers.IntegerIdentifierEntity
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

import java.io.IOException
import java.util.Date

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category(InMemoryDatabaseTests::class)
class DeleteHashIndexEntityTest : memory.base.BaseTest() {
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
    fun testDeleteEntity() {
        val entity = AllAttributeEntity()
        entity.id = "dc5cholqdu5vha5bb6ned8ASDF"
        entity.booleanValue = false
        entity.doubleValue = 234.3
        entity.dateValue = Date()
        entity.stringValue = "Hiya"
        save(entity)

        /*manager.deleteEntity(entity);

        boolean pass = false;
        try {
            manager.find(entity);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException)
            {
                pass = true;
            }
        }
        Assert.assertTrue(pass);*/
    }

    @Test
    fun testDeleteHashAndReSave() {
        var entity = IntegerIdentifierEntity()
        entity.identifier = 1
        save(entity)
        find(entity)
        delete(entity)
        var pass = false
        try {
            manager.find<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        Assert.assertTrue(pass)

        entity = IntegerIdentifierEntity()
        entity.identifier = 1
        save(entity)

        try {
            manager.find<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = false
            }
        }

        Assert.assertTrue(pass)
    }

    @Test
    fun testDeleteRoot() {
        val entity1 = IntegerIdentifierEntity()
        entity1.identifier = 1
        save(entity1)

        val entity2 = IntegerIdentifierEntity()
        entity2.identifier = 2
        save(entity2)

        val entity3 = IntegerIdentifierEntity()
        entity3.identifier = -1
        save(entity3)

        val entity4 = IntegerIdentifierEntity()
        entity4.identifier = 8
        save(entity4)

        val entity5 = IntegerIdentifierEntity()
        entity5.identifier = 6
        save(entity5)

        val entity6 = IntegerIdentifierEntity()
        entity6.identifier = 10
        save(entity6)

        delete(entity1)
        find(entity2)
        find(entity3)
        find(entity4)
        find(entity5)
        find(entity6)

        var pass = false
        try {
            manager.find<IManagedEntity>(entity1)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        Assert.assertTrue(pass)
    }

    @Test
    fun testDeleteParent() {
        val entity1 = IntegerIdentifierEntity()
        entity1.identifier = 1
        save(entity1)

        val entity2 = IntegerIdentifierEntity()
        entity2.identifier = 2
        save(entity2)

        val entity3 = IntegerIdentifierEntity()
        entity3.identifier = -1
        save(entity3)

        val entity4 = IntegerIdentifierEntity()
        entity4.identifier = 8
        save(entity4)

        val entity5 = IntegerIdentifierEntity()
        entity5.identifier = 6
        save(entity5)

        val entity6 = IntegerIdentifierEntity()
        entity6.identifier = 10
        save(entity6)

        delete(entity5)
        find(entity1)
        find(entity2)
        find(entity3)
        find(entity4)
        find(entity6)

        var pass = false
        try {
            manager.find<IManagedEntity>(entity5)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        Assert.assertTrue(pass)
    }

    @Test
    fun testDeleteLeft() {
        val entity1 = IntegerIdentifierEntity()
        entity1.identifier = 1
        save(entity1)

        val entity2 = IntegerIdentifierEntity()
        entity2.identifier = 2
        save(entity2)

        val entity3 = IntegerIdentifierEntity()
        entity3.identifier = 4
        save(entity3)

        val entity7 = IntegerIdentifierEntity()
        entity7.identifier = 3
        save(entity7)

        val entity4 = IntegerIdentifierEntity()
        entity4.identifier = 8
        save(entity4)

        val entity5 = IntegerIdentifierEntity()
        entity5.identifier = 6
        save(entity5)

        val entity6 = IntegerIdentifierEntity()
        entity6.identifier = 10
        save(entity6)


        delete(entity3)
        find(entity1)
        find(entity2)
        find(entity4)
        find(entity5)
        find(entity6)
        find(entity7)

        var pass = false
        try {
            manager.find<IManagedEntity>(entity3)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        Assert.assertTrue(pass)
    }

    @Test
    fun testDeleteRight() {
        val entity1 = IntegerIdentifierEntity()
        entity1.identifier = 1
        save(entity1)

        val entity2 = IntegerIdentifierEntity()
        entity2.identifier = 2
        save(entity2)


        val entity4 = IntegerIdentifierEntity()
        entity4.identifier = 8
        save(entity4)

        val entity5 = IntegerIdentifierEntity()
        entity5.identifier = 6
        save(entity5)

        val entity6 = IntegerIdentifierEntity()
        entity6.identifier = 10
        save(entity6)

        val entity8 = IntegerIdentifierEntity()
        entity8.identifier = 11
        save(entity8)


        val entity3 = IntegerIdentifierEntity()
        entity3.identifier = 4
        save(entity3)

        val entity7 = IntegerIdentifierEntity()
        entity7.identifier = 3
        save(entity7)

        delete(entity6)
        find(entity1)
        find(entity2)
        find(entity3)
        find(entity4)
        find(entity7)
        find(entity8)

        var pass = false
        try {
            manager.find<IManagedEntity>(entity6)
        } catch (e: OnyxException) {
            if (e is NoResultsException) {
                pass = true
            }
        }

        Assert.assertTrue(pass)
    }

}
