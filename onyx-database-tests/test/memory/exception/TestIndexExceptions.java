package memory.exception;

import category.EmbeddedDatabaseTests;
import category.InMemoryDatabaseTests;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.InvalidIndexException;
import entities.exception.InvalidIndexTypeEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category({ InMemoryDatabaseTests.class })
public class TestIndexExceptions extends memory.base.BaseTest
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

    @Test(expected = InvalidIndexException.class)
    public void testMissingAttribute() throws EntityException
    {
        EntityDescriptor descriptor = new EntityDescriptor(entities.exception.InvalidIndexException.class, context);
    }

    @Test(expected = InvalidIndexException.class)
    public void testInvalidIndexType() throws EntityException
    {
        EntityDescriptor descriptor = new EntityDescriptor(InvalidIndexTypeEntity.class, context);
    }


}
