package remote.queries;

import category.RemoteServerTests;
import com.onyx.exception.OnyxException;
import com.onyx.exception.NoResultsException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import entities.AllAttributeForFetch;
import entities.AllAttributeForFetchChild;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemotePrePopulatedBaseTest;

import java.util.List;

/**
 * Created by timothy.osborn on 1/10/15.
 */
@Category({ RemoteServerTests.class })
public class DeleteQueryTest extends RemotePrePopulatedBaseTest
{
    @Test
    public void testExecuteDeleteQuery() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3");
        Query query = new Query(AllAttributeForFetch.class, criteria);

        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 1);

        Query fetchQuery = new Query(AllAttributeForFetch.class, criteria);
        List<AllAttributeForFetch> listResults = manager.executeQuery(fetchQuery);

        Assert.assertTrue(listResults.size() == 0);
    }

    @Test
    public void testExecuteDeleteRangeQuery() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        Query query = new Query(AllAttributeForFetch.class, criteria);
        query.setFirstRow(2);
        query.setMaxResults(1);


        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 1);

        Query fetchQuery = new Query(AllAttributeForFetch.class, criteria);
        List<AllAttributeForFetch> listResults = manager.executeQuery(fetchQuery);

        Assert.assertTrue(listResults.size() == 3);
    }

    @Test(expected = NoResultsException.class)
    public void testCascadeRelationship() throws OnyxException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("intPrimitive", QueryCriteriaOperator.GREATER_THAN_EQUAL, 0).and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA");

        Query fetchQuery = new Query(AllAttributeForFetch.class, criteria);
        List<AllAttributeForFetch> listResults = manager.executeQuery(fetchQuery);
        long childId = listResults.get(0).child.id;

        Query query = new Query(AllAttributeForFetch.class, new QueryCriteria("intPrimitive", QueryCriteriaOperator.GREATER_THAN_EQUAL, 0).and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA"));

        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 2);

        Query fetchQuery2 = new Query(AllAttributeForFetch.class, new QueryCriteria("intPrimitive", QueryCriteriaOperator.GREATER_THAN_EQUAL, 0).and("child.someOtherField", QueryCriteriaOperator.STARTS_WITH, "HIYA"));
        listResults = manager.executeQuery(fetchQuery2);
        Assert.assertTrue(listResults.size() == 0);
        AllAttributeForFetchChild child = new AllAttributeForFetchChild();
        child.id = childId;
        manager.find(child);
    }
}
