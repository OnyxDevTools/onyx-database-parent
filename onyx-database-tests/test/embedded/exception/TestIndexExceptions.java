package embedded.exception;

import category.EmbeddedDatabaseTests;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.InvalidIndexException;
import embedded.base.BaseTest;
import entities.exception.InvalidIndexTypeEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category({ EmbeddedDatabaseTests.class })
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
}
