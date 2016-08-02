package schemaupdate;

import embedded.base.BaseTest;
import entities.schema.SchemaAttributeEntity;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Date;

/**
 * Created by tosborn1 on 8/23/15.
 */
@Ignore
public class TestAttributeUpdate extends BaseTest
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

        save(attributeEntity);

        find(attributeEntity);

    }

    /**
     * This tests whether you can add an attribute to an entity
     *
     * PRE - Uncomment addedAttribute in class SchemaAttributeEntity.  This is a manual process
     */
    @Test
    public void addAttributeTest() throws Exception
    {
        SchemaAttributeEntity attributeEntity = new SchemaAttributeEntity();
        attributeEntity.id = "A";
        find(attributeEntity);

        // Use reflection so it can compile
        Field addedAttributeField = SchemaAttributeEntity.class.getField("addedAttribute");
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

        addedAttributeField.set(attributeEntity, "I ADDED THIS");

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
        Assert.assertTrue(addedAttributeField.get(attributeEntity).equals("I ADDED THIS"));
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

        save(attributeEntity);
    }

    /**
     * This tests if you can remove an attribute
     *
     * PRE - Change intValue to type Long
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

        save(attributeEntity);
    }
}
