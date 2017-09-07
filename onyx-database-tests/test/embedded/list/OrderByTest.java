package embedded.list;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;
import embedded.base.BaseTest;
import entities.AllAttributeEntity;
import entities.AllAttributeForFetch;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Created by timothy.osborn on 11/8/14.
 */
@Category({ EmbeddedDatabaseTests.class })
public class OrderByTest extends BaseTest
{
    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Before
    public void seedData() throws OnyxException
    {
        initialize();

        Query deleteQuery = new Query(AllAttributeEntity.class);
        manager.executeDelete(deleteQuery);

        AllAttributeForFetch entity = new AllAttributeForFetch();
        entity.id = "FIRST ONE";
        entity.stringValue = "Some test strin";
        entity.dateValue = new Date(1483737266383l);
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
        entity.dateValue = new Date(1483737267383l);
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
        entity.dateValue = new Date(1483737268383l);
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
        entity.dateValue = new Date(1483737367383l);
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
        entity.dateValue = new Date(1493737267383l);
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

    @Test
    public void testOrderByString() throws OnyxException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ");
        QueryOrder[] orderList = {new QueryOrder("longValue", false), new QueryOrder("id", false)};

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList, orderList);
        Assert.assertEquals(results.get(0).id, "FIRST ONE");
        Assert.assertEquals(results.get(1).id, "FIRST ONE3");
        Assert.assertEquals(results.get(2).id, "FIRST ONE2");
        Assert.assertEquals(results.get(3).id, "FIRST ONE1");
        Assert.assertEquals(results.get(4).id, "FIRST ONE5");
        Assert.assertEquals(results.get(5).id, "FIRST ONE4");
    }

    @Test
    public void testOrderByNumber() throws OnyxException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ");
        QueryOrder[] orderList = {new QueryOrder("longValue", false)};

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList, orderList);
        Assert.assertEquals(results.get(0).id, "FIRST ONE");
    }

    @Test
    public void testOrderByDoubleDesc() throws OnyxException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ");
        QueryOrder[] orderList = {new QueryOrder("doubleValue", false)};

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList, orderList);
        Assert.assertEquals(results.get(0).id, "FIRST ONE3");
    }

    @Test
    public void testOrderByDoubleAsc() throws OnyxException
    {

        QueryCriteria criteriaList = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ");
        QueryOrder[] orderList = {new QueryOrder("doubleValue", true), new QueryOrder("id", true)};

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList, orderList);
        Assert.assertEquals(results.get(0).id, "FIRST ONE4");
    }

    @Test
    public void testOrderByIntDesc() throws OnyxException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ");
        QueryOrder[] orderList = {new QueryOrder("intValue", false)};

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList, orderList);
        Assert.assertEquals(results.get(0).id, "FIRST ONE3");
    }

    @Test
    public void testOrderByIntAsc() throws OnyxException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ");
        QueryOrder[] orderList = {new QueryOrder("intValue", true), new QueryOrder("id", true)};

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList, orderList);
        Assert.assertEquals(results.get(2).id, "FIRST ONE1");
    }

    @Test
    public void testOrderByDateDesc() throws OnyxException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ");
        QueryOrder[] orderList = {new QueryOrder("dateValue", false)};

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList, orderList);
        Assert.assertEquals(results.get(0).id, "FIRST ONE3");
    }

    @Test
    public void testOrderByDateAsc() throws OnyxException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_EQUAL, "ZZZ");
        QueryOrder[] orderList = {new QueryOrder("dateValue", true), new QueryOrder("id", true)};

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList, orderList);
        Assert.assertEquals(results.get(0).id, "FIRST ONE4");
        Assert.assertEquals(results.get(2).id, "FIRST ONE");
    }
}
