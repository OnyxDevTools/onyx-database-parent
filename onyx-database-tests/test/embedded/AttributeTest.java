package embedded;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.QueryCriteriaOperator;
import embedded.base.BaseTest;
import entities.AllAttributeV2Entity;
import entities.EnumEntity;
import entities.InheritedAttributeEntity;
import entities.SimpleEntity;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import pojo.SimpleEnum;

import java.io.IOException;
import java.util.*;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ EmbeddedDatabaseTests.class })
public class AttributeTest extends BaseTest {

    @BeforeClass
    public static void beforeClass()
    {
        deleteDatabase();
    }

    @Before
    public void before() throws InitializationException, InterruptedException
    {
        initialize();
    }

    @After
    public void after() throws IOException
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
    public void testPopulatedEntity() throws EntityException {
        AllAttributeV2Entity entity = new AllAttributeV2Entity();

        entity.id = "A";
        entity.longValue = 4l;
        entity.longPrimitive = 3l;
        entity.stringValue = "STring key";
        entity.dateValue = new Date(1483736263743l);
        entity.doublePrimitive = 342.23;
        entity.doubleValue = 232.2;
        entity.booleanPrimitive = true;
        entity.booleanValue = false;
        entity.mutableFloat = 23.234f;
        entity.floatValue = 666.3453f;
        entity.mutableByte = (byte)5;
        entity.byteValue = (byte)7;
        entity.mutableShort = 65;
        entity.shortValue = 44;
        entity.mutableChar = 'C';
        entity.charValue = 'D';
        entity.entity = new AllAttributeV2Entity();
        entity.entity.shortValue = 49;
        entity.amutableBytes = new Byte[]{(byte)2,(byte)8,(byte)7};
        entity.bytes = new byte[]{(byte)4,(byte)4,(byte)2};
        entity.shorts = new short[]{4,4,2};
        entity.mutableShorts = new Short[]{4,4,2};
        entity.strings = new String[]{"A","V"};
        entity.operator = QueryCriteriaOperator.CONTAINS;
        entity.entitySet = new HashSet();
        entity.entitySet.add(entity.entity);
        entity.entityList = new ArrayList();
        entity.entityList.add(entity.entity);

        manager.saveEntity(entity);

        AllAttributeV2Entity entity2 = new AllAttributeV2Entity();
        entity2.id = "A";
        try
        {
            entity2 = (AllAttributeV2Entity)manager.find(entity2);
        } catch (EntityException e)
        {
            e.printStackTrace();
        }

        Assert.assertEquals("A", entity2.id);
        Assert.assertEquals(Long.valueOf(4l), entity2.longValue);
        Assert.assertEquals(3l, entity2.longPrimitive);
        Assert.assertEquals("STring key", entity2.stringValue);
        Assert.assertEquals(entity.dateValue, entity2.dateValue);
        Assert.assertEquals(new Double(342.23), new Double(entity2.doublePrimitive));
        Assert.assertEquals(Double.valueOf(232.2), entity2.doubleValue);
        Assert.assertEquals(true, entity2.booleanPrimitive);
        Assert.assertEquals(Boolean.valueOf(false), entity2.booleanValue);
        Assert.assertTrue(23.234f == entity2.mutableFloat);
        Assert.assertTrue(666.3453f == entity2.floatValue);
        Assert.assertTrue((byte)5 == entity2.mutableByte);
        Assert.assertTrue((byte)7 == entity2.byteValue);
        Assert.assertTrue(65 == entity2.mutableShort);
        Assert.assertTrue(44 == entity2.shortValue);
        Assert.assertTrue('C' == entity2.mutableChar);
        Assert.assertTrue('D' == entity2.charValue);
        Assert.assertTrue(entity2.operator == QueryCriteriaOperator.CONTAINS);
        Assert.assertTrue(entity2.entity != null && entity2.entity.shortValue == 49);
        Assert.assertArrayEquals(entity2.bytes, new byte[]{(byte)4,(byte)4,(byte)2});
        Assert.assertTrue(entity2.shorts.length == 3 && entity2.shorts[2] == 2);
        Assert.assertTrue(entity2.mutableShorts.length == 3 && entity2.mutableShorts[2] == 2);
        Assert.assertTrue(entity2.strings.length == 2 && entity2.strings[1].equals("V"));
        Assert.assertArrayEquals(entity2.amutableBytes, new Byte[]{(byte)2,(byte)8,(byte)7});
        Assert.assertTrue(entity2.entityList instanceof List && entity2.entityList.size() == 1);
        Assert.assertTrue(entity2.entitySet instanceof Set && entity2.entitySet.size() == 1);
    }

    /**
     * Test null values for properties that are mutable
     */
    @Test
    public void testNullPopulatedEntity()
    {
        AllAttributeV2Entity entity = new AllAttributeV2Entity();

        entity.id = "B";

        try
        {
            manager.saveEntity(entity);
        } catch (EntityException e)
        {
            fail(e.getMessage());
        }

        AllAttributeV2Entity entity2 = new AllAttributeV2Entity();
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

        Assert.assertNull(entity2.mutableFloat);
        Assert.assertNull(entity2.mutableByte);
        Assert.assertNull(entity2.mutableShort);
        Assert.assertNull(entity2.mutableChar);
        Assert.assertNull(entity2.entity);
        Assert.assertNull(entity2.amutableBytes);
/*        Assert.assertNull(entity2.mutableShorts);
        Assert.assertNull(entity2.strings);
        Assert.assertNull(entity2.bytes);
        Assert.assertNull(entity2.shorts);
*/
        Assert.assertTrue(entity2.floatValue == 0.0f);
        Assert.assertTrue(entity2.byteValue == (byte)0);
        Assert.assertTrue(entity2.shortValue == 0);
        Assert.assertTrue(entity2.charValue == (char)0);


    }

    /**
     * Tests that inherited properties are persisted and hydrated properly
     *
     * @throws EntityException
     */
    @Test
    public void testInheritedPopulatedEntity() throws EntityException, InterruptedException {

        InheritedAttributeEntity entity = new InheritedAttributeEntity();

        entity.id = "C";
        entity.longValue = 4l;
        entity.longPrimitive = 3l;
        entity.stringValue = "STring key";
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
        Assert.assertEquals("STring key", entity2.stringValue);
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

    @Test
    public void testEnum() throws EntityException
    {
        EnumEntity enumEntity = new EnumEntity();
        enumEntity.setSimpleId("99HIYA");
        enumEntity.simpleEnum = SimpleEnum.SECOND;
        save(enumEntity);

        find(enumEntity);
        assertEquals(enumEntity.simpleEnum,  SimpleEnum.SECOND);
    }


}
