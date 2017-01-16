package remote.relationship;

import category.RemoteServerTests;
import com.onyx.exception.EntityException;
import entities.relationship.OneToManyChild;
import entities.relationship.OneToManyParent;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

import java.io.IOException;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category({ RemoteServerTests.class })
public class ManyToOneTest extends RemoteBaseTest
{

    @Before
    public void before() throws EntityException
    {
        initialize();
    }

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Test
    public void testManyToOneRelationshipSaved()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "MTO";
        parent.correlation = 1;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "MTOB";
        child.correlation = 2;
        save(child);

        child.parentNoCascade = parent;
        save(child);

        find(parent);

        Assert.assertEquals(1, parent.childNoCascade.size());
        Assert.assertEquals(parent.childNoCascade.get(0).identifier, child.identifier);
        Assert.assertEquals(parent.childNoCascade.get(0).correlation, child.correlation);
        Assert.assertEquals(2, child.correlation);
    }

    @Test
    public void testManyToOneRelationshipSavedAndRemoved()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "MTO1";
        parent.correlation = 1;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "MTOB1";
        child.correlation = 2;
        save(child);

        child.parentNoCascade = parent;
        save(child);

        find(parent);

        Assert.assertEquals(1, parent.childNoCascade.size());
        Assert.assertEquals(parent.childNoCascade.get(0).identifier, child.identifier);
        Assert.assertEquals(parent.childNoCascade.get(0).correlation, child.correlation);
        Assert.assertEquals(2, child.correlation);

        child.parentNoCascade = null;
        save(child);

        find(parent);

        Assert.assertEquals(0, parent.childNoCascade.size());

        find(child);

        Assert.assertNull(child.parentNoCascade);

    }

    @Test
    public void testManyToOneRelationshipSavedAndReassigned()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "MTO1";
        parent.correlation = 1;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "MTOB1";
        child.correlation = 2;
        save(child);

        child.parentNoCascade = parent;
        save(child);

        find(parent);

        Assert.assertEquals(1, parent.childNoCascade.size());
        Assert.assertEquals(parent.childNoCascade.get(0).identifier, child.identifier);
        Assert.assertEquals(parent.childNoCascade.get(0).correlation, child.correlation);
        Assert.assertEquals(2, child.correlation);

        child.parentNoCascade = new OneToManyParent();
        child.parentNoCascade.identifier = "SECOND";
        save(child.parentNoCascade);
        save(child);

        parent = new OneToManyParent();
        parent.identifier = "MTO1";
        find(parent);

        Assert.assertEquals(1, parent.childNoCascade.size());

        find(child);

        Assert.assertEquals(child.parentNoCascade.identifier, "SECOND");

        find(child.parentNoCascade);

        Assert.assertEquals(1, child.parentNoCascade.childNoCascade.size());
    }

    @Test
    public void testManyToOneRelationshipSavedAndReassignedCascade()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "MTO11";
        parent.correlation = 1;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "MTOB11";
        child.correlation = 2;
        save(child);

        child.parentCascadeTwo = parent;
        save(child);

        find(parent);

        Assert.assertEquals(1, parent.childCascadeTwo.size());
        Assert.assertEquals(parent.childCascadeTwo.get(0).identifier, child.identifier);
        Assert.assertEquals(parent.childCascadeTwo.get(0).correlation, child.correlation);
        Assert.assertEquals(2, child.correlation);

        child.parentCascadeTwo = new OneToManyParent();
        child.parentCascadeTwo.identifier = "SECOND1";
        save(child.parentCascadeTwo);
        save(child);

        parent = new OneToManyParent();
        parent.identifier = "MTO11";
        find(parent);

        Assert.assertEquals(1, parent.childCascadeTwo.size());

        find(child);

        Assert.assertEquals(child.parentCascadeTwo.identifier, "SECOND1");

        find(child.parentCascadeTwo);

        Assert.assertEquals(1, child.parentCascadeTwo.childCascadeTwo.size());
    }

    @Test
    public void testManyToOneRelationshipParentDeleted()
    {
        OneToManyParent parent = new OneToManyParent();
        parent.identifier = "MTO111";
        parent.correlation = 1;
        save(parent);

        OneToManyChild child = new OneToManyChild();
        child.identifier = "MTOB111";
        child.correlation = 2;
        save(child);

        child.parentNoCascade = parent;
        save(child);

        find(parent);

        Assert.assertEquals(1, parent.childNoCascade.size());
        Assert.assertEquals(parent.childNoCascade.get(0).identifier, child.identifier);
        Assert.assertEquals(parent.childNoCascade.get(0).correlation, child.correlation);
        Assert.assertEquals(2, child.correlation);

        delete(parent);

        child = new OneToManyChild();
        child.identifier = "MTOB111";
        find(child);

        Assert.assertNull(child.parentNoCascade);
        Assert.assertEquals(2,child.correlation);
    }
}
