package schemaupdate

import embedded.base.BaseTest
import entities.schema.SchemaIdentifierChangedEntity
import junit.framework.Assert
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Created by Tim Osborn on 8/23/15.
 */
@Ignore
class TestChangeIntegerToLongID : BaseTest() {
    @Before
    @Throws(Exception::class)
    fun startup() {
        this.initialize()
    }

    @After
    @Throws(Exception::class)
    fun teardown() {
        this.shutdown()
    }

    @Test
    @Throws(Exception::class)
    fun deleteData() {
        this.shutdown()
        Thread.sleep(2000)
        BaseTest.deleteDatabase()
    }

    /**
     * Initialize test by inserting dummy record
     * @throws Exception
     */
    @Test
    @Throws(Exception::class)
    fun initializeTest() {

        val attributeEntity = SchemaIdentifierChangedEntity()
        attributeEntity.longValue = 23L

        save(attributeEntity)

        find(attributeEntity)

        Assert.assertTrue(attributeEntity.id == 1L)
        Assert.assertTrue(attributeEntity.longValue === 23L)
    }

    @Test
    @Throws(Exception::class)
    fun testChangeIntToLong() {

        var attributeEntity = SchemaIdentifierChangedEntity()
        attributeEntity.id = 1L


        find(attributeEntity)

        Assert.assertTrue(attributeEntity.id == 1L)
        Assert.assertTrue(attributeEntity.longValue === 23L)

        attributeEntity = SchemaIdentifierChangedEntity()
        attributeEntity.longValue = 22L
        save(attributeEntity)

        attributeEntity = SchemaIdentifierChangedEntity()
        attributeEntity.id = 2

        find(attributeEntity)

        Assert.assertTrue(attributeEntity.id == 2L)
        Assert.assertTrue(attributeEntity.longValue === 22L)

    }
}