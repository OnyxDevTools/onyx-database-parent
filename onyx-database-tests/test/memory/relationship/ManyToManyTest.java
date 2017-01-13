package memory.relationship;

import category.InMemoryDatabaseTests;
import com.onyx.exception.EntityException;
import entities.relationship.ManyToManyChild;
import entities.relationship.ManyToManyParent;
import memory.base.BaseTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category({ InMemoryDatabaseTests.class })
public class ManyToManyTest extends BaseTest
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
    public void testManyToManyBasic()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "A";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "B";
        child.correlation = 2;
        save(child);

        parent.childNoCascade = new ArrayList<>();
        parent.childNoCascade.add(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "A";
        find(parent);

        Assert.assertEquals(1,parent.correlation);
        Assert.assertEquals(1,parent.childNoCascade.size());
        Assert.assertEquals(child.identifier, parent.childNoCascade.get(0).identifier);

        child = new ManyToManyChild();
        child.identifier = "B";
        find(child);

        Assert.assertEquals(2,child.correlation);
        Assert.assertEquals(1,child.parentNoCascade.size());
        Assert.assertEquals(parent.identifier, child.parentNoCascade.get(0).identifier);
    }

    @Test
    public void testManyToManyMultiple()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "C";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "D";
        child.correlation = 2;
        save(child);

        ManyToManyChild child2 = new ManyToManyChild();
        child2.identifier = "E";
        child2.correlation = 3;
        save(child2);

        parent.childNoCascade = new ArrayList<>();
        parent.childNoCascade.add(child);
        parent.childNoCascade.add(child2);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "C";
        find(parent);

        Assert.assertEquals(1,parent.correlation);
        Assert.assertEquals(2,parent.childNoCascade.size());
        Assert.assertEquals(child.identifier, parent.childNoCascade.get(0).identifier);

        child = new ManyToManyChild();
        child.identifier = "D";
        find(child);

        Assert.assertEquals(2,child.correlation);
        Assert.assertEquals(1,child.parentNoCascade.size());
        Assert.assertEquals(parent.identifier, child.parentNoCascade.get(0).identifier);
    }

    @Test
    public void testManyToManyNoCascadeRemove()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "F";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "G";
        child.correlation = 2;
        save(child);

        parent.childNoCascade = new ArrayList<>();
        parent.childNoCascade.add(child);
        save(parent);

        parent.childNoCascade.remove(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "F";
        find(parent);

        Assert.assertEquals(1,parent.correlation);
        Assert.assertEquals(1,parent.childNoCascade.size());

    }

    @Test
    public void testManyToManyCascadeRemove()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "H";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "I";
        child.correlation = 2;
        save(child);

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);
        save(parent);

        parent.childCascade.remove(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "H";
        find(parent);

        Assert.assertEquals(1,parent.correlation);
        Assert.assertEquals(0,parent.childCascade.size());

    }

    @Test
    public void testManyToManyCascadeDelete()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "J";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "K";
        child.correlation = 2;
        save(child);

        parent.childNoCascade = new ArrayList<>();
        parent.childNoCascade.add(child);
        save(parent);

        delete(child);

        parent = new ManyToManyParent();
        parent.identifier = "J";
        find(parent);

        Assert.assertEquals(1,parent.correlation);
        Assert.assertEquals(0,parent.childNoCascade.size());
    }

    @Test
    public void testManyToManyNoInverse()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "J";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "K";
        child.correlation = 2;
        save(child);

        parent.childNoInverseCascade = new ArrayList<>();
        parent.childNoInverseCascade.add(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "J";
        find(parent);

        Assert.assertEquals(1,parent.correlation);
        Assert.assertEquals(1,parent.childNoInverseCascade.size());
    }

    @Test
    public void testManyToManyNoInverseDelete()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "Z";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "P";
        child.correlation = 2;
        save(child);

        parent.childNoInverseCascade = new ArrayList<>();
        parent.childNoInverseCascade.add(child);
        save(parent);

        parent.childNoInverseCascade.remove(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "Z";
        find(parent);

        Assert.assertEquals(1,parent.correlation);
        Assert.assertEquals(0,parent.childNoInverseCascade.size());
    }
}
