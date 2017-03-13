package embedded.list;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import embedded.base.BaseTest;
import entities.AllAttributeForFetchSequenceGen;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Created by tosborn1 on 3/13/17.
 */
@Category({ EmbeddedDatabaseTests.class })
public class IdentifierListTest extends BaseTest {

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Before
    public void seedData() throws InitializationException
    {
        initialize();

        AllAttributeForFetchSequenceGen entity = new AllAttributeForFetchSequenceGen();
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

        entity = new AllAttributeForFetchSequenceGen();
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

        entity = new AllAttributeForFetchSequenceGen();
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

        entity = new AllAttributeForFetchSequenceGen();
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

        entity = new AllAttributeForFetchSequenceGen();
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

        entity = new AllAttributeForFetchSequenceGen();
        save(entity);
        find(entity);

        entity = new AllAttributeForFetchSequenceGen();
        save(entity);
        find(entity);
    }

    @Test
    public void testIdentifierRange() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 2);
        QueryCriteria criteriaList2 = new QueryCriteria("id", QueryCriteriaOperator.LESS_THAN, 4);
        List<AllAttributeForFetchSequenceGen> results = manager.list(AllAttributeForFetchSequenceGen.class, criteriaList.and(criteriaList2));
        Assert.assertEquals(1, results.size());
    }

    @Test
    public void testIdentifierRangeLTEqual() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 2);
        QueryCriteria criteriaList2 = new QueryCriteria("id", QueryCriteriaOperator.LESS_THAN_EQUAL, 4);
        List<AllAttributeForFetchSequenceGen> results = manager.list(AllAttributeForFetchSequenceGen.class, criteriaList.and(criteriaList2));
        Assert.assertEquals(2, results.size());
    }

    @Test
    public void testIdentifierRangeEqual() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteriaList = new QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN_EQUAL, 2);
        QueryCriteria criteriaList2 = new QueryCriteria("id", QueryCriteriaOperator.LESS_THAN_EQUAL, 4);
        List<AllAttributeForFetchSequenceGen> results = manager.list(AllAttributeForFetchSequenceGen.class, criteriaList.and(criteriaList2));
        Assert.assertEquals(3, results.size());
    }
}
