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
 * Created by timothy.osborn on 11/6/14.
 */
@Category({ RemoteServerTests.class })
public class FullTableScanTest extends RemoteBaseTest
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

        entity.longValue = new Long(23l);

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
    public void testBasicAnd() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
                .and("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some test str")
                .and("id", QueryCriteriaOperator.EQUAL, "FIRST ONE1");

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(1, results.size());
        Assert.assertEquals(results.get(0).stringValue, "Some test strin1");
    }

    @Test
    public void testBasicAndOr() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1")
                .or("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3");

        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(3, results.size());
    }


    @Test
    public void testBasicOrsSub() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria containsSubTes = new QueryCriteria("stringValue", QueryCriteriaOperator.CONTAINS, "Some tes");
        QueryCriteria containsSubTestStrin1 = new QueryCriteria("stringValue", QueryCriteriaOperator.CONTAINS, "Some test strin1");

        Assert.assertEquals(4, manager.list(AllAttributeForFetch.class, containsSubTes.or(containsSubTestStrin1)).size());
    }

    @Test
    public void testBasicAndSub() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria stringValueEqualsValue = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1");
        QueryCriteria containsSubTes = new QueryCriteria("stringValue", QueryCriteriaOperator.CONTAINS, "Some tes");
        QueryCriteria intValueNotEqual2 = new QueryCriteria("intValue", QueryCriteriaOperator.NOT_EQUAL, 2);
        QueryCriteria orStringValueEqualsSomeTest = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin2");

        Assert.assertEquals(2, manager.list(AllAttributeForFetch.class, stringValueEqualsValue.and(containsSubTes.or(intValueNotEqual2)).or(orStringValueEqualsSomeTest)).size());
    }

    @Test
    public void testBasicOrSub() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria stringValueEqualsValue = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin1");
        QueryCriteria containsSubTes = new QueryCriteria("stringValue", QueryCriteriaOperator.CONTAINS, "Some tes");
        QueryCriteria intValueNotEqual2 = new QueryCriteria("intValue", QueryCriteriaOperator.NOT_EQUAL, 2);
        QueryCriteria orStringValueEqualsSomeTest = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin2");

        Assert.assertEquals(6, manager.list(AllAttributeForFetch.class, stringValueEqualsValue.or(containsSubTes.or(intValueNotEqual2)).or(orStringValueEqualsSomeTest)).size());
    }

}
