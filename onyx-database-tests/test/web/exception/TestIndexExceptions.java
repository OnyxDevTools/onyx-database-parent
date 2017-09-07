package web.exception;

import category.WebServerTests;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.InvalidIndexException;
import entities.exception.InvalidIndexTypeEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import web.base.BaseTest;

import java.io.IOException;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category({ WebServerTests.class })
public class TestIndexExceptions extends BaseTest
{

    @Before
    public void before() throws InitializationException
    {
        initialize();
    }

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Test(expected = InvalidIndexException.class)
    public void testMissingAttribute() throws OnyxException
    {
        EntityDescriptor descriptor = new EntityDescriptor(entities.exception.InvalidIndexException.class);
    }

    @Test(expected = InvalidIndexException.class)
    public void testInvalidIndexType() throws OnyxException
    {
        EntityDescriptor descriptor = new EntityDescriptor(InvalidIndexTypeEntity.class);
    }


}
