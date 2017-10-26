package remote.callbacks;

import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import entities.EntityWithCallbacks;
import entities.SequencedEntityWithCallbacks;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import remote.base.RemoteBaseTest;

import java.io.IOException;

/**
 * Created by Chris Osborn on 12/29/2014.
 */
public class EntityWithCallbacksTest extends RemoteBaseTest {


    @Before
    public void before() throws InitializationException, InterruptedException
    {
        initialize();
    }

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Test
    public void testPrePersistCallbacksForHashIndex() throws OnyxException {
        //Create new entity
        EntityWithCallbacks entity = new EntityWithCallbacks();
        entity.setId("1");
        entity.setName("INSERT");
        manager.saveEntity(entity);

        //retrieve the entity
        EntityWithCallbacks savedEntity = new EntityWithCallbacks();
        savedEntity.setId("1");
        manager.find(savedEntity);

        //Update the entity
        savedEntity.setName(savedEntity.getName() + "&UPDATE");
        manager.saveEntity(savedEntity);

        //Assert
        Assert.assertTrue(savedEntity.getName().contains("INSERT"));
        Assert.assertTrue(savedEntity.getName().contains("UPDATE"));
        Assert.assertTrue(savedEntity.getName().contains("_PrePersist"));
        Assert.assertTrue(savedEntity.getName().contains("_PreInsert"));
        Assert.assertTrue(savedEntity.getName().contains("_PreUpdate"));
    }

    @Test
    public void testPrePersistCallbacksForSequenceIndex() throws OnyxException {
        //Create new entity
        SequencedEntityWithCallbacks entity = new SequencedEntityWithCallbacks();
        entity.setName("INSERT");
        entity = (SequencedEntityWithCallbacks) manager.saveEntity(entity);

        //retrieve the entity
        SequencedEntityWithCallbacks savedEntity = (SequencedEntityWithCallbacks) manager.find(entity);

        //Update the entity
        savedEntity.setName(savedEntity.getName() + "&UPDATE");
        manager.saveEntity(savedEntity);

        //Assert
        Assert.assertTrue(savedEntity.getName().contains("INSERT"));
        Assert.assertTrue(savedEntity.getName().contains("UPDATE"));
        Assert.assertTrue(savedEntity.getName().contains("_PrePersist"));
        Assert.assertTrue(savedEntity.getName().contains("_PreInsert"));
        Assert.assertTrue(savedEntity.getName().contains("_PreUpdate"));
    }

    @Test
    public void testPostPersistCallbacksForHashIndex() throws OnyxException {
        //Create new entity
        EntityWithCallbacks entity = new EntityWithCallbacks();
        entity.setId("1");
        entity.setName("INSERT");

        try
        {
            manager.deleteEntity(entity);

        } catch (OnyxException e)
        { }

        manager.saveEntity(entity);

        Assert.assertTrue(entity.getName().contains("_PostInsert"));

        //retrieve the entity
        EntityWithCallbacks savedEntity = new EntityWithCallbacks();
        savedEntity.setId("1");
        manager.find(savedEntity);

        //Update the entity
        savedEntity.setName(savedEntity.getName() + "&UPDATE");
        manager.saveEntity(savedEntity);

        //Assert
        Assert.assertTrue(savedEntity.getName().contains("INSERT"));
        Assert.assertTrue(savedEntity.getName().contains("UPDATE"));
        Assert.assertTrue(savedEntity.getName().contains("_PostPersist"));
        Assert.assertTrue(savedEntity.getName().contains("_PostUpdate"));
    }

    @Test
    public void testPostPersistCallbacksForSequenceIndex() throws OnyxException {
        //Create new entity
        SequencedEntityWithCallbacks entity = new SequencedEntityWithCallbacks();
        entity.setName("INSERT");
        entity = (SequencedEntityWithCallbacks) manager.saveEntity(entity);

        Assert.assertTrue(entity.getName().contains("_PostInsert"));

        //retrieve the entity
        SequencedEntityWithCallbacks savedEntity = (SequencedEntityWithCallbacks) manager.find(entity);

        //Update the entity
        savedEntity.setName(savedEntity.getName() + "&UPDATE");
        manager.saveEntity(savedEntity);

        //Assert
        Assert.assertTrue(savedEntity.getName().contains("INSERT"));
        Assert.assertTrue(savedEntity.getName().contains("UPDATE"));
        Assert.assertTrue(savedEntity.getName().contains("_PostPersist"));
        Assert.assertTrue(savedEntity.getName().contains("_PostUpdate"));
    }

}
