package memory.partition;

import category.InMemoryDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.NoResultsException;
import entities.partition.*;
import junit.framework.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import java.util.ArrayList;

/**
 * Created by timothy.osborn on 2/12/15.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ InMemoryDatabaseTests.class })
public class RelationshipPartitionTest extends memory.partition.BasePartitionTest
{

    @Test
    public void testSaveEntityWithRelationshipToOne() throws EntityException
    {
        ToOnePartitionEntityParent parent = new ToOnePartitionEntityParent();
        parent.id = 4l;
        parent.partitionId = 20l;

        parent.child = new ToOnePartitionEntityChild();
        parent.child.id = 5l;

        save(parent.child);
        save(parent);

        ToOnePartitionEntityParent parentToFind = new ToOnePartitionEntityParent();
        parentToFind.id = 4l;
        parentToFind.partitionId = 20l;

        find(parentToFind);

        Assert.assertNotNull(parentToFind.child);
        Assert.assertTrue(parentToFind.child.id == parent.child.id);


        ToOnePartitionEntityChild childToFind = new ToOnePartitionEntityChild();
        childToFind.id = 5l;

        find(childToFind);

        Assert.assertNotNull(childToFind.parent);
        Assert.assertTrue(childToFind.parent.id == parent.id);
    }

    @Test(expected = NoResultsException.class)
    public void testDeleteEntityWithRelationshipToOne() throws EntityException
    {
        testSaveEntityWithRelationshipToOne();

        ToOnePartitionEntityParent parent = new ToOnePartitionEntityParent();
        parent.id = 4l;
        parent.partitionId = 20l;

        find(parent);

        delete(parent);

        manager.find(parent.child);
    }

    @Test(expected = NoResultsException.class)
    public void testUpdateEntityWithRelationshipToOne() throws EntityException
    {
        testSaveEntityWithRelationshipToOne();
        testSaveEntityWithRelationshipToOne();

        ToOnePartitionEntityParent parent = new ToOnePartitionEntityParent();
        parent.id = 4l;
        parent.partitionId = 20l;

        find(parent);

        parent.child.partitionId = 34l;
        save(parent.child);

        find(parent);

        org.junit.Assert.assertNotNull(parent.child);
        Assert.assertTrue(parent.child.partitionId == 34l);

        ToOnePartitionEntityChild child = new ToOnePartitionEntityChild();
        child.id = parent.child.id;
        child.partitionId = 34l;

        manager.find(child);
    }

    @Test
    public void testSaveEntityAsRelationshipOneToMany() throws EntityException
    {
        OneToManyPartitionEntity parent = new OneToManyPartitionEntity();
        parent.id = 23l;
        parent.partitionId = 3l;
        parent.child = new ManyToOnePartitionEntity();
        parent.child.id = 32l;

        save(parent);

        parent = new OneToManyPartitionEntity();
        parent.id = 23l;
        parent.partitionId = 3l;

        find(parent);

        Assert.assertNotNull(parent.child);
        Assert.assertTrue(parent.child.id == 32l);

        find(parent.child);

        org.junit.Assert.assertTrue(parent.child.parents.size() == 1);
        org.junit.Assert.assertTrue(parent.child.parents.get(0).id == 23l);
    }

    @Test
    public void testDeleteEntityAsRelationshipOneToMany() throws EntityException
    {
        OneToManyPartitionEntity parent = new OneToManyPartitionEntity();
        parent.id = 23l;
        parent.partitionId = 3l;
        parent.child = new ManyToOnePartitionEntity();
        parent.child.id = 32l;

        save(parent);

        parent = new OneToManyPartitionEntity();
        parent.id = 23l;
        parent.partitionId = 3l;

        delete(parent);

        parent.child = new ManyToOnePartitionEntity();
        parent.child.id = 32l;

        find(parent.child);

        org.junit.Assert.assertTrue(parent.child.parents.size() == 0);

        boolean failedToFind = false;
        try
        {
            manager.find(parent);
        }
        catch (EntityException e)
        {
            failedToFind = true;
        }

        Assert.assertTrue(failedToFind);
    }

    @Test
    public void testUpdateEntityAsRelationshipOneToMany() throws EntityException
    {
        OneToManyPartitionEntity parent = new OneToManyPartitionEntity();
        parent.id = 23l;
        parent.partitionId = 3l;
        parent.child = new ManyToOnePartitionEntity();
        parent.child.id = 32l;

        save(parent);

        parent = new OneToManyPartitionEntity();
        parent.id = 23l;
        parent.partitionId = 3l;
        parent.child = new ManyToOnePartitionEntity();
        parent.child.id = 32l;

        find(parent.child);

        parent.child.partitionId = 2l;

        save(parent.child);

        find(parent);


        Assert.assertNotNull(parent.child);
        Assert.assertTrue(parent.child.id == 32l);
        Assert.assertTrue(parent.child.partitionId == 2l);

        find(parent.child);

        org.junit.Assert.assertTrue(parent.child.parents.size() == 1);
        org.junit.Assert.assertTrue(parent.child.parents.get(0).id == 23l);
        org.junit.Assert.assertTrue(parent.child.parents.get(0).partitionId == 3l);

    }

    @Test
    public void testSaveEntityWithRelationshipManyToMany() throws EntityException
    {
        ToManyPartitionEntityParent parent = new ToManyPartitionEntityParent();
        parent.id = 23l;
        parent.partitionId = 3l;
        parent.child = new ArrayList();

        ToManyPartitionEntityChild child = new ToManyPartitionEntityChild();
        parent.child.add(child);

        child.id = 33l;

        save(parent);

        parent = new ToManyPartitionEntityParent();
        parent.id = 23l;
        parent.partitionId = 3l;

        find(parent);

        Assert.assertNotNull(parent.child);
        Assert.assertTrue(parent.child.size() == 1);

        child = (ToManyPartitionEntityChild)find(parent.child.get(0));

        org.junit.Assert.assertTrue(child.parent.size() == 1);
        org.junit.Assert.assertTrue(child.parent.get(0).id == 23l);
    }

    @Test
    public void testDeleteEntityWithRelationshipManyToMany() throws EntityException
    {
        ToManyPartitionEntityParent parent = new ToManyPartitionEntityParent();
        parent.id = 23l;
        parent.partitionId = 3l;
        parent.child = new ArrayList();

        ToManyPartitionEntityChild child = new ToManyPartitionEntityChild();
        parent.child.add(child);

        child.id = 32l;

        save(parent);

        parent = new ToManyPartitionEntityParent();
        parent.id = 23l;
        parent.partitionId = 3l;

        find(parent);

        Assert.assertNotNull(parent.child);
        Assert.assertTrue(parent.child.size() == 1);

        child = (ToManyPartitionEntityChild)manager.find(parent.child.get(0));

        org.junit.Assert.assertTrue(child.parent.size() == 1);
        org.junit.Assert.assertTrue(child.parent.get(0).id == 23l);


        parent.child.clear();
        save(parent);


        parent = new ToManyPartitionEntityParent();
        parent.id = 23l;
        parent.partitionId = 3l;

        find(parent);

        Assert.assertNotNull(parent.child);
        Assert.assertTrue(parent.child.size() == 0);

        child = new ToManyPartitionEntityChild();
        child.id = 32l;
        find(child);

        org.junit.Assert.assertTrue(child.parent.size() == 0);
    }

    @Test
    public void testUpdateEntityWithRelationshipManyToMany() throws EntityException
    {
        ToManyPartitionEntityParent parent = new ToManyPartitionEntityParent();
        parent.id = 23l;
        parent.partitionId = 3l;
        parent.child = new ArrayList();

        ToManyPartitionEntityChild child = new ToManyPartitionEntityChild();
        parent.child.add(child);

        child.id = 34l;

        save(parent);

        parent = new ToManyPartitionEntityParent();
        parent.id = 23l;
        parent.partitionId = 3l;

        find(parent);

        Assert.assertNotNull(parent.child);
        Assert.assertTrue(parent.child.size() == 1);

        find(parent.child.get(0));

        org.junit.Assert.assertTrue(parent.child.get(0).parent.size() == 1);
        org.junit.Assert.assertTrue(parent.child.get(0).parent.get(0).id == 23l);


        child = parent.child.get(0);
        manager.initialize(child, "parent");
        child.partitionId = 3l;
        save(child);

        parent = new ToManyPartitionEntityParent();
        parent.id = 23l;
        parent.partitionId = 3l;

        find(parent);
        initialize(parent, "child");

        Assert.assertNotNull(parent.child);
        Assert.assertTrue(parent.child.size() == 1);
        Assert.assertTrue(parent.child.get(0).partitionId == 3l);
    }

    @Test
    public void testInitializeToMany() throws EntityException
    {
        ToManyPartitionEntityParent parent = new ToManyPartitionEntityParent();
        parent.id = 23l;
        parent.partitionId = 3l;
        parent.child = new ArrayList();

        ToManyPartitionEntityChild child = new ToManyPartitionEntityChild();
        parent.child.add(child);

        child.id = 32l;

        save(parent);

        parent = new ToManyPartitionEntityParent();
        parent.id = 23l;
        parent.partitionId = 3l;

        manager.initialize(parent, "child");


        Assert.assertNotNull(parent.child);
        Assert.assertTrue(parent.child.size() == 1);
    }
}
