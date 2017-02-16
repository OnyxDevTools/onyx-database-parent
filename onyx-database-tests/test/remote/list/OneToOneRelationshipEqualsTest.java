package remote.list;

import category.RemoteServerTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.application.DatabaseServer;
import entities.OneToOneChildFetchEntity;
import entities.OneToOneFetchEntity;
import entities.relationship.OneToManyChild;
import entities.relationship.OneToManyParent;
import org.junit.*;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by timothy.osborn on 11/8/14.
 */
@Category({ RemoteServerTests.class })
public class OneToOneRelationshipEqualsTest extends RemoteBaseTest
{

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Before
    public void seedData() throws InitializationException, InterruptedException {

        initialize();

        OneToOneFetchEntity entity = new OneToOneFetchEntity();
        entity.id = "FIRST ONE";
        entity.stringValue = "Some test strin";
        entity.dateValue = new Date(1000);
        entity.doublePrimitive = 3.3;
        entity.doubleValue = 1.1;
        entity.booleanValue = false;
        entity.booleanPrimitive = true;
        entity.longPrimitive = 1000l;
        entity.longValue = 323l;
        save(entity);
        find(entity);

        entity = new OneToOneFetchEntity();
        entity.id = "FIRST ONE1";
        entity.stringValue = "Some test strin1";
        entity.dateValue = new Date(1001);
        entity.doublePrimitive = 3.31;
        entity.doubleValue = 1.11;
        entity.booleanValue = true;
        entity.booleanPrimitive = false;
        entity.longPrimitive = 1002l;
        entity.longValue = 322l;
        save(entity);
        find(entity);

        entity = new OneToOneFetchEntity();
        entity.id = "FIRST ONE2";
        entity.stringValue = "Some test strin1";
        entity.dateValue = new Date(1001);
        entity.doublePrimitive = 3.31;
        entity.doubleValue = 1.11;
        entity.booleanValue = true;
        entity.booleanPrimitive = false;
        entity.longPrimitive = 1002l;
        entity.longValue = 322l;
        save(entity);
        find(entity);

        entity = new OneToOneFetchEntity();
        entity.id = "FIRST ONE3";
        entity.stringValue = "Some test strin2";
        entity.dateValue = new Date(1002);
        entity.doublePrimitive = 3.32;
        entity.doubleValue = 1.12;
        entity.booleanValue = true;
        entity.booleanPrimitive = false;
        entity.longPrimitive = 1001l;
        entity.longValue = 321l;
        save(entity);
        find(entity);

        entity = new OneToOneFetchEntity();
        entity.id = "FIRST ONE3";
        entity.stringValue = "Some test strin3";
        entity.dateValue = new Date(1022);
        entity.doublePrimitive = 3.35;
        entity.doubleValue = 1.126;
        entity.booleanValue = false;
        entity.booleanPrimitive = true;
        entity.longPrimitive = 1301l;
        entity.longValue = 322l;
        save(entity);
        find(entity);

        entity = new OneToOneFetchEntity();
        entity.id = "FIRST ONE4";
        save(entity);
        find(entity);

        entity = new OneToOneFetchEntity();
        entity.id = "FIRST ONE5";
        save(entity);
        find(entity);






        OneToOneChildFetchEntity entity2 = new OneToOneChildFetchEntity();
        entity2.id = "FIRST ONE";
        entity2.stringValue = "Some test strin";
        entity2.dateValue = new Date(1000);
        entity2.doublePrimitive = 3.3;
        entity2.doubleValue = 1.1;
        entity2.booleanValue = false;
        entity2.booleanPrimitive = true;
        entity2.longPrimitive = 1000l;
        entity2.longValue = 323l;
        save(entity2);
        find(entity2);

        entity2.parent = new OneToOneFetchEntity();
        entity2.parent.id = "FIRST ONE1";
        save(entity2);

        entity2 = new OneToOneChildFetchEntity();
        entity2.id = "FIRST ONE1";
        entity2.stringValue = "Some test strin1";
        entity2.dateValue = new Date(1001);
        entity2.doublePrimitive = 3.31;
        entity2.doubleValue = 1.11;
        entity2.booleanValue = true;
        entity2.booleanPrimitive = false;
        entity2.longPrimitive = 1002l;
        entity2.longValue = 322l;
        save(entity2);
        find(entity2);

        entity2.parent = new OneToOneFetchEntity();
        entity2.parent.id = "FIRST ONE2";
        save(entity2);

        entity2 = new OneToOneChildFetchEntity();
        entity2.id = "FIRST ONE2";
        entity2.stringValue = "Some test strin1";
        entity2.dateValue = new Date(1001);
        entity2.doublePrimitive = 3.31;
        entity2.doubleValue = 1.11;
        entity2.booleanValue = true;
        entity2.booleanPrimitive = false;
        entity2.longPrimitive = 1002l;
        entity2.longValue = 322l;
        save(entity2);
        find(entity2);

        entity2 = new OneToOneChildFetchEntity();
        entity2.id = "FIRST ONE3";
        entity2.stringValue = "Some test strin2";
        entity2.dateValue = new Date(1002);
        entity2.doublePrimitive = 3.32;
        entity2.doubleValue = 1.12;
        entity2.booleanValue = true;
        entity2.booleanPrimitive = false;
        entity2.longPrimitive = 1001l;
        entity2.longValue = 321l;
        save(entity2);
        find(entity2);

        entity2 = new OneToOneChildFetchEntity();
        entity2.id = "FIRST ONE3";
        entity2.stringValue = "Some test strin3";
        entity2.dateValue = new Date(1022);
        entity2.doublePrimitive = 3.35;
        entity2.doubleValue = 1.126;
        entity2.booleanValue = false;
        entity2.booleanPrimitive = true;
        entity2.longPrimitive = 1301l;
        entity2.longValue = 322l;
        save(entity2);
        find(entity2);

        entity2.parent = new OneToOneFetchEntity();
        entity2.parent.id = "FIRST ONE3";
        save(entity2);
        find(entity2);

        find(entity2.parent);

        entity2 = new OneToOneChildFetchEntity();
        entity2.id = "FIRST ONE4";
        save(entity2);
        find(entity2);

        entity2 = new OneToOneChildFetchEntity();
        entity2.id = "FIRST ONE5";
        save(entity2);
        find(entity2);

    }

    @Test
    public void testOneToOneHasRelationship() throws EntityException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3")
                .and("child.id", QueryCriteriaOperator.EQUAL, "FIRST ONE3");

        long time = System.currentTimeMillis();
        List<OneToOneFetchEntity> results = manager.list(OneToOneFetchEntity.class, criteria);
        long done = System.currentTimeMillis();

        Assert.assertEquals(1, results.size());
    }

    @Test
    public void testOneToOneNoMeetCriteriaRelationship() throws EntityException
    {

        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some te1st strin3")
                .and("child.id", QueryCriteriaOperator.EQUAL, "FIRST ONE3");

        long time = System.currentTimeMillis();
        List<OneToOneFetchEntity> results = manager.list(OneToOneFetchEntity.class, criteria);
        long done = System.currentTimeMillis();

        Assert.assertEquals(0, results.size());
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
        junit.framework.Assert.assertEquals(child2.correlation, 32);

        OneToManyParent parent1 = new OneToManyParent();
        parent1.identifier = "ZZ";
        find(parent1);

        // Verify the relationship is still there
        junit.framework.Assert.assertEquals(parent1.correlation, 30);
        junit.framework.Assert.assertNotNull(parent1.childNoInverseCascade);
        junit.framework.Assert.assertEquals(parent1.childNoInverseCascade.size(), 2);
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
        junit.framework.Assert.assertEquals(0, parent2.childNoInverseCascade.size());
        junit.framework.Assert.assertEquals(30, parent2.correlation);

    }
}
