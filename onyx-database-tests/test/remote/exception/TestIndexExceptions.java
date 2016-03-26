package remote.exception;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.InvalidIndexException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;
import entities.exception.InvalidIndexTypeEntity;

import java.io.IOException;
import category.RemoteServerTests;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category({ RemoteServerTests.class })
public class TestIndexExceptions extends RemoteBaseTest
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
