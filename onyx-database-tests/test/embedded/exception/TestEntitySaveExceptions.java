package embedded.exception;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.*;
import embedded.base.BaseTest;
import entities.EntityWithNoInterface;
import entities.SimpleEntity;
import entities.exception.*;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by timothy.osborn on 12/14/14.
 */
@Category({ EmbeddedDatabaseTests.class })
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
    public void testNoEntitySave() throws EntityException
    {
        NoEntityAnnotationClass entity = new NoEntityAnnotationClass();
        entity.id = "Hiya";

        manager.saveEntity(entity);
    }

    @Test
    public void testNoIntefaceButHasExtension() throws EntityException
    {
        EntityWithNoInterface entityWithNoInterface = new EntityWithNoInterface();
        manager.saveEntity(entityWithNoInterface);
    }

    @Test(expected = InvalidIdentifierException.class)
    public void testNoIDEntity() throws EntityException
    {
        NoIdEntity entity = new NoIdEntity();
        entity.attr = 3;
        manager.saveEntity(entity);
    }

    @Test(expected = InvalidIdentifierException.class)
    public void testInvalidIDEntity() throws EntityException
    {
        InvalidIDEntity entity = new InvalidIDEntity();
        entity.id = 23.3;
        manager.saveEntity(entity);
    }

    @Test(expected = InvalidIdentifierException.class)
    public void testInvalidGenerator() throws EntityException
    {
        InvalidIDGeneratorEntity entity = new InvalidIDGeneratorEntity();
        entity.id = "ASDF";
        manager.saveEntity(entity);
    }

    @Test(expected = EntityClassNotFoundException.class)
    public void testNoInterfaceException() throws EntityException
    {
        EntityNoIPersistedEntity entity = new EntityNoIPersistedEntity();
        entity.id = "ASDF";
        List entities = new ArrayList<>();
        entities.add(entity);

        manager.saveEntities(entities);
    }

    @Test(expected = EntityTypeMatchException.class)
    public void testInvalidAttributeType() throws EntityException
    {
        InvalidAttributeTypeEntity entity = new InvalidAttributeTypeEntity();
        entity.id = "ASDF";
        manager.saveEntity(entity);
    }

    @Test
    public void testInvalidFindById() throws EntityException{
        //Save entity
        SimpleEntity entity = new SimpleEntity();
        entity.setSimpleId("1");
        entity.setName("Chris");
        manager.saveEntity(entity);
        //Retreive entity using findById method using the wrong data type for id
        SimpleEntity savedEntity = (SimpleEntity) manager.findById(entity.getClass(), 1);
        Assert.assertNull(savedEntity);
    }

}
