package embedded.delete;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.NoResultsException;
import embedded.base.BaseTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import entities.AllAttributeEntity;
import entities.identifiers.IntegerIdentifierEntity;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Date;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category({ EmbeddedDatabaseTests.class })
public class DeleteHashIndexEntityTest extends BaseTest
{
    @Before
    public void before() throws InitializationException, EntityException
    {
        initialize();
    }

    @After
    public void after() throws EntityException, IOException
    {
        shutdown();
    }

    @Test
    public void testDeleteEntity() throws EntityException {
        AllAttributeEntity entity = new AllAttributeEntity();
        entity.id = "dc5cholqdu5vha5bb6ned8ASDF";
        entity.booleanValue = false;
        entity.doubleValue = 234.3;
        entity.dateValue = new Date();
        entity.stringValue = "Hiya";
        save(entity);

        /*manager.deleteEntity(entity);

        boolean pass = false;
        try {
            manager.find(entity);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException)
            {
                pass = true;
            }
        }
        Assert.assertTrue(pass);*/
    }

    @Test
    public void testDeleteHashAndReSave()
    {
        IntegerIdentifierEntity entity = new IntegerIdentifierEntity();
        entity.identifier = 1;
        save(entity);
        find(entity);
        delete(entity);
        boolean pass = false;
        try {
            entity = (IntegerIdentifierEntity)manager.find(entity);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException)
            {
                pass = true;
            }
        }
        Assert.assertTrue(pass);

        entity = new IntegerIdentifierEntity();
        entity.identifier = 1;
        save(entity);

        try {
            manager.find(entity);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException)
            {
                pass = false;
            }
        }

        Assert.assertTrue(pass);
    }

    @Test
    public void testDeleteRoot()
    {
        IntegerIdentifierEntity entity1 = new IntegerIdentifierEntity();
        entity1.identifier = 1;
        save(entity1);

        IntegerIdentifierEntity entity2 = new IntegerIdentifierEntity();
        entity2.identifier = 2;
        save(entity2);

        IntegerIdentifierEntity entity3 = new IntegerIdentifierEntity();
        entity3.identifier = -1;
        save(entity3);

        IntegerIdentifierEntity entity4 = new IntegerIdentifierEntity();
        entity4.identifier = 8;
        save(entity4);

        IntegerIdentifierEntity entity5 = new IntegerIdentifierEntity();
        entity5.identifier = 6;
        save(entity5);

        IntegerIdentifierEntity entity6 = new IntegerIdentifierEntity();
        entity6.identifier = 10;
        save(entity6);

        delete(entity1);
        find(entity2);
        find(entity3);
        find(entity4);
        find(entity5);
        find(entity6);

        boolean pass = false;
        try {
            manager.find(entity1);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException)
            {
                pass = true;
            }
        }

        Assert.assertTrue(pass);
    }

    @Test
    public void testDeleteParent()
    {
        IntegerIdentifierEntity entity1 = new IntegerIdentifierEntity();
        entity1.identifier = 1;
        save(entity1);

        IntegerIdentifierEntity entity2 = new IntegerIdentifierEntity();
        entity2.identifier = 2;
        save(entity2);

        IntegerIdentifierEntity entity3 = new IntegerIdentifierEntity();
        entity3.identifier = -1;
        save(entity3);

        IntegerIdentifierEntity entity4 = new IntegerIdentifierEntity();
        entity4.identifier = 8;
        save(entity4);

        IntegerIdentifierEntity entity5 = new IntegerIdentifierEntity();
        entity5.identifier = 6;
        save(entity5);

        IntegerIdentifierEntity entity6 = new IntegerIdentifierEntity();
        entity6.identifier = 10;
        save(entity6);

        delete(entity5);
        find(entity1);
        find(entity2);
        find(entity3);
        find(entity4);
        find(entity6);

        boolean pass = false;
        try {
            manager.find(entity5);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException)
            {
                pass = true;
            }
        }

        Assert.assertTrue(pass);
    }

    @Test
    public void testDeleteLeft()
    {
        IntegerIdentifierEntity entity1 = new IntegerIdentifierEntity();
        entity1.identifier = 1;
        save(entity1);

        IntegerIdentifierEntity entity2 = new IntegerIdentifierEntity();
        entity2.identifier = 2;
        save(entity2);

        IntegerIdentifierEntity entity3 = new IntegerIdentifierEntity();
        entity3.identifier = 4;
        save(entity3);

        IntegerIdentifierEntity entity7 = new IntegerIdentifierEntity();
        entity7.identifier = 3;
        save(entity7);

        IntegerIdentifierEntity entity4 = new IntegerIdentifierEntity();
        entity4.identifier = 8;
        save(entity4);

        IntegerIdentifierEntity entity5 = new IntegerIdentifierEntity();
        entity5.identifier = 6;
        save(entity5);

        IntegerIdentifierEntity entity6 = new IntegerIdentifierEntity();
        entity6.identifier = 10;
        save(entity6);


        delete(entity3);
        find(entity1);
        find(entity2);
        find(entity4);
        find(entity5);
        find(entity6);
        find(entity7);

        boolean pass = false;
        try {
            manager.find(entity3);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException)
            {
                pass = true;
            }
        }

        Assert.assertTrue(pass);
    }

    @Test
    public void testDeleteRight()
    {
        IntegerIdentifierEntity entity1 = new IntegerIdentifierEntity();
        entity1.identifier = 1;
        save(entity1);

        IntegerIdentifierEntity entity2 = new IntegerIdentifierEntity();
        entity2.identifier = 2;
        save(entity2);


        IntegerIdentifierEntity entity4 = new IntegerIdentifierEntity();
        entity4.identifier = 8;
        save(entity4);

        IntegerIdentifierEntity entity5 = new IntegerIdentifierEntity();
        entity5.identifier = 6;
        save(entity5);

        IntegerIdentifierEntity entity6 = new IntegerIdentifierEntity();
        entity6.identifier = 10;
        save(entity6);

        IntegerIdentifierEntity entity8 = new IntegerIdentifierEntity();
        entity8.identifier = 11;
        save(entity8);


        IntegerIdentifierEntity entity3 = new IntegerIdentifierEntity();
        entity3.identifier = 4;
        save(entity3);

        IntegerIdentifierEntity entity7 = new IntegerIdentifierEntity();
        entity7.identifier = 3;
        save(entity7);

        delete(entity6);
        find(entity1);
        find(entity2);
        find(entity3);
        find(entity4);
        find(entity7);
        find(entity8);

        boolean pass = false;
        try {
            manager.find(entity6);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException)
            {
                pass = true;
            }
        }

        Assert.assertTrue(pass);
    }

}
