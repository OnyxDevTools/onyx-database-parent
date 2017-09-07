package embedded.exception;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.*;
import embedded.base.BaseTest;
import entities.ValidateRequiredIDEntity;
import entities.ValidationEntity;
import entities.exception.TestValidExtendAbstract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;

/**
 * Created by timothy.osborn on 1/21/15.
 */
@Category({ EmbeddedDatabaseTests.class })
public class TestEntityValidation extends BaseTest
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

    @Test
    public void testValidObjectAsExtendingAbstract() throws OnyxException
    {
        TestValidExtendAbstract obj = new TestValidExtendAbstract();
        manager.saveEntity(obj);

        manager.find(obj);
    }

}
