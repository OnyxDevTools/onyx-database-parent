package memory.relationship;

import category.EmbeddedDatabaseTests;
import category.InMemoryDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.exception.NoResultsException;
import entities.relationship.*;
import memory.base.BaseTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category({ InMemoryDatabaseTests.class })
public class OneToOneTest extends BaseTest
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
    public void testOneToOneCascade()
    {
        OneToOneParent parent = new OneToOneParent();
        parent.correlation = 1;
        parent.identifier = "A";

        save(parent);
        find(parent);
        assertEquals(1, parent.correlation);
        assertNull(parent.child);

        parent.child = new OneToOneChild();
        parent.child.identifier = "B";
        parent.child.correlation = 2;

        save(parent);
        find(parent);

        assertEquals(1, parent.correlation);
        assertNotNull(parent.child);
        assertEquals(2, parent.child.correlation);

        OneToOneChild child = new OneToOneChild();
        child.identifier = "B";
        find(child);
        initialize(child, "parent");

        assertEquals(2, child.correlation);
        assertNotNull(child.parent);
        assertEquals(1, child.parent.correlation);

    }

    @Test
    public void testOneToOneNoCascade()
    {
        OneToOneChild child = new OneToOneChild();
        child.identifier = "D";
        child.correlation = 4;

        save(child);

        OneToOneParent parent = new OneToOneParent();
        parent.correlation = 3;
        parent.identifier = "C";

        save(parent);
        find(child);

        assertEquals(4, child.correlation);
        assertNull(child.parent);

        find(parent);
        assertEquals(3, parent.correlation);
        assertNull(parent.child);

        child.parent = parent;
        save(child);
        find(parent);

        assertEquals(3, parent.correlation);
        assertNotNull(parent.child);
        assertEquals(4, parent.child.correlation);

        find(child);
        initialize(child, "parent");

        assertEquals(4, child.correlation);
        assertNotNull(child.parent);
        assertEquals(3, child.parent.correlation);
    }

    @Test
    public void testDeleteRelationshipNoCascade()
    {

        // Save The Parent
        OneToOneParent parent = new OneToOneParent();
        parent.correlation = 10;
        parent.identifier = "E";
        save(parent);

        // Save the child
        OneToOneChild child = new OneToOneChild();
        child.correlation = 11;
        child.identifier = "F";
        save(child);

        parent.child = child;
        save(parent);

        parent = new OneToOneParent();
        parent.identifier = "E";
        find(parent);

        Assert.assertNotNull(parent.child);

        initialize(child, "parent");

        Assert.assertNotNull(child.parent);
        assertEquals(child.parent.identifier, parent.identifier);
        assertEquals(parent.child.identifier, child.identifier);

        parent.child = null;
        save(parent);
        find(parent);
        child = new OneToOneChild();
        child.identifier = "F";
        initialize(child, "parent");

        Assert.assertNull(child.parent);
        Assert.assertNull(parent.child);
    }

    @Test
    public void testDeleteCascade()
    {
        // Save The Parent
        OneToOneParent parent = new OneToOneParent();
        parent.correlation = 10;
        parent.identifier = "G";
        save(parent);

        // Save the child
        OneToOneChild child = new OneToOneChild();
        child.correlation = 11;
        child.identifier = "H";
        save(child);

        parent.cascadeChild = child;
        save(parent);

        parent = new OneToOneParent();
        parent.identifier = "G";
        parent = (OneToOneParent)find(parent);

        Assert.assertNotNull(parent.cascadeChild);

        initialize(child, "cascadeParent");

        Assert.assertNotNull(child.cascadeParent);
        assertEquals(child.cascadeParent.identifier, parent.identifier);
        assertEquals(parent.cascadeChild.identifier, child.identifier);

        parent.cascadeChild = null;
        save(parent);
        parent = (OneToOneParent)find(parent);
        child = new OneToOneChild();
        child.identifier = "H";

        boolean exceptionThrown = false;
        try {
            child = (OneToOneChild)manager.find(child);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException) {
                exceptionThrown = true;
            }
        }

        Assert.assertTrue(exceptionThrown);
        // Ensure this bad boy was deleted
        //Assert.assertEquals(child.correlation, 11);
        //Assert.assertNull(child.parent);

    }

    @Test
    public void testNoInverseCascade()
    {
        // Save The Parent
        OneToOneParent parent = new OneToOneParent();
        parent.correlation = 10;
        parent.identifier = "G";
        save(parent);

        // Save the child
        OneToOneChild child = new OneToOneChild();
        child.correlation = 11;
        child.identifier = "H";
        save(child);

        parent.childNoInverseCascade = child;
        save(parent);

        parent = new OneToOneParent();
        parent.identifier = "G";
        find(parent);

        Assert.assertNotNull(parent.childNoInverseCascade);

        initialize(child, "cascadeParent");

        assertEquals(parent.childNoInverseCascade.identifier, child.identifier);

        parent.childNoInverseCascade = null;

        parent.cascadeChild = null;
        save(parent);
        find(parent);

        child = new OneToOneChild();
        child.identifier = "H";

        boolean exceptionThrown = false;
        try {
            manager.find(child);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException) {
                exceptionThrown = true;
            }
        }

        Assert.assertTrue(exceptionThrown);
        // Ensure this bad boy was deleted
        //Assert.assertEquals(child.correlation, 11);
        //Assert.assertNull(child.cascadeParent);

    }

    @Test
    public void testNoInverse()
    {
        // Save The Parent
        OneToOneParent parent = new OneToOneParent();
        parent.correlation = 133;
        parent.identifier = "G";
        save(parent);

        // Save the child
        OneToOneChild child = new OneToOneChild();
        child.correlation = 112;
        child.identifier = "I";
        save(child);

        parent.childNoInverse = child;
        save(parent);

        parent = new OneToOneParent();
        parent.identifier = "G";
        find(parent);

        Assert.assertNotNull(parent.childNoInverse);
        assertEquals(parent.childNoInverse.identifier, child.identifier);

        parent.childNoInverse = null;

        parent.cascadeChild = null;
        save(parent);
        find(parent);

        child = new OneToOneChild();
        child.identifier = "I";

        boolean exceptionThrown = false;
        try {
            manager.find(child);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException) {
                exceptionThrown = true;
            }
        }

        Assert.assertFalse(exceptionThrown);
        // Ensure this bad boy was deleted
        assertEquals(child.correlation, 112);

    }

    @Test
    public void testInverseCascadeDelete()
    {
        // Save The Parent
        OneToOneParent parent = new OneToOneParent();
        parent.correlation = 10;
        parent.identifier = "Z";
        save(parent);

        // Save the child
        OneToOneChild child = new OneToOneChild();
        child.correlation = 11;
        child.identifier = "X";
        save(child);

        parent.cascadeChild = child;
        save(parent);

        parent = new OneToOneParent();
        parent.identifier = "Z";
        find(parent);

        Assert.assertNotNull(parent.cascadeChild);

        initialize(child, "cascadeParent");

        assertEquals(parent.cascadeChild.identifier, child.identifier);

        save(parent);
        find(parent);
        delete(parent);

        child = new OneToOneChild();
        child.identifier = "X";

        boolean exceptionThrown = false;
        try {
            manager.find(child);
        }
        catch (EntityException e)
        {
            if(e instanceof NoResultsException) {
                exceptionThrown = true;
            }
        }

        Assert.assertTrue(exceptionThrown);

    }


    @Test
    public void testRecursiveOneToOne()
    {
        OneToOneRecursive parent = new OneToOneRecursive();
        parent.id = 1;
        parent.child = new OneToOneRecursiveChild();
        parent.child.id = 2;
        parent.child.third = new OneToOneThreeDeep();
        parent.child.third.id = 3;


        save(parent);

        OneToOneRecursive newParent = new OneToOneRecursive();
        newParent.id = 1;
        find(newParent);

        assertNotNull(parent);
        assertNotNull(parent.child);
        assertNotNull(parent.child.third);

        assertNotNull(parent.child.parent);
        assertNotNull(parent.child.third.parent);

    }
}
