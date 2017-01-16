package web.list;

import category.WebServerTests;
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
import web.base.BaseTest;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Created by timothy.osborn on 11/6/14.
 */
@Category({ WebServerTests.class })
public class MutableEqualsNullTest extends BaseTest
{
    @After
    public void after() throws IOException
    {
        shutdown();
    }

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

    @Test
    public void testStringEqualsNull() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue",QueryCriteriaOperator.EQUAL, QueryCriteria.NULL_STRING_VALUE);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(2, results.size());
    }

    @Test
    public void testIntEqualsNull() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("intValue",QueryCriteriaOperator.EQUAL, QueryCriteria.NULL_INTEGER_VALUE);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(2, results.size());
    }

    @Test
    public void testLongEqualsNull() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("longValue",QueryCriteriaOperator.EQUAL, QueryCriteria.NULL_LONG_VALUE);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(2, results.size());
    }

    @Test
    public void testDateEqualsNull() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("dateValue",QueryCriteriaOperator.EQUAL, QueryCriteria.NULL_DATE_VALUE);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(2, results.size());
    }

    @Test
    public void testDoubleEqualsNull() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("doubleValue",QueryCriteriaOperator.EQUAL, QueryCriteria.NULL_DOUBLE_VALUE);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(2, results.size());
    }

    @Test
    public void testBooleanEqualsNull() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("booleanValue",QueryCriteriaOperator.EQUAL, QueryCriteria.NULL_BOOLEAN_VALUE);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(2, results.size());
    }
}