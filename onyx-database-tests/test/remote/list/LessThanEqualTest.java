package remote.list;

import category.RemoteServerTests;
import com.onyx.exception.OnyxException;
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
 * Created by timothy.osborn on 11/8/14.
 */
@Category({ RemoteServerTests.class })
public class LessThanEqualTest extends RemoteBaseTest
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
    public void testStringIDLessThanEqual() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("id",QueryCriteriaOperator.LESS_THAN_EQUAL, "FIRST ONE3");
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(4, results.size());
    }

    @Test
    public void testStringStringLessThanEqual() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("stringValue",QueryCriteriaOperator.LESS_THAN_EQUAL, "Some test strin2");
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(5, results.size());
    }


    @Test
    public void testLongLessThanEqual() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("longValue",QueryCriteriaOperator.LESS_THAN_EQUAL, 323l);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(6, results.size());
    }

    @Test
    public void testPrimitiveLongLessThanEqual() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("longPrimitive",QueryCriteriaOperator.LESS_THAN_EQUAL, 1000l);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(3, results.size());
    }

    @Test
    public void testIntegerLessThanEqual() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("intValue",QueryCriteriaOperator.LESS_THAN_EQUAL, 3);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(5, results.size());
    }

    @Test
    public void testPrimitiveIntegerLessThanEqual() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("intPrimitive",QueryCriteriaOperator.LESS_THAN_EQUAL, 4);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(6, results.size());
    }


    @Test
    public void testDoubleLessThanEqual() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("doubleValue",QueryCriteriaOperator.LESS_THAN_EQUAL, 1.11);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(5, results.size());
    }

    @Test
    public void testPrimitiveDoubleLessThanEqual() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("doublePrimitive",QueryCriteriaOperator.LESS_THAN_EQUAL, 3.32);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(5, results.size());
    }

    @Test
    public void testDateLessThanEqual() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("dateValue",QueryCriteriaOperator.LESS_THAN_EQUAL, new Date(1001));
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(5, results.size());
    }

}
