package embedded.relationship;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.InvalidRelationshipTypeException;
import embedded.base.BaseTest;
import entities.relationship.HasInvalidToMany;
import entities.relationship.HasInvalidToOne;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;

/**
 * Created by tosborn1 on 5/29/16.
 */
@Category({ EmbeddedDatabaseTests.class })
public class InvalidRelationshipTest extends BaseTest
{

    @Before
    public void before() throws InitializationException, EntityException
    {
        initialize();
    }

    @After
    public void after() throws EntityException, IOException
    {
        shutdown();
    }

    @Test(expected = InvalidRelationshipTypeException.class)
    public void testInvalidOneToOneWithListType() throws EntityException
    {
        HasInvalidToOne myInvalidEntity = new HasInvalidToOne();
        myInvalidEntity.identifier = "INVALIDONE";
        manager.saveEntity(myInvalidEntity);
    }

    @Test(expected = InvalidRelationshipTypeException.class)
    public void testInvalidOneToManyWithNonListType() throws EntityException
    {
        HasInvalidToMany myInvalidEntity = new HasInvalidToMany();
        myInvalidEntity.identifier = "INVALIDONE";
        manager.saveEntity(myInvalidEntity);
    }
}
