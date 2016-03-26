package schemaupdate;

import embedded.base.BaseTest;
import entities.schema.SchemaIdentifierChangedEntity;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by tosborn1 on 8/23/15.
 */
public class TestChangeIntegerToLongID extends BaseTest {
    @Before
    public void startup() throws Exception {
        this.initialize();
    }

    @After
    public void teardown() throws Exception {
        this.shutdown();
    }

    @Test
    public void deleteData() throws Exception {
        this.shutdown();
        Thread.sleep(2000);
        deleteDatabase();
    }

    /**
     * Initialize test by inserting dummy record
     * @throws Exception
     */
    @Test
    public void initializeTest() throws Exception {

        SchemaIdentifierChangedEntity attributeEntity = new SchemaIdentifierChangedEntity();
        attributeEntity.longValue = 23l;

        save(attributeEntity);

        find(attributeEntity);

        Assert.assertTrue(attributeEntity.id == 1);
        Assert.assertTrue(attributeEntity.longValue == 23l);
    }

    @Test
    public void testChangeIntToLong() throws Exception {

        SchemaIdentifierChangedEntity attributeEntity = new SchemaIdentifierChangedEntity();
        attributeEntity.id = 1l;


        find(attributeEntity);

        Assert.assertTrue(attributeEntity.id == 1l);
        Assert.assertTrue(attributeEntity.longValue == 23l);

        attributeEntity = new SchemaIdentifierChangedEntity();
        attributeEntity.longValue = 22l;
        save(attributeEntity);

        attributeEntity = new SchemaIdentifierChangedEntity();
        attributeEntity.id = 2;

        find(attributeEntity);

        Assert.assertTrue(attributeEntity.id == 2l);
        Assert.assertTrue(attributeEntity.longValue == 22l);

    }
}