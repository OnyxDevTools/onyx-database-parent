package web.exception;

import category.WebServerTests;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.exception.*;
import entities.AllAttributeEntity;
import entities.exception.EntityToOneDoesntMatch;
import entities.exception.NoInverseEntity;
import entities.exception.OTMNoListEntity;
import entities.exception.RelationshipNoEntityType;
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
public class TestRelationshipExceptions extends BaseTest
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

    @Test(expected = InvalidRelationshipTypeException.class)
    public void testInvalidInverse() throws EntityException
    {
        EntityDescriptor descriptor = new EntityDescriptor(NoInverseEntity.class);
    }

    @Test(expected = InvalidRelationshipTypeException.class)
    public void testInvalidType() throws EntityException
    {
        EntityDescriptor descriptor = new EntityDescriptor(OTMNoListEntity.class);
    }

    @Test(expected = EntityClassNotFoundException.class)
    public void testInvalidTypeInverse() throws EntityException
    {
        EntityDescriptor descriptor = new EntityDescriptor(RelationshipNoEntityType.class);
    }

    @Test(expected = InvalidRelationshipTypeException.class)
    public void testInvalidTypeInverseNoMatch() throws EntityException
    {
        EntityDescriptor descriptor = new EntityDescriptor(EntityToOneDoesntMatch.class);
    }


    @Test(expected = RelationshipNotFoundException.class)
    public void testRelationshipNotFound() throws EntityException
    {
        AllAttributeEntity entity = new AllAttributeEntity();
        entity.id = "ZZZ";
        save(entity);

        manager.initialize(entity, "ASDFASDF");
    }

}
