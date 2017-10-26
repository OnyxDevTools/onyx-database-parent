package schemaupdate;

import com.onyx.exception.OnyxException;
import com.onyx.exception.InvalidRelationshipTypeException;
import embedded.base.BaseTest;
import entities.schema.SchemaAttributeEntity;
import entities.schema.SchemaRelationshipEntity;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Date;

/**
 * Created by Tim Osborn on 8/23/15.
 */
@Ignore
public class TestRelationshipUpdate extends BaseTest
{
    @Before
    public void startup() throws Exception
    {
        this.initialize();
    }

    @After
    public void teardown() throws Exception
    {
        this.shutdown();
    }

    @Test
    public void deleteData() throws Exception
    {
        this.shutdown();
        Thread.sleep(2000);
        deleteDatabase();
    }

    /**
     * Initialize test by inserting dummy record
     * @throws Exception
     */
    @Test
    public void initializeTestWithBasicAttribute() throws Exception
    {

        SchemaAttributeEntity attributeEntity = new SchemaAttributeEntity();
        attributeEntity.id = "A";
        attributeEntity.booleanPrimitive = true;
        attributeEntity.booleanValue = true;
        attributeEntity.longPrimitive = 23l;
        attributeEntity.longValue = 34l;
        attributeEntity.doublePrimitive = 23.23;
        attributeEntity.doubleValue = 23.2332;
        attributeEntity.dateValue = new Date(234234);
        attributeEntity.intValue = 25;
        attributeEntity.intPrimitive = 29;
        attributeEntity.stringValue = "B";

        attributeEntity.child = new SchemaRelationshipEntity();
        attributeEntity.child.id = "HIYA";
        attributeEntity.child.booleanPrimitive = true;
        attributeEntity.child.booleanValue = true;
        attributeEntity.child.longPrimitive = 23l;
        attributeEntity.child.longValue = 34l;
        attributeEntity.child.doublePrimitive = 23.23;
        attributeEntity.child.doubleValue = 23.2332;
        attributeEntity.child.dateValue = new Date(234234);
        attributeEntity.child.intValue = 25;
        attributeEntity.child.intPrimitive = 29;
        attributeEntity.child.stringValue = "B";
        save(attributeEntity);

        find(attributeEntity);

        Assert.assertTrue(attributeEntity.child != null);

    }

    /**
     * This tests whether you can add an attribute to an entity
     *
     * PRE - Uncomment addedAttribute in class SchemaRelationshipEntity.  This is a manual process
     */
    @Test
    public void addAttributeTest() throws Exception
    {
        SchemaAttributeEntity attributeEntity = new SchemaAttributeEntity();
        attributeEntity.id = "A";
        find(attributeEntity);

        // Use reflection so it can compile
        Field addedAttributeField = SchemaRelationshipEntity.class.getField("addedAttribute");
        addedAttributeField.setAccessible(true);

        Assert.assertTrue(attributeEntity.booleanPrimitive == true);
        Assert.assertTrue(attributeEntity.booleanValue.equals(true));
        Assert.assertTrue(attributeEntity.longPrimitive == 23l);
        Assert.assertTrue(attributeEntity.longValue == 34l);
        Assert.assertTrue(attributeEntity.doublePrimitive == 23.23);
        Assert.assertTrue(attributeEntity.doubleValue == 23.2332);
        Assert.assertTrue(attributeEntity.dateValue.equals(new Date(234234)));
        Assert.assertTrue(attributeEntity.intValue == 25);
        Assert.assertTrue(attributeEntity.intPrimitive == 29);
        Assert.assertTrue(attributeEntity.stringValue.equals("B"));

        Assert.assertTrue(attributeEntity.child.booleanPrimitive == true);
        Assert.assertTrue(attributeEntity.child.booleanValue.equals(true));
        Assert.assertTrue(attributeEntity.child.longPrimitive == 23l);
        Assert.assertTrue(attributeEntity.child.longValue == 34l);
        Assert.assertTrue(attributeEntity.child.doublePrimitive == 23.23);
        Assert.assertTrue(attributeEntity.child.doubleValue == 23.2332);
        Assert.assertTrue(attributeEntity.child.dateValue.equals(new Date(234234)));
        Assert.assertTrue(attributeEntity.child.intValue == 25);
        Assert.assertTrue(attributeEntity.child.intPrimitive == 29);
        Assert.assertTrue(attributeEntity.child.stringValue.equals("B"));

        addedAttributeField.set(attributeEntity.child, "I ADDED THIS");

        save(attributeEntity);

        attributeEntity = new SchemaAttributeEntity();
        attributeEntity.id = "A";
        find(attributeEntity);

        Assert.assertTrue(attributeEntity.booleanPrimitive == true);
        Assert.assertTrue(attributeEntity.booleanValue == true);
        Assert.assertTrue(attributeEntity.longPrimitive == 23l);
        Assert.assertTrue(attributeEntity.longValue == 34l);
        Assert.assertTrue(attributeEntity.doublePrimitive == 23.23);
        Assert.assertTrue(attributeEntity.doubleValue == 23.2332);
        Assert.assertTrue(attributeEntity.dateValue.equals(new Date(234234)));
        Assert.assertTrue(attributeEntity.intValue == 25);
        Assert.assertTrue(attributeEntity.intPrimitive == 29);
        Assert.assertTrue(attributeEntity.stringValue.equals("B"));

        Assert.assertTrue(attributeEntity.child.booleanPrimitive == true);
        Assert.assertTrue(attributeEntity.child.booleanValue.equals(true));
        Assert.assertTrue(attributeEntity.child.longPrimitive == 23l);
        Assert.assertTrue(attributeEntity.child.longValue == 34l);
        Assert.assertTrue(attributeEntity.child.doublePrimitive == 23.23);
        Assert.assertTrue(attributeEntity.child.doubleValue == 23.2332);
        Assert.assertTrue(attributeEntity.child.dateValue.equals(new Date(234234)));
        Assert.assertTrue(attributeEntity.child.intValue == 25);
        Assert.assertTrue(attributeEntity.child.intPrimitive == 29);
        Assert.assertTrue(attributeEntity.child.stringValue.equals("B"));

        Assert.assertTrue(addedAttributeField.get(attributeEntity.child).equals("I ADDED THIS"));
    }

    /**
     * This tests if you can remove an attribute
     *
     * PRE - Comment out property addedAttribute.  This is a manual process
     */
    @Test
    public void removeAttributeTest() {
        SchemaAttributeEntity attributeEntity = new SchemaAttributeEntity();
        attributeEntity.id = "A";
        find(attributeEntity);

        Assert.assertTrue(attributeEntity.booleanPrimitive == true);
        Assert.assertTrue(attributeEntity.booleanValue.equals(true));
        Assert.assertTrue(attributeEntity.longPrimitive == 23l);
        Assert.assertTrue(attributeEntity.longValue == 34l);
        Assert.assertTrue(attributeEntity.doublePrimitive == 23.23);
        Assert.assertTrue(attributeEntity.doubleValue == 23.2332);
        Assert.assertTrue(attributeEntity.dateValue.equals(new Date(234234)));
        Assert.assertTrue(attributeEntity.intValue == 25);
        Assert.assertTrue(attributeEntity.intPrimitive == 29);
        Assert.assertTrue(attributeEntity.stringValue.equals("B"));

        Assert.assertTrue(attributeEntity.child.booleanPrimitive == true);
        Assert.assertTrue(attributeEntity.child.booleanValue.equals(true));
        Assert.assertTrue(attributeEntity.child.longPrimitive == 23l);
        Assert.assertTrue(attributeEntity.child.longValue == 34l);
        Assert.assertTrue(attributeEntity.child.doublePrimitive == 23.23);
        Assert.assertTrue(attributeEntity.child.doubleValue == 23.2332);
        Assert.assertTrue(attributeEntity.child.dateValue.equals(new Date(234234)));
        Assert.assertTrue(attributeEntity.child.intValue == 25);
        Assert.assertTrue(attributeEntity.child.intPrimitive == 29);
        Assert.assertTrue(attributeEntity.child.stringValue.equals("B"));

        save(attributeEntity);
    }

    /**
     * This tests if you can remove an attribute
     *
     * PRE - Change intValue to type Long on SchemaRelationshipEntity
     * POST - Change intValue back to int on SchemaRelationshipEntity
     */
    @Test
    public void testChangeIntToLongType() {
        SchemaAttributeEntity attributeEntity = new SchemaAttributeEntity();
        attributeEntity.id = "A";
        find(attributeEntity);

        Assert.assertTrue(attributeEntity.booleanPrimitive == true);
        Assert.assertTrue(attributeEntity.booleanValue.equals(true));
        Assert.assertTrue(attributeEntity.longPrimitive == 23l);
        Assert.assertTrue(attributeEntity.longValue == 34l);
        Assert.assertTrue(attributeEntity.doublePrimitive == 23.23);
        Assert.assertTrue(attributeEntity.doubleValue == 23.2332);
        Assert.assertTrue(attributeEntity.dateValue.equals(new Date(234234)));
        Assert.assertTrue(attributeEntity.intValue == 25l);
        Assert.assertTrue(attributeEntity.intPrimitive == 29);
        Assert.assertTrue(attributeEntity.stringValue.equals("B"));

        Assert.assertTrue(attributeEntity.child.booleanPrimitive == true);
        Assert.assertTrue(attributeEntity.child.booleanValue.equals(true));
        Assert.assertTrue(attributeEntity.child.longPrimitive == 23l);
        Assert.assertTrue(attributeEntity.child.longValue == 34l);
        Assert.assertTrue(attributeEntity.child.doublePrimitive == 23.23);
        Assert.assertTrue(attributeEntity.child.doubleValue == 23.2332);
        Assert.assertTrue(attributeEntity.child.dateValue.equals(new Date(234234)));
        Assert.assertTrue(attributeEntity.child.intValue == 25);
        Assert.assertTrue(attributeEntity.child.intPrimitive == 29);
        Assert.assertTrue(attributeEntity.child.stringValue.equals("B"));

        save(attributeEntity);
    }

    /**
     * This tests if you can add a relationship
     *
     * PRE - Uncomment relationship addedRelationship on SchemaRelationshipEntity
     */
    @Test
    public void testAddRelationship() {
        SchemaRelationshipEntity relationshipEntity = new SchemaRelationshipEntity();
        relationshipEntity.id = "HIYA";
        find(relationshipEntity);

        Assert.assertTrue(relationshipEntity.booleanPrimitive == true);
        Assert.assertTrue(relationshipEntity.booleanValue.equals(true));
        Assert.assertTrue(relationshipEntity.longPrimitive == 23l);
        Assert.assertTrue(relationshipEntity.longValue == 34l);
        Assert.assertTrue(relationshipEntity.doublePrimitive == 23.23);
        Assert.assertTrue(relationshipEntity.doubleValue == 23.2332);
        Assert.assertTrue(relationshipEntity.dateValue.equals(new Date(234234)));
        Assert.assertTrue(relationshipEntity.intValue == 25l);
        Assert.assertTrue(relationshipEntity.intPrimitive == 29);
        Assert.assertTrue(relationshipEntity.stringValue.equals("B"));

//        relationshipEntity.addedRelationship = new SchemaAttributeEntity();
//        relationshipEntity.addedRelationship.id = "B";

        save(relationshipEntity);

        relationshipEntity = new SchemaRelationshipEntity();
        relationshipEntity.id = "HIYA";
    }

    /**
     *  This tests if you can change a toOne relationship to a toMany
     *
     *  PRE - Change SchemaRelationshipEntity parent to a ONE_TO_MANY
     *  PRE - Change SchemaAttributeEntity to a MANY_TO_ONE
     *
     *  POST - R
     */
    @Test
    public void testChangeToManyRelationship()
    {
        SchemaRelationshipEntity relationshipEntity = new SchemaRelationshipEntity();
        relationshipEntity.id = "HIYA";
        find(relationshipEntity);
//        Assert.assertTrue(relationshipEntity.parent.size() == 1);
    }

    /**
     * This tests if you can remove an relationship
     *
     * PRE - Comment out relationship addedRelationship on SchemaRelationshipEntity
     */
    @Test(expected = InvalidRelationshipTypeException.class)
    public void testChangeRelationshipToOne() throws OnyxException {
        SchemaRelationshipEntity relationshipEntity = new SchemaRelationshipEntity();
        relationshipEntity.id = "HIYA";
        manager.find(relationshipEntity);
    }
    /**
     * This tests if you can remove an relationship
     *
     * PRE - Comment out relationship addedRelationship on SchemaRelationshipEntity
     */
    @Test
    public void testRemoveRelationship() {
        SchemaRelationshipEntity relationshipEntity = new SchemaRelationshipEntity();
        relationshipEntity.id = "HIYA";
        find(relationshipEntity);

        Assert.assertTrue(relationshipEntity.booleanPrimitive == true);
        Assert.assertTrue(relationshipEntity.booleanValue.equals(true));
        Assert.assertTrue(relationshipEntity.longPrimitive == 23l);
        Assert.assertTrue(relationshipEntity.longValue == 34l);
        Assert.assertTrue(relationshipEntity.doublePrimitive == 23.23);
        Assert.assertTrue(relationshipEntity.doubleValue == 23.2332);
        Assert.assertTrue(relationshipEntity.dateValue.equals(new Date(234234)));
        Assert.assertTrue(relationshipEntity.intValue == 25l);
        Assert.assertTrue(relationshipEntity.intPrimitive == 29);
        Assert.assertTrue(relationshipEntity.stringValue.equals("B"));

        save(relationshipEntity);
    }
}
