package web.exception;

import category.WebServerTests;
import com.onyx.exception.*;
import entities.SimpleEntity;
import entities.exception.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import web.base.BaseTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category({ WebServerTests.class })
public class TestEntitySaveExceptions extends BaseTest {

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

    int z = 0;

    protected synchronized void increment()
    {
        z++;
    }

    protected synchronized int getZ()
    {
        return z;
    }

    @Test(expected = EntityClassNotFoundException.class)
    public void testNoEntitySave() throws OnyxException
    {
        NoEntityAnnotationClass entity = new NoEntityAnnotationClass();
        entity.id = "Hiya";

        manager.saveEntity(entity);
    }


    @Test(expected = InvalidIdentifierException.class)
    public void testNoIDEntity() throws OnyxException
    {
        NoIdEntity entity = new NoIdEntity();
        entity.attr = 3;
        manager.saveEntity(entity);
    }

    @Test(expected = InvalidIdentifierException.class)
    public void testInvalidGenerator() throws OnyxException
    {
        InvalidIDGeneratorEntity entity = new InvalidIDGeneratorEntity();
        entity.id = "ASDF";
        manager.saveEntity(entity);
    }

    @Test(expected = EntityClassNotFoundException.class)
    public void testNoInterfaceException() throws OnyxException
    {
        EntityNoIPersistedEntity entity = new EntityNoIPersistedEntity();
        entity.id = "ASDF";
        List entities = new ArrayList<>();
        entities.add(entity);

        manager.saveEntities(entities);
    }

    @Test(expected = EntityTypeMatchException.class)
    public void testInvalidAttributeType() throws OnyxException
    {
        InvalidAttributeTypeEntity entity = new InvalidAttributeTypeEntity();
        entity.id = "ASDF";
        manager.saveEntity(entity);
    }

    /**
     * This unit test no longer applies becuse there was an enhancement to accept like types.
     * @throws OnyxException
     */
    @Ignore
    @Test(expected = AttributeTypeMismatchException.class)
    public void testInvalidFindById() throws OnyxException {
        //Save entity
        SimpleEntity entity = new SimpleEntity();
        entity.setSimpleId("1");
        entity.setName("Chris");
        manager.saveEntity(entity);
        //Retreive entity using findById method using the wrong data type for id
        SimpleEntity savedEntity = (SimpleEntity) manager.findById(entity.getClass(), 1);

    }

}
