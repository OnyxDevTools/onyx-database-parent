package embedded.list;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import embedded.base.BaseTest;
import entities.OneToManyChildFetchEntity;
import entities.OneToOneFetchEntity;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Category({ EmbeddedDatabaseTests.class })
public class OneToManyRelationshipEqualsTest extends BaseTest
{
    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Before
    public void seedData() throws InitializationException
    {
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






        OneToManyChildFetchEntity entity2 = new OneToManyChildFetchEntity();
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

        entity2.parents = new OneToOneFetchEntity();
        entity2.parents.id = "FIRST ONE1";
        save(entity2);

        entity2 = new OneToManyChildFetchEntity();
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

        entity2.parents = new OneToOneFetchEntity();
        entity2.parents.id = "FIRST ONE2";
        save(entity2);

        entity2 = new OneToManyChildFetchEntity();
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

        entity2 = new OneToManyChildFetchEntity();
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

        entity2 = new OneToManyChildFetchEntity();
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

        entity2.parents = new OneToOneFetchEntity();
        entity2.parents.id = "FIRST ONE2";
        save(entity2);

        entity2.parents = new OneToOneFetchEntity();
        entity2.parents.id = "FIRST ONE3";
        save(entity2);
        find(entity2);

        find(entity2.parents);

        entity2 = new OneToManyChildFetchEntity();
        entity2.id = "FIRST ONE4";
        save(entity2);
        find(entity2);

        entity2 = new OneToManyChildFetchEntity();
        entity2.id = "FIRST ONE5";
        save(entity2);
        find(entity2);

    }

    @Test
    public void testOneToOneHasRelationshipMeetsOne() throws EntityException
    {
        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some test strin3")
                .and("children.id", QueryCriteriaOperator.EQUAL, "FIRST ONE3");

        long time = System.currentTimeMillis();
        List<OneToOneFetchEntity> results = manager.list(OneToOneFetchEntity.class, criteria);
        long done = System.currentTimeMillis();

        Assert.assertEquals(1, results.size());
    }

    @Test
    public void testOneToOneHasRelationship() throws EntityException
    {

        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.CONTAINS, "Some test strin")
                .and("children.id", QueryCriteriaOperator.EQUAL, "FIRST ONE3");

        List<OneToOneFetchEntity> results = manager.list(OneToOneFetchEntity.class, criteria);

        Assert.assertEquals(1, results.size());
    }

    @Test
    public void testOneToOneNoMeetCriteriaRelationship() throws EntityException
    {

        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.EQUAL, "Some te1st strin3")
                .and("children.id", QueryCriteriaOperator.EQUAL, "FIRST ONE3");

        long time = System.currentTimeMillis();
        List<OneToOneFetchEntity> results = manager.list(OneToOneFetchEntity.class, criteria);
        long done = System.currentTimeMillis();

        Assert.assertEquals(0, results.size());
    }

    @Test
    public void testOneToManyInCriteriaRelationship() throws EntityException
    {
        List<Object> idlist = new ArrayList<Object>();
        idlist.add("FIRST ONE3");
        idlist.add("FIRST ONE2");

        QueryCriteria criteria = new QueryCriteria("stringValue", QueryCriteriaOperator.STARTS_WITH, "Some test strin1")
                .and("children.id", QueryCriteriaOperator.IN, idlist);

        List<OneToOneFetchEntity> results = manager.list(OneToOneFetchEntity.class, criteria);
        Assert.assertEquals(0, results.size());
    }
}