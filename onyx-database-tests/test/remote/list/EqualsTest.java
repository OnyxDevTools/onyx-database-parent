package remote.list;

import category.RemoteServerTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import entities.AllAttributeForFetch;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Created by timothy.osborn on 11/6/14.
 */
@Category({ RemoteServerTests.class })
public class EqualsTest extends RemoteBaseTest
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

}
