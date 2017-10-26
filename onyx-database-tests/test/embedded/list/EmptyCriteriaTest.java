package embedded.list;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import embedded.base.PrePopulatedDatabaseTest;
import entities.AllAttributeForFetch;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

/**
 * Created by Tim Osborn on 5/29/16.
 */
@Category({ EmbeddedDatabaseTests.class })
public class EmptyCriteriaTest extends PrePopulatedDatabaseTest
{
    /**
     * The purpose of this test is to verify that the empty criteria gives results and lists all of the entities of a certain
     * type
     * @throws OnyxException Should not happen
     */
    @Test
    public void testEmptyCriteria() throws OnyxException
    {
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class);
        Assert.assertEquals(9, results.size());
    }

    /**
     * This test validates that the not null criteria operator is valid to use against a primitive type.
     *
     * @throws OnyxException Generic exception.  This should not happen
     */
    @Test
    public void testEmptyCriteriaOnInt() throws OnyxException
    {
        QueryCriteria criteria = new QueryCriteria("intPrimitive", QueryCriteriaOperator.NOT_NULL);
        List<AllAttributeForFetch> results = manager.list(AllAttributeForFetch.class, criteria);
        Assert.assertEquals(9, results.size());
    }
}
