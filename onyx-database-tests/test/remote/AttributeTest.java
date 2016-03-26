package remote;

import category.RemoteServerTests;
import com.onyx.application.DatabaseServer;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import remote.base.RemoteBaseTest;
import entities.AllAttributeEntity;
import entities.InheritedAttributeEntity;
import entities.SimpleEntity;

import java.io.IOException;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.Date;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category({ RemoteServerTests.class })
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AttributeTest extends RemoteBaseTest {


    @BeforeClass
    public static void beforeClass() throws InterruptedException {
        deleteDatabase();
        databaseServer = new DatabaseServer();
        databaseServer.setPort(8080);
        databaseServer.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server.oxd");
        databaseServer.setEnableSocketSupport(true);
        databaseServer.setSocketPort(Registry.REGISTRY_PORT);
        databaseServer.start();
        Thread.sleep(2000);
    }

    @AfterClass
    public static void afterClass()
    {
        //databaseServer.stop();
    }

    @Before
    public void before() throws InitializationException, InterruptedException
    {
        initialize();
    }

    @After
    public void after() throws EntityException, IOException
    {
        shutdown();
    }

    /**
     * Tests all possible populated values in order to test a round trip serialization
     *
     * @throws EntityException
     * @throws InitializationException
     */
    @Test
    public void testPopulatedEntity() throws EntityException, InitializationException
    {
        AllAttributeEntity entity = new AllAttributeEntity();

        entity.id = "A";
        entity.longValue = 4l;
        entity.longPrimitive = 3l;
        entity.stringValue = "STring value";
        entity.dateValue = new Date(1483736263743l);
        entity.doublePrimitive = 342.23;
        entity.doubleValue = 232.2;
        entity.booleanPrimitive = true;
        entity.booleanValue = false;

        manager.saveEntity(entity);

        AllAttributeEntity entity2 = new AllAttributeEntity();
        entity2.id = "A";
        try
        {
            entity2 = (AllAttributeEntity)manager.find(entity2);
        } catch (EntityException e)
        {
            e.printStackTrace();
        }

        Assert.assertEquals("A", entity2.id);
        Assert.assertEquals(Long.valueOf(4l), entity2.longValue);
        Assert.assertEquals(3l, entity2.longPrimitive);
        Assert.assertEquals("STring value", entity2.stringValue);
        Assert.assertEquals(entity.dateValue, entity2.dateValue);
        Assert.assertEquals(new Double(342.23), new Double(entity2.doublePrimitive));
        Assert.assertEquals(Double.valueOf(232.2), entity2.doubleValue);
        Assert.assertEquals(true, entity2.booleanPrimitive);
        Assert.assertEquals(Boolean.valueOf(false), entity2.booleanValue);

    }

    /**
     * Test null values for properties that are mutable
     */
    @Test
    public void testNullPopulatedEntity()
    {
        AllAttributeEntity entity = new AllAttributeEntity();

        entity.id = "B";

        try
        {
            manager.saveEntity(entity);
        } catch (EntityException e)
        {
            fail(e.getMessage());
        }

        AllAttributeEntity entity2 = new AllAttributeEntity();
        entity2.id = "B";
        try
        {
            manager.find(entity2);
        } catch (EntityException e)
        {
            fail(e.getMessage());
        }

        Assert.assertEquals("B", entity2.id);
        Assert.assertNull(entity2.longValue);
        Assert.assertEquals(0, entity2.longPrimitive);
        Assert.assertNull(entity2.stringValue);
        Assert.assertNull(entity2.dateValue);
        Assert.assertEquals(new Double(0.0), new Double(entity2.doublePrimitive));
        Assert.assertNull(entity2.doubleValue);
        Assert.assertEquals(false, entity2.booleanPrimitive);
        Assert.assertNull(entity2.booleanValue);

    }

    /**
     * Tests that inherited properties are persisted and hydrated properly
     *
     * @throws EntityException
     */
    @Test
    public void testInheritedPopulatedEntity() throws EntityException, InitializationException
    {

        InheritedAttributeEntity entity = new InheritedAttributeEntity();

        entity.id = "C";
        entity.longValue = 4l;
        entity.longPrimitive = 3l;
        entity.stringValue = "STring value";
        entity.dateValue = new Date(343535);
        entity.doublePrimitive = 342.23;
        entity.doubleValue = 232.2;
        entity.booleanPrimitive = true;
        entity.booleanValue = false;

        try
        {
            manager.saveEntity(entity);
        } catch (EntityException e)
        {
            fail(e.getMessage());
        }


        InheritedAttributeEntity entity2 = new InheritedAttributeEntity();
        entity2.id = "C";

        assertTrue(manager.exists(entity2));
        try
        {
            entity2 = (InheritedAttributeEntity)manager.find(entity2);
        } catch (EntityException e)
        {
            fail("Error finding entity");
        }

        Assert.assertEquals("C", entity2.id);
        Assert.assertEquals(Long.valueOf(4l), entity2.longValue);
        Assert.assertEquals(3l, entity2.longPrimitive);
        Assert.assertEquals("STring value", entity2.stringValue);
        Assert.assertEquals(entity.dateValue, entity2.dateValue);
        Assert.assertEquals(new Double(342.23), new Double(entity2.doublePrimitive));
        Assert.assertEquals(Double.valueOf(232.2), entity2.doubleValue);
        Assert.assertEquals(true, entity2.booleanPrimitive);
        Assert.assertEquals(Boolean.valueOf(false), entity2.booleanValue);

    }


    @Test
    public void simpleMultipleTest() throws EntityException{
        SimpleEntity simpleEntity2 = new SimpleEntity();
        simpleEntity2.setSimpleId("2");
        simpleEntity2.setName("Name2");

        SimpleEntity simpleEntity3 = new SimpleEntity();
        simpleEntity3.setSimpleId("3");
        simpleEntity3.setName("Name3");

        manager.saveEntities(Arrays.asList(simpleEntity2, simpleEntity3));

        //@todo: retreive using manager.list()

    }

    @Test
    public void testFindById() throws EntityException{
        //Save entity
        SimpleEntity entity = new SimpleEntity();
        entity.setSimpleId("1");
        entity.setName("Chris");
        manager.saveEntity(entity);
        //Retreive entity using findById method
        SimpleEntity savedEntity = (SimpleEntity) manager.findById(entity.getClass(), "1");

        //Assert
        Assert.assertTrue(entity.getSimpleId().equals(savedEntity.getSimpleId()));
        Assert.assertTrue(entity.getName().equals(savedEntity.getName()));
    }



}
