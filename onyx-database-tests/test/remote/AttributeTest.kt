package remote

import category.RemoteServerTests
import com.onyx.exception.OnyxException
import com.onyx.exception.InitializationException
import com.onyx.application.impl.DatabaseServer
import com.onyx.persistence.IManagedEntity
import entities.AllAttributeEntity
import entities.InheritedAttributeEntity
import entities.SimpleEntity
import org.junit.*
import org.junit.experimental.categories.Category
import org.junit.runners.MethodSorters
import remote.base.RemoteBaseTest

import java.io.IOException
import java.util.Arrays
import java.util.Date

import junit.framework.Assert.assertTrue
import org.junit.Assert.fail

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category(RemoteServerTests::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AttributeTest : RemoteBaseTest() {

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

    /**
     * Tests all possible populated values in order to test a round trip serialization
     *
     * @throws OnyxException
     * @throws InitializationException
     */
    @Test
    @Throws(OnyxException::class)
    fun testPopulatedEntity() {
        val entity = AllAttributeEntity()

        entity.id = "A"
        entity.longValue = 4L
        entity.longPrimitive = 3L
        entity.stringValue = "STring key"
        entity.dateValue = Date(1483736263743L)
        entity.doublePrimitive = 342.23
        entity.doubleValue = 232.2
        entity.booleanPrimitive = true
        entity.booleanValue = false

        manager!!.saveEntity<IManagedEntity>(entity)

        var entity2 = AllAttributeEntity()
        entity2.id = "A"
        try {
            entity2 = manager!!.find(entity2)
        } catch (e: OnyxException) {
            e.printStackTrace()
        }

        Assert.assertEquals("A", entity2.id)
        Assert.assertEquals(java.lang.Long.valueOf(4L), entity2.longValue)
        Assert.assertEquals(3L, entity2.longPrimitive)
        Assert.assertEquals("STring key", entity2.stringValue)
        Assert.assertEquals(entity.dateValue, entity2.dateValue)
        assert(342.23 == entity2.doublePrimitive)
        Assert.assertEquals(java.lang.Double.valueOf(232.2), entity2.doubleValue)
        Assert.assertEquals(true, entity2.booleanPrimitive)
        Assert.assertEquals(java.lang.Boolean.valueOf(false), entity2.booleanValue)

    }

    /**
     * Test null values for properties that are mutable
     */
    @Test
    fun testNullPopulatedEntity() {
        val entity = AllAttributeEntity()

        entity.id = "B"

        try {
            manager!!.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        val entity2 = AllAttributeEntity()
        entity2.id = "B"
        try {
            manager!!.find<IManagedEntity>(entity2)
        } catch (e: OnyxException) {
            fail(e.message)
        }

        Assert.assertEquals("B", entity2.id)
        Assert.assertNull(entity2.longValue)
        Assert.assertEquals(0, entity2.longPrimitive)
        Assert.assertNull(entity2.stringValue)
        Assert.assertNull(entity2.dateValue)
        assert(0.0 == entity2.doublePrimitive)
        Assert.assertNull(entity2.doubleValue)
        Assert.assertEquals(false, entity2.booleanPrimitive)
        Assert.assertNull(entity2.booleanValue)

    }

    /**
     * Tests that inherited properties are persisted and hydrated properly
     *
     * @throws OnyxException
     */
    @Test
    @Throws(OnyxException::class)
    fun testInheritedPopulatedEntity() {

        val entity = InheritedAttributeEntity()

        entity.id = "C"
        entity.longValue = 4L
        entity.longPrimitive = 3L
        entity.stringValue = "STring key"
        entity.dateValue = Date(343535)
        entity.doublePrimitive = 342.23
        entity.doubleValue = 232.2
        entity.booleanPrimitive = true
        entity.booleanValue = false

        try {
            manager!!.saveEntity<IManagedEntity>(entity)
        } catch (e: OnyxException) {
            fail(e.message)
        }


        var entity2 = InheritedAttributeEntity()
        entity2.id = "C"

        assertTrue(manager!!.exists(entity2))
        try {
            entity2 = manager!!.find(entity2)
        } catch (e: OnyxException) {
            fail("Error finding entity")
        }

        Assert.assertEquals("C", entity2.id)
        Assert.assertEquals(java.lang.Long.valueOf(4L), entity2.longValue)
        Assert.assertEquals(3L, entity2.longPrimitive)
        Assert.assertEquals("STring key", entity2.stringValue)
        Assert.assertEquals(entity.dateValue, entity2.dateValue)
        assert(342.23 == entity2.doublePrimitive)
        Assert.assertEquals(java.lang.Double.valueOf(232.2), entity2.doubleValue)
        Assert.assertEquals(true, entity2.booleanPrimitive)
        Assert.assertEquals(java.lang.Boolean.valueOf(false), entity2.booleanValue)

    }


    @Test
    @Throws(OnyxException::class)
    fun simpleMultipleTest() {
        val simpleEntity2 = SimpleEntity()
        simpleEntity2.simpleId = "2"
        simpleEntity2.name = "Name2"

        val simpleEntity3 = SimpleEntity()
        simpleEntity3.simpleId = "3"
        simpleEntity3.name = "Name3"

        manager!!.saveEntities(Arrays.asList(simpleEntity2, simpleEntity3))

        //@todo: retreive using manager.list()

    }

    @Test
    @Throws(OnyxException::class)
    fun testFindById() {
        //Save entity
        val entity = SimpleEntity()
        entity.simpleId = "1"
        entity.name = "Chris"
        manager!!.saveEntity<IManagedEntity>(entity)
        //Retreive entity using findById method
        val savedEntity = manager!!.findById<IManagedEntity>(entity.javaClass, "1") as SimpleEntity?

        //Assert
        Assert.assertTrue(entity.simpleId == savedEntity!!.simpleId)
        Assert.assertTrue(entity.name == savedEntity.name)
    }

    companion object {


        @BeforeClass
        @JvmStatic
        @Throws(InterruptedException::class)
        fun beforeClass() {
            RemoteBaseTest.deleteDatabase()
            RemoteBaseTest.databaseServer = DatabaseServer("C:/Sandbox/Onyx/Tests/server.oxd")
            RemoteBaseTest.databaseServer!!.port = 8080
            RemoteBaseTest.databaseServer!!.start()
            Thread.sleep(500)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            //databaseServer.stop();
        }
    }

}
