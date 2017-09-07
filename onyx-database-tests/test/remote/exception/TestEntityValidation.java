package remote.exception;

import category.RemoteServerTests;
import com.onyx.exception.*;
import entities.ValidateRequiredIDEntity;
import entities.ValidationEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

import java.io.IOException;

/**
 * Created by timothy.osborn on 1/21/15.
 */
@Category({ RemoteServerTests.class })
public class TestEntityValidation extends RemoteBaseTest
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

    @Test(expected = AttributeNonNullException.class)
    public void testNullValue() throws OnyxException
    {
        ValidationEntity validationEntity = new ValidationEntity();
        validationEntity.id = 3l;
        manager.saveEntity(validationEntity);
    }

    @Test(expected = AttributeSizeException.class)
    public void testAttributeSizeException() throws OnyxException
    {
        ValidationEntity validationEntity = new ValidationEntity();
        validationEntity.id = 3l;
        validationEntity.requiredString =  "ASFD";
        validationEntity.maxSizeString = "ASD1234569a";
        manager.saveEntity(validationEntity);
    }

    @Test
    public void testValidAttributeSizeException() throws OnyxException
    {
        ValidationEntity validationEntity = new ValidationEntity();
        validationEntity.id = 3l;
        validationEntity.requiredString =  "ASFD";
        validationEntity.maxSizeString = "ASD1234569";
        manager.saveEntity(validationEntity);
    }

    @Test(expected = IdentifierRequiredException.class)
    public void testRequiredIDException() throws OnyxException
    {
        ValidateRequiredIDEntity validationEntity = new ValidateRequiredIDEntity();
        validationEntity.requiredString =  "ASFD";
        validationEntity.maxSizeString = "ASD1234569";
        manager.saveEntity(validationEntity);
    }

}
