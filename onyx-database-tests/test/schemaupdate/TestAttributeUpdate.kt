package schemaupdate

import embedded.base.BaseTest
import entities.schema.SchemaAttributeEntity
import junit.framework.Assert
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import java.lang.reflect.Field
import java.util.Date

/**
 * Created by Tim Osborn on 8/23/15.
 */
@Ignore
class TestAttributeUpdate : BaseTest() {
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
    fun initializeTestWithBasicAttribute() {

        val attributeEntity = SchemaAttributeEntity()
        attributeEntity.id = "A"
        attributeEntity.booleanPrimitive = true
        attributeEntity.booleanValue = true
        attributeEntity.longPrimitive = 23L
        attributeEntity.longValue = 34L
        attributeEntity.doublePrimitive = 23.23
        attributeEntity.doubleValue = 23.2332
        attributeEntity.dateValue = Date(234234)
        attributeEntity.intValue = 25
        attributeEntity.intPrimitive = 29
        attributeEntity.stringValue = "B"

        save(attributeEntity)

        find(attributeEntity)

    }

    /**
     * This tests whether you can add an attribute to an entity
     *
     * PRE - Uncomment addedAttribute in class SchemaAttributeEntity.  This is a manual process
     */
    @Test
    @Throws(Exception::class)
    fun addAttributeTest() {
        var attributeEntity = SchemaAttributeEntity()
        attributeEntity.id = "A"
        find(attributeEntity)

        // Use reflection so it can compile
        val addedAttributeField = SchemaAttributeEntity::class.java.getField("addedAttribute")
        addedAttributeField.isAccessible = true

        Assert.assertTrue(attributeEntity.booleanPrimitive == true)
        Assert.assertTrue(attributeEntity.booleanValue == true)
        Assert.assertTrue(attributeEntity.longPrimitive == 23L)
        Assert.assertTrue(attributeEntity.longValue === 34L)
        Assert.assertTrue(attributeEntity.doublePrimitive == 23.23)
        Assert.assertTrue(attributeEntity.doubleValue === 23.2332)
        Assert.assertTrue(attributeEntity.dateValue == Date(234234))
        Assert.assertTrue(attributeEntity.intValue == 25)
        Assert.assertTrue(attributeEntity.intPrimitive == 29)
        Assert.assertTrue(attributeEntity.stringValue == "B")

        addedAttributeField.set(attributeEntity, "I ADDED THIS")

        save(attributeEntity)

        attributeEntity = SchemaAttributeEntity()
        attributeEntity.id = "A"
        find(attributeEntity)

        Assert.assertTrue(attributeEntity.booleanPrimitive == true)
        Assert.assertTrue(attributeEntity.booleanValue === true)
        Assert.assertTrue(attributeEntity.longPrimitive == 23L)
        Assert.assertTrue(attributeEntity.longValue === 34L)
        Assert.assertTrue(attributeEntity.doublePrimitive == 23.23)
        Assert.assertTrue(attributeEntity.doubleValue === 23.2332)
        Assert.assertTrue(attributeEntity.dateValue == Date(234234))
        Assert.assertTrue(attributeEntity.intValue == 25)
        Assert.assertTrue(attributeEntity.intPrimitive == 29)
        Assert.assertTrue(attributeEntity.stringValue == "B")
        Assert.assertTrue(addedAttributeField.get(attributeEntity) == "I ADDED THIS")
    }

    /**
     * This tests if you can remove an attribute
     *
     * PRE - Comment out property addedAttribute.  This is a manual process
     */
    @Test
    fun removeAttributeTest() {
        val attributeEntity = SchemaAttributeEntity()
        attributeEntity.id = "A"
        find(attributeEntity)

        Assert.assertTrue(attributeEntity.booleanPrimitive == true)
        Assert.assertTrue(attributeEntity.booleanValue == true)
        Assert.assertTrue(attributeEntity.longPrimitive == 23L)
        Assert.assertTrue(attributeEntity.longValue === 34L)
        Assert.assertTrue(attributeEntity.doublePrimitive == 23.23)
        Assert.assertTrue(attributeEntity.doubleValue === 23.2332)
        Assert.assertTrue(attributeEntity.dateValue == Date(234234))
        Assert.assertTrue(attributeEntity.intValue == 25)
        Assert.assertTrue(attributeEntity.intPrimitive == 29)
        Assert.assertTrue(attributeEntity.stringValue == "B")

        save(attributeEntity)
    }

    /**
     * This tests if you can remove an attribute
     *
     * PRE - Change intValue to type Long
     */
    @Test
    fun testChangeIntToLongType() {
        val attributeEntity = SchemaAttributeEntity()
        attributeEntity.id = "A"
        find(attributeEntity)

        Assert.assertTrue(attributeEntity.booleanPrimitive == true)
        Assert.assertTrue(attributeEntity.booleanValue == true)
        Assert.assertTrue(attributeEntity.longPrimitive == 23L)
        Assert.assertTrue(attributeEntity.longValue === 34L)
        Assert.assertTrue(attributeEntity.doublePrimitive == 23.23)
        Assert.assertTrue(attributeEntity.doubleValue === 23.2332)
        Assert.assertTrue(attributeEntity.dateValue == Date(234234))
        Assert.assertTrue(attributeEntity.intValue.toLong() == 25L)
        Assert.assertTrue(attributeEntity.intPrimitive == 29)
        Assert.assertTrue(attributeEntity.stringValue == "B")

        save(attributeEntity)
    }
}
