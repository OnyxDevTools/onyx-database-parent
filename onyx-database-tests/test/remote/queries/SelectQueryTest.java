package remote.queries;

import category.RemoteServerTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;
import entities.AllAttributeForFetch;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemotePrePopulatedBaseTest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by timothy.osborn on 1/10/15.
 */
@Category({ RemoteServerTests.class })
public class SelectQueryTest extends RemotePrePopulatedBaseTest
{

    @Test
    public void testExecuteSelectFields() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        Query query = new Query(AllAttributeForFetch.class, Arrays.asList("longValue", "intPrimitive"), criteria);
        List results = manager.executeQuery(query);
        Assert.assertNotNull(results);
        Assert.assertTrue(results.size() == 5);
        Assert.assertTrue(results.get(0) instanceof Map);
        Assert.assertTrue((Long)((Map) results.get(0)).get("longValue") > 0);
    }

    @Test
    public void testSelectOnlyOne() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        Query query = new Query(AllAttributeForFetch.class, Arrays.asList("longValue", "intPrimitive"), criteria);
        query.setFirstRow(0);
        query.setMaxResults(1);
        List results = manager.executeQuery(query);

        Assert.assertNotNull(results);
        Assert.assertTrue(results.size() == 1);
        Assert.assertTrue(results.get(0) instanceof Map);
        Assert.assertTrue((Long)((Map) results.get(0)).get("longValue") > 0);
    }

    @Test
    public void testSelectTwoOrderBy() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        Query query = new Query(AllAttributeForFetch.class, Arrays.asList("stringValue","longValue", "intPrimitive"), criteria);
        query.setFirstRow(2);
        query.setMaxResults(2);
        query.setQueryOrders(Arrays.asList(new QueryOrder("stringValue"), new QueryOrder("intPrimitive")));

        List results = manager.executeQuery(query);

        Assert.assertNotNull(results);
//        Assert.assertTrue(results.size() == 2);
        Assert.assertTrue(results.get(0) instanceof Map);
        Assert.assertTrue((int)((Map) results.get(0)).get("intPrimitive") == 4);
        Assert.assertTrue((int)((Map) results.get(1)).get("intPrimitive") == 6);
    }

    @Test
    public void testNoSelect() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        Query query = new Query(AllAttributeForFetch.class, criteria);
        query.setFirstRow(0);
        query.setMaxResults(2);
        query.setQueryOrders(Arrays.asList(new QueryOrder("stringValue"), new QueryOrder("intPrimitive")));

        List results = manager.executeQuery(query);

        Assert.assertNotNull(results);
        Assert.assertTrue(results.size() == 2);
        Assert.assertTrue(results.get(0) instanceof AllAttributeForFetch);
        Assert.assertTrue(((AllAttributeForFetch)results.get(0)).id.equals("FIRST ONE"));
        Assert.assertTrue(((AllAttributeForFetch)results.get(1)).id.equals("FIRST ONE2") || ((AllAttributeForFetch)results.get(1)).id.equals("FIRST ONE1"));
    }

    @Test
    public void testSelectRelationship() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some")
                                              .and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA");
        Query query = new Query(AllAttributeForFetch.class, criteria);
        query.setFirstRow(0);
        query.setMaxResults(2);
        query.setQueryOrders(Arrays.asList(new QueryOrder("child.someOtherField"), new QueryOrder("intPrimitive")));
        query.setSelections(Arrays.asList("longValue", "intPrimitive", "child.someOtherField"));

        List results = manager.executeQuery(query);

        Assert.assertNotNull(results);
        Assert.assertTrue(results.size() == 1);
        Assert.assertTrue(results.get(0) instanceof Map);
    }

    @Test
    public void testSelectRelationshipMultiResult() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("id", QueryCriteriaOperator.STARTS_WITH, "FIRST ONE").and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA");
        Query query = new Query(AllAttributeForFetch.class, criteria);
        query.setFirstRow(0);
        query.setMaxResults(2);
        query.setQueryOrders(Arrays.asList(new QueryOrder("child.someOtherField"), new QueryOrder("intPrimitive")));
        query.setSelections(Arrays.asList("id", "longValue", "intPrimitive", "child.someOtherField"));

        List results = manager.executeQuery(query);

        Assert.assertNotNull(results);
        Assert.assertTrue(results.size() == 2);
        Assert.assertTrue(results.get(0) instanceof Map);
        Assert.assertTrue(((Map)results.get(0)).get("id").equals("FIRST ONE"));
        Assert.assertTrue(((Map)results.get(1)).get("id").equals("FIRST ONE7"));
    }
}
