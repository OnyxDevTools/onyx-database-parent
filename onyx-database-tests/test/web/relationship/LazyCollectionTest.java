package web.relationship;

import category.WebServerTests;
import com.onyx.exception.EntityException;
import com.onyx.persistence.collections.LazyRelationshipCollection;
import entities.relationship.ManyToManyChild;
import entities.relationship.ManyToManyParent;
import org.junit.*;
import org.junit.experimental.categories.Category;
import web.base.BaseTest;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by timothy.osborn on 2/10/15.
 */
@Ignore
@Category({ WebServerTests.class })
public class LazyCollectionTest extends BaseTest
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
    public void testExists()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "A";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "B";
        child.correlation = 2;
        save(child);

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "A";
        find(parent);

        Assert.assertTrue(parent.childCascade instanceof LazyRelationshipCollection);
        Assert.assertTrue(parent.childCascade.contains(parent.childCascade.get(0)));
    }

    @Test
    public void testSize()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "A";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "B";
        child.correlation = 2;
        save(child);

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "A";
        find(parent);

        Assert.assertTrue(parent.childCascade instanceof LazyRelationshipCollection);
        Assert.assertTrue(parent.childCascade.size() == 1);
    }

    @Test
    public void testAdd()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "A";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "B";
        child.correlation = 2;
        save(child);

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "A";
        find(parent);

        ManyToManyChild child2 = new ManyToManyChild();
        child2.identifier = "C";
        child2.correlation = 2;
        save(child2);

        parent.childCascade.add(child2);
        Assert.assertTrue(parent.childCascade instanceof LazyRelationshipCollection);
        Assert.assertTrue(parent.childCascade.size() == 2);
        Assert.assertTrue(parent.childCascade.contains(child2));
    }

    @Test
    public void testEmpty()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "A";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "B";
        child.correlation = 2;
        save(child);

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "A";
        find(parent);

        Assert.assertFalse(parent.childCascade.isEmpty());
    }

    @Test
    public void testClear()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "A";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "B";
        child.correlation = 2;
        save(child);

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "A";
        find(parent);

        parent.childCascade.clear();

        Assert.assertTrue(parent.childCascade.isEmpty());
    }

    @Test
    public void testSet()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "A";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "B";
        child.correlation = 2;
        save(child);

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "A";
        find(parent);

        ManyToManyChild child2 = new ManyToManyChild();
        child2.identifier = "C";
        child2.correlation = 2;
        save(child2);

        parent.childCascade.set(0,child2);
        Assert.assertTrue(parent.childCascade instanceof LazyRelationshipCollection);
        Assert.assertTrue(parent.childCascade.size() == 1);
        Assert.assertTrue(parent.childCascade.contains(child2));
    }

    @Test
    public void testRemove()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "A";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "B";
        child.correlation = 2;
        save(child);

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "A";
        find(parent);


        parent.childCascade.remove(0);
        Assert.assertTrue(parent.childCascade instanceof LazyRelationshipCollection);
        Assert.assertTrue(parent.childCascade.size() == 0);
    }

    @Test
    public void testRemoveByObject()
    {
        ManyToManyParent parent = new ManyToManyParent();
        parent.identifier = "A";
        parent.correlation = 1;
        save(parent);

        ManyToManyChild child = new ManyToManyChild();
        child.identifier = "B";
        child.correlation = 2;
        save(child);

        parent.childCascade = new ArrayList<>();
        parent.childCascade.add(child);
        save(parent);

        parent = new ManyToManyParent();
        parent.identifier = "A";
        find(parent);


        parent.childCascade.remove(parent.childCascade.get(0));
        Assert.assertTrue(parent.childCascade instanceof ArrayList);
        Assert.assertTrue(parent.childCascade.size() == 0);
    }
}
