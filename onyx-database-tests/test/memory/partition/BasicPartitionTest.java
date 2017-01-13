package memory.partition;

import category.InMemoryDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.NoResultsException;
import entities.partition.BasicPartitionEntity;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

/**
 * Created by timothy.osborn on 2/12/15.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ InMemoryDatabaseTests.class })
public class BasicPartitionTest extends memory.partition.BasePartitionTest
{

    @Test
    public void aTestSavePartitionEntityTest() throws EntityException
    {
        BasicPartitionEntity entity = new BasicPartitionEntity();
        entity.partitionId = 2l;

        manager.saveEntity(entity);
    }

    @Test
    public void bTestFindPartitionEntityTest() throws EntityException
    {
        BasicPartitionEntity entity = new BasicPartitionEntity();
        entity.partitionId = 1l;

        manager.saveEntity(entity);

        long id = entity.id;

        BasicPartitionEntity entity2Find = new BasicPartitionEntity();
        entity2Find.id = id;
        entity2Find.partitionId = 1l;

        find(entity2Find);

        Assert.assertNotNull(entity2Find);
        Assert.assertTrue(entity2Find.id == id);
        Assert.assertTrue(entity2Find.partitionId == 1l);
    }

    @Test(expected = NoResultsException.class)
    public void cTestFindPartitionEntityByIDTest() throws EntityException
    {
        BasicPartitionEntity entity = new BasicPartitionEntity();
        entity.partitionId = 2l;

        manager.saveEntity(entity);

        long id = entity.id;

        BasicPartitionEntity entity2Find = new BasicPartitionEntity();
        entity2Find.id = id;

        manager.find(entity2Find);
    }


    @Test
    public void dTestNonUniqueEntityWithDifferentPartition() throws EntityException
    {
        BasicPartitionEntity entity = new BasicPartitionEntity();
        entity.partitionId = 1l;
        entity.id = 1l;

        manager.saveEntity(entity);

        BasicPartitionEntity entity2 = new BasicPartitionEntity();
        entity2.partitionId = 2l;
        entity2.id = 1l;

        manager.saveEntity(entity2);

        BasicPartitionEntity entity2Find = new BasicPartitionEntity();
        entity2Find.id = 1l;
        entity2Find.partitionId = 1l;

        find(entity2Find);

        Assert.assertNotNull(entity2Find);
        Assert.assertTrue(entity2Find.id == 1l);
        Assert.assertTrue(entity2Find.partitionId == 1l);

        entity2Find = new BasicPartitionEntity();
        entity2Find.id = 1l;
        entity2Find.partitionId = 2l;

        find(entity2Find);

        Assert.assertNotNull(entity2Find);
        Assert.assertTrue(entity2Find.id == 1l);
        Assert.assertTrue(entity2Find.partitionId == 2l);
    }

    @Test
    public void testDeletePartitionEntity() throws EntityException
    {
        BasicPartitionEntity entity = new BasicPartitionEntity();
        entity.partitionId = 1l;
        entity.id = 1l;

        manager.saveEntity(entity);

        BasicPartitionEntity entity2Find = new BasicPartitionEntity();
        entity2Find.id = 1l;
        entity2Find.partitionId = 1l;

        find(entity2Find);

        manager.deleteEntity(entity);

        boolean exceptionThrown = false;
        try
        {
            manager.find(entity2Find);
        }
        catch (EntityException e)
        {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);
    }


}
