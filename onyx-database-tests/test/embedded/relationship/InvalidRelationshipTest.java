package embedded.relationship;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.OnyxException;
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
 * Created by Tim Osborn on 5/29/16.
 */
@Category({ EmbeddedDatabaseTests.class })
public class InvalidRelationshipTest extends BaseTest
{

    @Before
    public void before() throws OnyxException
    {
        initialize();
    }

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Test(expected = InvalidRelationshipTypeException.class)
    public void testInvalidOneToOneWithListType() throws OnyxException
    {
        HasInvalidToOne myInvalidEntity = new HasInvalidToOne();
        myInvalidEntity.identifier = "INVALIDONE";
        manager.saveEntity(myInvalidEntity);
    }

    @Test(expected = InvalidRelationshipTypeException.class)
    public void testInvalidOneToManyWithNonListType() throws OnyxException
    {
        HasInvalidToMany myInvalidEntity = new HasInvalidToMany();
        myInvalidEntity.identifier = "INVALIDONE";
        manager.saveEntity(myInvalidEntity);
    }
}
