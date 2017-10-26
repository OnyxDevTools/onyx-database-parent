package schemaupdate

import com.onyx.exception.OnyxException
import com.onyx.exception.InvalidRelationshipTypeException
import com.onyx.persistence.IManagedEntity
import embedded.base.BaseTest
import entities.schema.SchemaAttributeEntity
import entities.schema.SchemaRelationshipEntity
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
class TestRelationshipUpdate : BaseTest() {
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

        attributeEntity.child = SchemaRelationshipEntity()
        attributeEntity.child!!.id = "HIYA"
        attributeEntity.child!!.booleanPrimitive = true
        attributeEntity.child!!.booleanValue = true
        attributeEntity.child!!.longPrimitive = 23L
        attributeEntity.child!!.longValue = 34L
        attributeEntity.child!!.doublePrimitive = 23.23
        attributeEntity.child!!.doubleValue = 23.2332
        attributeEntity.child!!.dateValue = Date(234234)
        attributeEntity.child!!.intValue = 25
        attributeEntity.child!!.intPrimitive = 29
        attributeEntity.child!!.stringValue = "B"
        save(attributeEntity)

        find(attributeEntity)

        Assert.assertTrue(attributeEntity.child != null)

    }

    /**
     * This tests whether you can add an attribute to an entity
     *
     * PRE - Uncomment addedAttribute in class SchemaRelationshipEntity.  This is a manual process
     */
    @Test
    @Throws(Exception::class)
    fun addAttributeTest() {
        var attributeEntity = SchemaAttributeEntity()
        attributeEntity.id = "A"
        find(attributeEntity)

        // Use reflection so it can compile
        val addedAttributeField = SchemaRelationshipEntity::class.java.getField("addedAttribute")
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

        Assert.assertTrue(attributeEntity.child!!.booleanPrimitive == true)
        Assert.assertTrue(attributeEntity.child!!.booleanValue == true)
        Assert.assertTrue(attributeEntity.child!!.longPrimitive == 23L)
        Assert.assertTrue(attributeEntity.child!!.longValue === 34L)
        Assert.assertTrue(attributeEntity.child!!.doublePrimitive == 23.23)
        Assert.assertTrue(attributeEntity.child!!.doubleValue === 23.2332)
        Assert.assertTrue(attributeEntity.child!!.dateValue == Date(234234))
        Assert.assertTrue(attributeEntity.child!!.intValue == 25)
        Assert.assertTrue(attributeEntity.child!!.intPrimitive == 29)
        Assert.assertTrue(attributeEntity.child!!.stringValue == "B")

        addedAttributeField.set(attributeEntity.child, "I ADDED THIS")

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

        Assert.assertTrue(attributeEntity.child!!.booleanPrimitive == true)
        Assert.assertTrue(attributeEntity.child!!.booleanValue == true)
        Assert.assertTrue(attributeEntity.child!!.longPrimitive == 23L)
        Assert.assertTrue(attributeEntity.child!!.longValue === 34L)
        Assert.assertTrue(attributeEntity.child!!.doublePrimitive == 23.23)
        Assert.assertTrue(attributeEntity.child!!.doubleValue === 23.2332)
        Assert.assertTrue(attributeEntity.child!!.dateValue == Date(234234))
        Assert.assertTrue(attributeEntity.child!!.intValue == 25)
        Assert.assertTrue(attributeEntity.child!!.intPrimitive == 29)
        Assert.assertTrue(attributeEntity.child!!.stringValue == "B")

        Assert.assertTrue(addedAttributeField.get(attributeEntity.child) == "I ADDED THIS")
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

        Assert.assertTrue(attributeEntity.child!!.booleanPrimitive == true)
        Assert.assertTrue(attributeEntity.child!!.booleanValue == true)
        Assert.assertTrue(attributeEntity.child!!.longPrimitive == 23L)
        Assert.assertTrue(attributeEntity.child!!.longValue === 34L)
        Assert.assertTrue(attributeEntity.child!!.doublePrimitive == 23.23)
        Assert.assertTrue(attributeEntity.child!!.doubleValue === 23.2332)
        Assert.assertTrue(attributeEntity.child!!.dateValue == Date(234234))
        Assert.assertTrue(attributeEntity.child!!.intValue == 25)
        Assert.assertTrue(attributeEntity.child!!.intPrimitive == 29)
        Assert.assertTrue(attributeEntity.child!!.stringValue == "B")

        save(attributeEntity)
    }

    /**
     * This tests if you can remove an attribute
     *
     * PRE - Change intValue to type Long on SchemaRelationshipEntity
     * POST - Change intValue back to int on SchemaRelationshipEntity
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

        Assert.assertTrue(attributeEntity.child!!.booleanPrimitive == true)
        Assert.assertTrue(attributeEntity.child!!.booleanValue == true)
        Assert.assertTrue(attributeEntity.child!!.longPrimitive == 23L)
        Assert.assertTrue(attributeEntity.child!!.longValue === 34L)
        Assert.assertTrue(attributeEntity.child!!.doublePrimitive == 23.23)
        Assert.assertTrue(attributeEntity.child!!.doubleValue === 23.2332)
        Assert.assertTrue(attributeEntity.child!!.dateValue == Date(234234))
        Assert.assertTrue(attributeEntity.child!!.intValue == 25)
        Assert.assertTrue(attributeEntity.child!!.intPrimitive == 29)
        Assert.assertTrue(attributeEntity.child!!.stringValue == "B")

        save(attributeEntity)
    }

    /**
     * This tests if you can add a relationship
     *
     * PRE - Uncomment relationship addedRelationship on SchemaRelationshipEntity
     */
    @Test
    fun testAddRelationship() {
        var relationshipEntity = SchemaRelationshipEntity()
        relationshipEntity.id = "HIYA"
        find(relationshipEntity)

        Assert.assertTrue(relationshipEntity.booleanPrimitive == true)
        Assert.assertTrue(relationshipEntity.booleanValue == true)
        Assert.assertTrue(relationshipEntity.longPrimitive == 23L)
        Assert.assertTrue(relationshipEntity.longValue === 34L)
        Assert.assertTrue(relationshipEntity.doublePrimitive == 23.23)
        Assert.assertTrue(relationshipEntity.doubleValue === 23.2332)
        Assert.assertTrue(relationshipEntity.dateValue == Date(234234))
        Assert.assertTrue(relationshipEntity.intValue.toLong() == 25L)
        Assert.assertTrue(relationshipEntity.intPrimitive == 29)
        Assert.assertTrue(relationshipEntity.stringValue == "B")

        //        relationshipEntity.addedRelationship = new SchemaAttributeEntity();
        //        relationshipEntity.addedRelationship.id = "B";

        save(relationshipEntity)

        relationshipEntity = SchemaRelationshipEntity()
        relationshipEntity.id = "HIYA"
    }

    /**
     * This tests if you can change a toOne relationship to a toMany
     *
     * PRE - Change SchemaRelationshipEntity parent to a ONE_TO_MANY
     * PRE - Change SchemaAttributeEntity to a MANY_TO_ONE
     *
     * POST - R
     */
    @Test
    fun testChangeToManyRelationship() {
        val relationshipEntity = SchemaRelationshipEntity()
        relationshipEntity.id = "HIYA"
        find(relationshipEntity)
        //        Assert.assertTrue(relationshipEntity.parent.size() == 1);
    }

    /**
     * This tests if you can remove an relationship
     *
     * PRE - Comment out relationship addedRelationship on SchemaRelationshipEntity
     */
    @Test(expected = InvalidRelationshipTypeException::class)
    @Throws(OnyxException::class)
    fun testChangeRelationshipToOne() {
        val relationshipEntity = SchemaRelationshipEntity()
        relationshipEntity.id = "HIYA"
        manager.find<IManagedEntity>(relationshipEntity)
    }

    /**
     * This tests if you can remove an relationship
     *
     * PRE - Comment out relationship addedRelationship on SchemaRelationshipEntity
     */
    @Test
    fun testRemoveRelationship() {
        val relationshipEntity = SchemaRelationshipEntity()
        relationshipEntity.id = "HIYA"
        find(relationshipEntity)

        Assert.assertTrue(relationshipEntity.booleanPrimitive == true)
        Assert.assertTrue(relationshipEntity.booleanValue == true)
        Assert.assertTrue(relationshipEntity.longPrimitive == 23L)
        Assert.assertTrue(relationshipEntity.longValue === 34L)
        Assert.assertTrue(relationshipEntity.doublePrimitive == 23.23)
        Assert.assertTrue(relationshipEntity.doubleValue === 23.2332)
        Assert.assertTrue(relationshipEntity.dateValue == Date(234234))
        Assert.assertTrue(relationshipEntity.intValue.toLong() == 25L)
        Assert.assertTrue(relationshipEntity.intPrimitive == 29)
        Assert.assertTrue(relationshipEntity.stringValue == "B")

        save(relationshipEntity)
    }
}
