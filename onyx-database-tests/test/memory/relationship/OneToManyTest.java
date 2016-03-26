package memory.relationship;

import category.EmbeddedDatabaseTests;
import category.InMemoryDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.NoResultsException;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import entities.relationship.OneToManyChild;
import entities.relationship.OneToManyParent;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by timothy.osborn on 11/3/14.
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ InMemoryDatabaseTests.class })
public class OneToManyTest extends memory.base.BaseTest
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
    public void atestOneToManyNoCascade()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "A";
        parent.correlation = 1;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "B";
        child.correlation = 2;
        save(child);

        parent.childNoCascade = new ArrayList<>();
        parent.childNoCascade.add(child);

        save(parent);

        OneToManyChild child1 = new OneToManyChild();
        child1.identifier = "B";
        find(child1);

        // Validate the child contains the relationship
        Assert.assertEquals(child1.correlation, 2);
        Assert.assertNotNull(child1.parentNoCascade);
        Assert.assertEquals(child1.parentNoCascade.identifier, parent.identifier);

        OneToManyParent parent1 = new OneToManyParent();
        parent1.identifier = "A";
        find(parent1);

        Assert.assertEquals(parent1.correlation, 1);
        Assert.assertNotNull(parent1.childNoCascade);
        Assert.assertEquals(parent1.childNoCascade.size(), 1);
        Assert.assertEquals(parent1.childNoCascade.get(0).identifier, child.identifier);

        child1 = new OneToManyChild();
        child1.identifier = "B";
        find(child1);
        child1.parentNoCascade = null;
        save(child1);

        Assert.assertNull(child1.parentNoCascade);
        Assert.assertEquals(child1.correlation, 2);

        find(parent1);

        Assert.assertEquals(parent1.childNoCascade.size(), 0);

    }

    @Test
    public void btestOneToManyCascade()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "E";
        parent.correlation = 1;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "F";
        child.correlation = 2;
        save(child);

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);

        save(parent);

        OneToManyChild child1 = new OneToManyChild();
        child1.identifier = "F";
        find(child1);

        // Validate the child contains the relationship
        Assert.assertEquals(child1.correlation, 2);
        Assert.assertNotNull(child1.parentCascade);
        Assert.assertEquals(child1.parentCascade.identifier, parent.identifier);

        OneToManyParent parent1 = new OneToManyParent();
        parent1.identifier = "E";
        find(parent1);

        Assert.assertEquals(parent1.correlation, 1);
        Assert.assertNotNull(parent1.childCascade);
        Assert.assertEquals(parent1.childCascade.size(), 1);
        Assert.assertEquals(parent1.childCascade.get(0).identifier, child.identifier);

        parent1.childCascade.remove(0);
        save(parent1);

        parent1 = new OneToManyParent();
        parent1.identifier = "E";
        find(parent1);

        Assert.assertEquals(parent1.childCascade.size(), 0);

        child1 = new OneToManyChild();
        child1.identifier = "F";

        boolean exception = false;
        try
        {
            manager.find(child1);
        } catch (EntityException e)
        {
            if(e instanceof NoResultsException)
            {
                exception = true;
            }
        }

        Assert.assertFalse(exception);
    }


    @Test
    public void ctestOneToManyCascadeInverse()
    {

        OneToManyChild tmpPar = new OneToManyChild();
        tmpPar.identifier = "ASDF";
        save(tmpPar);

        tmpPar.parentCascade = new OneToManyParent();
        tmpPar.parentCascade.identifier = "ASDFs";
        save(tmpPar);


        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "C";
        parent.correlation = 1;
        save(parent);
        find(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "D";
        child.correlation = 2;
        child.parentCascade = parent;
        save(child);
        find(child);
        //

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);

        save(parent);
        //find(parent);

        OneToManyChild child1 = new OneToManyChild();
        child1.identifier = "D";
        find(child1);

        initialize(child1, "parentCascade");
        // Validate the child contains the relationship
        Assert.assertEquals(child1.correlation, 2);
        Assert.assertNotNull(child1.parentCascade);
        Assert.assertEquals(child1.parentCascade.identifier, parent.identifier);

        OneToManyParent parent1 = new OneToManyParent();
        parent1.identifier = "C";
        find(parent1);

        Assert.assertEquals(parent1.correlation, 1);
        Assert.assertNotNull(parent1.childCascade);
        Assert.assertEquals(parent1.childCascade.size(), 1);
        Assert.assertEquals(parent1.childCascade.get(0).identifier, child.identifier);

        delete(child1);

        OneToManyParent parent2 = new OneToManyParent();
        parent2.identifier = "C";
        find(parent2);

        Assert.assertEquals(0, parent2.childCascade.size());

    }

    @Test
    public void dtestOneToManyNoCascade()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "F";
        parent.correlation = 1;
        save(parent);
        find(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "G";
        child.correlation = 2;
        parent.childNoCascade = new ArrayList<>();
        parent.childNoCascade.add(child);
        save(parent);

        // Ensure the object was not cascaded
        OneToManyParent parent2 = new OneToManyParent();
        parent2.identifier = "F";
        find(parent2);
        Assert.assertEquals(0, parent2.childNoCascade.size());
    }

    @Test
    public void etestOneToManyNoCascadeNoInverse()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "H";
        parent.correlation = 14;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "I";
        child.correlation = 22;
        save(child);

        parent.childNoInverseNoCascade = new ArrayList<>();
        parent.childNoInverseNoCascade.add(child);

        save(parent);

        OneToManyChild child1 = new OneToManyChild();
        child1.identifier = "I";
        find(child1);

        // Validate the child contains the relationship
        Assert.assertEquals(child1.correlation, 22);

        OneToManyParent parent1 = new OneToManyParent();
        parent1.identifier = "H";
        find(parent1);

        // Verify the relationship is still there
        Assert.assertEquals(parent1.correlation, 14);
        Assert.assertNotNull(parent1.childNoInverseNoCascade);
        Assert.assertEquals(parent1.childNoInverseNoCascade.size(), 1);
        Assert.assertEquals(parent1.childNoInverseNoCascade.get(0).identifier, child.identifier);

        initialize(parent1, "childNoInverseNoCascade");
        parent1.childNoInverseNoCascade.remove(0);
        save(parent1);

        // Ensure the child still loads and the parent did not wipe out the entity
        child1 = new OneToManyChild();
        child1.identifier = "I";
        find(child1);
        Assert.assertEquals(22, child1.correlation);

        // Get the parent to check relationships
        OneToManyParent parent2 = new OneToManyParent();
        parent2.identifier = "H";
        find(parent2);

        // Ensure the relationship was not removed
        Assert.assertEquals(1, parent2.childNoInverseNoCascade.size());
        Assert.assertEquals(14, parent2.correlation);
        child1.parentNoCascade = null;

    }

    @Test
    public void ftestOneToManyNoCascadeNoInverse()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "J";
        parent.correlation = 15;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "K";
        child.correlation = 23;
        save(child);

        parent.childNoInverseCascade = new ArrayList<>();
        parent.childNoInverseCascade.add(child);

        save(parent);


        OneToManyChild child1 = new OneToManyChild();
        child1.identifier = "K";
        find(child1);

        // Validate the child still exists
        Assert.assertEquals(child1.correlation, 23);

        OneToManyParent parent1 = new OneToManyParent();
        parent1.identifier = "J";
        find(parent1);

        // Verify the relationship is still there
        Assert.assertEquals(parent1.correlation, 15);
        Assert.assertNotNull(parent1.childNoInverseCascade);
        Assert.assertEquals(parent1.childNoInverseCascade.size(), 1);
        Assert.assertEquals(parent1.childNoInverseCascade.get(0).identifier, child.identifier);

        //parent1.childNoInverseNoCascade = null;
        initialize(parent1, "childNoInverseCascade");
        parent1.childNoInverseCascade.remove(0);
        save(parent1);

        // Ensure the child still loads and the parent did not wipe out the entity
        child1 = new OneToManyChild();
        child1.identifier = "K";
        find(child1);
        Assert.assertEquals(23, child1.correlation);

        // Get the parent to check relationships
        OneToManyParent parent2 = new OneToManyParent();
        parent2.identifier = "J";
        find(parent2);

        // Ensure the relationship was not removed
        Assert.assertEquals(0, parent2.childNoInverseCascade.size());
        Assert.assertEquals(15, parent2.correlation);

    }

    @Test
    public void gtestOneToManyRemoveHasMultiple()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "Z";
        parent.correlation = 30;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "Y";
        child.correlation = 31;
        //save(child);

        OneToManyChild child2 = new OneToManyChild();
        child2.identifier = "X";
        child2.correlation = 32;
        //save(child);

        parent.childNoInverseCascade = new ArrayList<>();
        parent.childNoInverseCascade.add(child);
        parent.childNoInverseCascade.add(child2);

        save(parent);

        child = new OneToManyChild();
        child.identifier = "Y";
        find(child);

        child2 = new OneToManyChild();
        child2.identifier = "X";
        find(child2);

        // Validate the child still exists
        Assert.assertEquals(child2.correlation, 32);

        OneToManyParent parent1 = new OneToManyParent();
        parent1.identifier = "Z";
        find(parent1);

        // Verify the relationship is still there
        Assert.assertEquals(parent1.correlation, 30);
        Assert.assertNotNull(parent1.childNoInverseCascade);
        Assert.assertEquals(parent1.childNoInverseCascade.size(), 2);

        initialize(parent1, "childNoInverseCascade");
        parent1.childNoInverseCascade.remove(1);
        save(parent1);

        // Get the parent to check relationships
        OneToManyParent parent2 = new OneToManyParent();
        parent2.identifier = "Z";
        find(parent2);

        // Ensure the relationship was not removed
        Assert.assertEquals(1, parent2.childNoInverseCascade.size());
        Assert.assertEquals(parent2.childNoInverseCascade.get(0).identifier, child2.identifier);
        Assert.assertEquals(30, parent2.correlation);

    }

    @Test
    public void ftestOneToManyRemoveHasMultiple()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "ZZ";
        parent.correlation = 30;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "YY";
        child.correlation = 31;
        //save(child);

        OneToManyChild child2 = new OneToManyChild();
        child2.identifier = "XX";
        child2.correlation = 32;
        //save(child);

        parent.childNoInverseCascade = new ArrayList<>();
        parent.childNoInverseCascade.add(child);
        parent.childNoInverseCascade.add(child2);

        save(parent);


        child = new OneToManyChild();
        child.identifier = "YY";
        find(child);

        child2 = new OneToManyChild();
        child2.identifier = "XX";
        find(child2);

        // Validate the child still exists
        Assert.assertEquals(child2.correlation, 32);

        OneToManyParent parent1 = new OneToManyParent();
        parent1.identifier = "ZZ";
        find(parent1);

        // Verify the relationship is still there
        Assert.assertEquals(parent1.correlation, 30);
        Assert.assertNotNull(parent1.childNoInverseCascade);
        Assert.assertEquals(parent1.childNoInverseCascade.size(), 2);
        //Assert.assertEquals(parent1.childNoInverseCascade.get(0).identifier, child.identifier);
        //Assert.assertEquals(parent1.childNoInverseCascade.get(1).identifier, child2.identifier);

        //parent1.childNoInverseNoCascade = null;
        initialize(parent1, "childNoInverseCascade");
        parent1.childNoInverseCascade.removeAll(parent1.childNoInverseCascade);
        save(parent1);

        // Get the parent to check relationships
        OneToManyParent parent2 = new OneToManyParent();
        parent2.identifier = "ZZ";
        find(parent2);

        // Ensure the relationship was not removed
        Assert.assertEquals(0, parent2.childNoInverseCascade.size());
        Assert.assertEquals(30, parent2.correlation);

    }
    // Test Multiple remove

}
