package web.list;

import category.WebServerTests;
import com.onyx.application.WebDatabaseServer;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import entities.AllAttributeEntity;
import entities.AllAttributeForFetch;
import entities.AllAttributeV2Entity;
import org.junit.*;
import org.junit.experimental.categories.Category;
import web.base.BaseTest;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Created by timothy.osborn on 11/6/14.
 */
@Category({ WebServerTests.class })
public class EqualsTest extends BaseTest
{

    @Before
    public void seedData() throws InitializationException
    {
        initialize();

        AllAttributeForFetch entity = new AllAttributeForFetch();
        entity.id = "FIRST ONE";
        entity.stringValue = "Some test strin";
        entity.dateValue = new Date(1000);
        entity.doublePrimitive = 3.3;
        entity.doubleValue = 1.1;
        entity.booleanValue = false;
        entity.booleanPrimitive = true;
        entity.longPrimitive = 1000l;
        entity.longValue = 323l;
        entity.intValue = 3;
        entity.intPrimitive = 3;
        entity.mutableFloat = 34.3f;
        entity.floatValue = 55.3f;
        entity.mutableByte = (byte)43;
        entity.byteValue = (byte)99;
        entity.mutableShort = 828;
        entity.shortValue = 882;
        entity.mutableChar = 'A';
        entity.charValue = 'C';
        entity.entity = new AllAttributeV2Entity();
        entity.entity.id = "ASDF";
        entity.operator = QueryCriteriaOperator.CONTAINS;

        save(entity);
        find(entity);

        entity = new AllAttributeForFetch();
        entity.id = "FIRST ONE1";
        entity.stringValue = "Some test strin1";
        entity.dateValue = new Date(1001);
        entity.doublePrimitive = 3.31;
        entity.doubleValue = 1.11;
        entity.booleanValue = true;
        entity.booleanPrimitive = false;
        entity.longPrimitive = 1002l;
        entity.longValue = 322l;
        entity.intValue = 2;
        entity.intPrimitive = 4;
        entity.mutableFloat = 34.2f;
        entity.floatValue = 55.2f;
        entity.mutableByte = (byte)42;
        entity.byteValue = (byte)98;
        entity.mutableShort = 827;
        entity.shortValue = 881;
        entity.mutableChar = 'P';
        entity.charValue = 'F';
        entity.entity = new AllAttributeV2Entity();
        entity.entity.id = "ASDFL";
        entity.operator = QueryCriteriaOperator.EQUAL;
        save(entity);
        find(entity);

        entity = new AllAttributeForFetch();
        entity.id = "FIRST ONE2";
        entity.stringValue = "Some test strin1";
        entity.dateValue = new Date(1001);
        entity.doublePrimitive = 3.31;
        entity.doubleValue = 1.11;
        entity.booleanValue = true;
        entity.booleanPrimitive = false;
        entity.longPrimitive = 1002l;
        entity.longValue = 322l;
        entity.intValue = 2;
        entity.intPrimitive = 4;
        entity.mutableFloat = 34.1f;
        entity.floatValue = 55.1f;
        entity.mutableByte = (byte)41;
        entity.byteValue = (byte)91;
        entity.mutableShort = 821;
        entity.shortValue = 881;
        entity.mutableChar = '1';
        entity.charValue = '2';
        entity.entity = new AllAttributeV2Entity();
        entity.entity.id = "ASDF1";
        entity.operator = QueryCriteriaOperator.GREATER_THAN_EQUAL;
        save(entity);
        find(entity);

        entity = new AllAttributeForFetch();
        entity.id = "FIRST ONE3";
        entity.stringValue = "Some test strin2";
        entity.dateValue = new Date(1002);
        entity.doublePrimitive = 3.32;
        entity.doubleValue = 1.12;
        entity.booleanValue = true;
        entity.booleanPrimitive = false;
        entity.longPrimitive = 1001l;
        entity.longValue = 321l;
        entity.intValue = 5;
        entity.intPrimitive = 6;
        entity.mutableFloat = 31.3f;
        entity.floatValue = 51.3f;
        entity.mutableByte = (byte)13;
        entity.byteValue = (byte)19;
        entity.mutableShort = 818;
        entity.shortValue = 812;
        entity.mutableChar = '9';
        entity.charValue = 'O';
        entity.entity = new AllAttributeV2Entity();
        entity.entity.id = "ASDAAF";
        entity.operator = QueryCriteriaOperator.CONTAINS;
        save(entity);
        find(entity);

        entity = new AllAttributeForFetch();
        entity.id = "FIRST ONE3";
        entity.stringValue = "Some test strin3";
        entity.dateValue = new Date(1022);
        entity.doublePrimitive = 3.35;
        entity.doubleValue = 1.126;
        entity.booleanValue = false;
        entity.booleanPrimitive = true;
        entity.longPrimitive = 1301l;
        entity.longValue = 322l;
        entity.intValue = 6;
        entity.intPrimitive = 3;
        entity.mutableFloat = 34.3f;
        entity.floatValue = 55.3f;
        entity.mutableByte = (byte)43;
        entity.byteValue = (byte)99;
        entity.mutableShort = 828;
        entity.shortValue = 882;
        entity.mutableChar = 'A';
        entity.charValue = 'C';
        entity.entity = new AllAttributeV2Entity();
        entity.entity.id = "ASDF";
        entity.operator = QueryCriteriaOperator.CONTAINS;

        save(entity);
        find(entity);

        entity = new AllAttributeForFetch();
        entity.id = "FIRST ONE4";
        save(entity);
        find(entity);

        entity = new AllAttributeForFetch();
        entity.id = "FIRST ONE5";
        save(entity);
        find(entity);
    }

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Test
    public void testStringEquals() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1");

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(2, results.size());
        Assert.assertEquals(results.get(0).stringValue, "Some test strin1");
        Assert.assertEquals(results.get(1).stringValue, "Some test strin1");
    }


    @Test
    public void testEqualsStringId() throws Exception
    {
        final QueryCriteria criteria = new QueryCriteria("id", QueryCriteriaOperator.EQUAL, "FIRST ONE3");
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(results.get(0).id, "FIRST ONE3");
    }

    @Test
    public void testNumberEquals() throws Exception
    {
        final QueryCriteria criteria = new QueryCriteria("longValue", QueryCriteriaOperator.EQUAL, 322l);

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(3, results.size());
        Assert.assertEquals(results.get(0).longValue, Long.valueOf(322l));

        final QueryCriteria criteria2 = new QueryCriteria("longPrimitive", QueryCriteriaOperator.EQUAL, 1301l);
        results = manager.list(AllAttributeForFetch.class, criteria2);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(results.get(0).longPrimitive, 1301l);
    }

    @Test
    public void testDateEquals() throws Exception
    {

        final QueryCriteria criteria = new QueryCriteria("dateValue", QueryCriteriaOperator.EQUAL, new Date(1000));
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(results.get(0).dateValue, new Date(1000));
    }

    @Test
    public void testIntegerEquals() throws Exception
    {
        final QueryCriteria criteria = new QueryCriteria("intValue", QueryCriteriaOperator.EQUAL, 2);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(2, results.size());
    }

    @Test
    public void testDoubleEquals() throws Exception
    {
        final QueryCriteria criteria = new QueryCriteria("doubleValue", QueryCriteriaOperator.EQUAL, 1.126);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(results.get(0).doubleValue, Double.valueOf(1.126));

        final QueryCriteria criteria2 = new QueryCriteria("doublePrimitive", QueryCriteriaOperator.EQUAL, 3.35);
        results = manager.list(AllAttributeForFetch.class, criteria2);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(Double.valueOf(results.get(0).doublePrimitive), Double.valueOf(3.35));
    }

    @Test
    public void testBooleanEquals() throws Exception
    {
        final QueryCriteria criteria = new QueryCriteria("booleanValue", QueryCriteriaOperator.EQUAL, false);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(2, results.size());
        Assert.assertEquals(results.get(0).booleanValue, Boolean.valueOf(false));

        final QueryCriteria criteria2 = new QueryCriteria("booleanPrimitive", QueryCriteriaOperator.EQUAL, true);
        List<AllAttributeForFetch> results2 = manager.list(AllAttributeForFetch.class, criteria2);
        Assert.assertEquals(2, results2.size());
        Assert.assertEquals(results2.get(0).booleanPrimitive, true);
    }



    @Test
    public void testFloatEquals() throws EntityException
    {
        final QueryCriteria criteria = new QueryCriteria("floatValue", QueryCriteriaOperator.EQUAL, 55.3f);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(2, results.size());
        Assert.assertTrue(results.get(0).floatValue == 55.3f);

        final QueryCriteria criteria2 = new QueryCriteria("mutableFloat", QueryCriteriaOperator.EQUAL, 34.3f);
        List<AllAttributeForFetch> results2 = manager.list(AllAttributeForFetch.class, criteria2);
        Assert.assertEquals(2, results2.size());
        Assert.assertTrue(results2.get(0).mutableFloat == 34.3f);
    }


    @Test
    public void testByteEquals() throws EntityException
    {
        final QueryCriteria criteria = new QueryCriteria("mutableByte", QueryCriteriaOperator.EQUAL, (byte)43);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(2, results.size());
        Assert.assertTrue(results.get(0).mutableByte == (byte)43);

        final QueryCriteria criteria2 = new QueryCriteria("byteValue", QueryCriteriaOperator.EQUAL, (byte)99);
        List<AllAttributeForFetch> results2 = manager.list(AllAttributeForFetch.class, criteria2);
        Assert.assertEquals(2, results2.size());
        Assert.assertTrue(results2.get(0).byteValue == (byte)99);
    }

    @Test
    public void testShortEquals() throws EntityException
    {
        final QueryCriteria criteria = new QueryCriteria("mutableShort", QueryCriteriaOperator.EQUAL, (short)828);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(2, results.size());
        Assert.assertTrue(results.get(0).mutableShort == 828);

        final QueryCriteria criteria2 = new QueryCriteria("shortValue", QueryCriteriaOperator.EQUAL, (short)882);
        List<AllAttributeForFetch> results2 = manager.list(AllAttributeForFetch.class, criteria2);
        Assert.assertEquals(2, results2.size());
        Assert.assertTrue(results2.get(0).shortValue == 882);
    }

    @Test
    public void testCharEquals() throws EntityException
    {
        final QueryCriteria criteria = new QueryCriteria("mutableChar", QueryCriteriaOperator.EQUAL, 'A');
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(2, results.size());
        Assert.assertTrue(results.get(0).mutableChar == 'A');

        final QueryCriteria criteria2 = new QueryCriteria("charValue", QueryCriteriaOperator.EQUAL, 'C');
        List<AllAttributeForFetch> results2 = manager.list(AllAttributeForFetch.class, criteria2);
        Assert.assertEquals(2, results2.size());
        Assert.assertTrue(results2.get(0).charValue == 'C');
    }
}
