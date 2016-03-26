package embedded.delete;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.NoResultsException;
import com.onyx.persistence.IManagedEntity;
import embedded.base.BaseTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category({ EmbeddedDatabaseTests.class })
public class DeleteSequenceIndexEntityTest extends BaseTest
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
    public void testAddDeleteSequence() throws EntityException
    {
        ImmutableSequenceIdentifierEntityForDelete entity = new ImmutableSequenceIdentifierEntityForDelete();
        entity.correlation = 1;
        save(entity);

        Assert.assertTrue(entity.identifier > 0);
        delete(entity);

        assertFalse(manager.exists(entity));

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

        Assert.assertTrue(pass);
    }

    @Test
    public void testSequenceBatchDelete()
    {
        ImmutableSequenceIdentifierEntityForDelete entity = new ImmutableSequenceIdentifierEntityForDelete();
        entity.correlation = 1;
        save(entity);
        long id1 = entity.identifier;

        ImmutableSequenceIdentifierEntityForDelete entity2 = new ImmutableSequenceIdentifierEntityForDelete();
        entity2.correlation = 1;
        save(entity2);
        long id2 = entity2.identifier;

        ImmutableSequenceIdentifierEntityForDelete entity3 = new ImmutableSequenceIdentifierEntityForDelete();
        entity3.correlation = 1;
        save(entity3);
        long id3 = entity3.identifier;

        ImmutableSequenceIdentifierEntityForDelete entity4 = new ImmutableSequenceIdentifierEntityForDelete();
        entity4.correlation = 1;
        save(entity4);
        long id4 = entity4.identifier;

        ImmutableSequenceIdentifierEntityForDelete entity5 = new ImmutableSequenceIdentifierEntityForDelete();
        entity5.correlation = 5;
        save(entity5);
        long id5 = entity.identifier;

        ImmutableSequenceIdentifierEntityForDelete entity6 = new ImmutableSequenceIdentifierEntityForDelete();
        entity.correlation = 6;
        save(entity6);
        long id6 = entity6.identifier;

        Assert.assertTrue(entity.identifier > 0);
        Assert.assertTrue(entity6.identifier > 0);

        List entitiesToDelete = new ArrayList<>();
        //entitiesToDelete.add(entity);
        entitiesToDelete.add(entity2);
        entitiesToDelete.add(entity3);
        entitiesToDelete.add(entity4);
        entitiesToDelete.add(entity5);
        entitiesToDelete.add(entity6);


        try {
            manager.deleteEntities(entitiesToDelete);
        } catch (EntityException e) {
            fail("Failure to execute delete batch");
        }
        for (Object deletedEntity : entitiesToDelete)
        {
            boolean pass = false;
            try {
                manager.find((IManagedEntity)deletedEntity);
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

        ImmutableSequenceIdentifierEntityForDelete entity7 = new ImmutableSequenceIdentifierEntityForDelete();
        entity.correlation = 7;
        save(entity7);
        long id7 = entity7.identifier;

        find(entity7);
        find(entity);
    }

    @Test
    public void testSequenceSkip()
    {
        ImmutableSequenceIdentifierEntityForDelete entity = new ImmutableSequenceIdentifierEntityForDelete();
        entity.correlation = 1;
        save(entity);
        long id1 = entity.identifier;

        ImmutableSequenceIdentifierEntityForDelete entity2 = new ImmutableSequenceIdentifierEntityForDelete();
        entity2.correlation = 1;
        entity2.identifier = id1 + 100;
        save(entity2);
        long id2 = entity2.identifier;

        ImmutableSequenceIdentifierEntityForDelete entity3 = new ImmutableSequenceIdentifierEntityForDelete();
        entity3.correlation = 1;
        save(entity3);
        long id3 = entity3.identifier;


        Assert.assertTrue(entity.identifier > 0);
        Assert.assertTrue(entity2.identifier > entity.identifier);
//        Assert.assertTrue(entity3.identifier > entity2.identifier);
        Assert.assertTrue((entity2.identifier - entity.identifier) == 100);

        delete(entity2);

        find(entity);
        find(entity3);

        boolean pass= false;
        try
        {
            manager.find(entity2);
        } catch (EntityException e)
        {
            if(e instanceof NoResultsException)
            {
                pass = true;
            }
        }

        Assert.assertTrue(pass);

    }
}
