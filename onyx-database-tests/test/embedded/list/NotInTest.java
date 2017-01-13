package embedded.list;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import embedded.base.BaseTest;
import entities.AllAttributeEntity;
import entities.AllAttributeForFetch;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by timothy.osborn on 11/8/14.
 */
@Category({ EmbeddedDatabaseTests.class })
public class NotInTest extends BaseTest
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
    public void testNotInString() throws EntityException
    {
        List stringArray = new ArrayList<>();
        stringArray.add("Some test strin1");
        stringArray.add("Some test strin3");
        QueryCriteria criteriaList = new QueryCriteria("stringValue", QueryCriteriaOperator.NOT_IN, stringArray);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(3, results.size());
        Assert.assertNotEquals(results.get(0).stringValue, "Some test strin1");
        Assert.assertNotEquals(results.get(0).stringValue, "Some test strin1");
    }

    @Test
    public void testNumberNotIn() throws EntityException
    {
        List stringArray = new ArrayList<>();
        stringArray.add(322l);
        stringArray.add(321l);
        QueryCriteria criteriaList = new QueryCriteria("longValue", QueryCriteriaOperator.NOT_IN, stringArray);
        List<AllAttributeEntity> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(3, results.size());
    }

    @Test
    public void testDateNotIn() throws EntityException
    {
        List stringArray = new ArrayList<>();
        stringArray.add(new Date(1000));
        stringArray.add(new Date(1001));
        QueryCriteria criteriaList = new QueryCriteria("dateValue", QueryCriteriaOperator.NOT_IN, stringArray);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(3, results.size());
        Assert.assertNotEquals(results.get(0).dateValue, new Date(1000));
        Assert.assertNotEquals(results.get(0).dateValue, new Date(1001));
    }

    @Test
    public void testDoubleNotIn() throws EntityException
    {
        List stringArray = new ArrayList<>();
        stringArray.add(1.126);
        stringArray.add(1.11);
        QueryCriteria criteriaList = new QueryCriteria("doubleValue", QueryCriteriaOperator.NOT_IN, stringArray);
        List<AllAttributeEntity> results = manager.list(AllAttributeForFetch.class, criteriaList);
        Assert.assertEquals(3, results.size());

    }
}
