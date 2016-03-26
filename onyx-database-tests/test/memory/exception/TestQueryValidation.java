package memory.exception;


import category.EmbeddedDatabaseTests;
import category.InMemoryDatabaseTests;
import com.onyx.exception.*;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.update.AttributeUpdate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import entities.ValidationEntity;
import org.junit.experimental.categories.Category;

import java.io.IOException;

/**
 * Created by timothy.osborn on 1/21/15.
 * Updated by Chris Osborn on 5/15/15
 */
@Category({ InMemoryDatabaseTests.class })
public class TestQueryValidation extends memory.base.BaseTest
{

    @Before
    public void before() throws InitializationException
    {
        initialize();
    }

    @After
    public void after() throws EntityException, IOException
    {
        shutdown();
    }

    @Test(expected = AttributeNonNullException.class)
    public void testNullValue() throws EntityException
    {
        Query updateQuery = new Query(ValidationEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3l), new AttributeUpdate<>("requiredString", QueryCriteria.NULL_STRING_VALUE));
        manager.executeUpdate(updateQuery);
    }


    @Test(expected = AttributeMissingException.class)
    public void testAttributeMissing() throws EntityException
    {
        Query updateQuery = new Query(ValidationEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3l), new AttributeUpdate<>("booger", QueryCriteria.NULL_STRING_VALUE));
        manager.executeUpdate(updateQuery);
    }

    @Test(expected = AttributeSizeException.class)
    public void testAttributeSizeException() throws EntityException
    {
        Query updateQuery = new Query(ValidationEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3l), new AttributeUpdate<>("maxSizeString", "12345678901"));
        manager.executeUpdate(updateQuery);

    }

    @Test(expected = AttributeUpdateException.class)
    public void testUpdateIdentifier() throws EntityException
    {
        Query updateQuery = new Query(ValidationEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3l), new AttributeUpdate<>("id", 5l));
        manager.executeUpdate(updateQuery);
    }

    @Test(expected = AttributeTypeMismatchException.class)
    public void testTypeMisMatchException() throws EntityException
    {
        Query updateQuery = new Query(ValidationEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 3l), new AttributeUpdate<>("requiredString", 5l));
        manager.executeUpdate(updateQuery);
    }
    
    @Test(expected = EntityException.class)
    public void testMissingEntityTypeException() throws EntityException
    {
        QueryCriteria criteria = new QueryCriteria("name", QueryCriteriaOperator.NOT_EQUAL, QueryCriteria.NULL_STRING_VALUE);
        Query query = new Query();
        // query.setEntityType(SystemEntity.class); //should throw error because this line is missing
        query.setCriteria(criteria);
        manager.executeQuery(query);
    }

}
