package remote.queries;

import com.onyx.exception.EntityException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.update.AttributeUpdate;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemotePrePopulatedBaseTest;
import entities.AllAttributeForFetch;

import java.util.List;
import category.RemoteServerTests;

/**
 * Created by cosbor11 on 1/9/2015.
 */
@Category({ RemoteServerTests.class })
public class UpdateQueryTest extends RemotePrePopulatedBaseTest
{

    @Test
    public void testExecuteUpdateQuery() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3");
        Query query = new Query(AllAttributeForFetch.class, criteria, new AttributeUpdate("stringValue", "B"));

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 1);

        Query fetchQuery = new Query(AllAttributeForFetch.class, new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "B"));
        List<AllAttributeForFetch> listResults = manager.executeQuery(fetchQuery);

        AllAttributeForFetch res = listResults.get(0);
        res.stringValue = "Some test strin3";
        manager.saveEntity(res);

        Assert.assertTrue(listResults.size() == 1);
    }

    @Test
    public void testExecuteUpdateMultipleQuery() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        Query query = new Query(AllAttributeForFetch.class, criteria, new AttributeUpdate("stringValue", "B"));

        query.setFirstRow(2);
        query.setMaxResults(2);

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 2);

        Query fetchQuery = new Query(AllAttributeForFetch.class, new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "B"));
        List listResults = manager.executeQuery(fetchQuery);
        Assert.assertTrue(listResults.size() == 2);
    }

    @Test
    public void testExecuteUpdateMultipleFieldsQuery() throws EntityException, InstantiationException, IllegalAccessException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some");
        Query query = new Query(AllAttributeForFetch.class, criteria, new AttributeUpdate("stringValue", "B"), new AttributeUpdate("intValue", 4));

        query.setFirstRow(2);
        query.setMaxResults(2);

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 2);

        Query fetchQuery = new Query(AllAttributeForFetch.class, new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "B").and("intValue",QueryCriteriaOperator.EQUAL, 4));
        List listResults = manager.executeQuery(fetchQuery);
        Assert.assertTrue(listResults.size() == 2);
    }

}
