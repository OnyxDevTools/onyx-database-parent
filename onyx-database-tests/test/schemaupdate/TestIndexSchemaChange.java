package schemaupdate;

import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import embedded.base.BaseTest;
import entities.schema.SchemaIndexChangedEntity;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

/**
 * Created by Tim Osborn on 9/1/15.
 */
@Ignore
public class TestIndexSchemaChange extends BaseTest {
    @Before
    public void startup() throws Exception {
        this.initialize();
    }

    @After
    public void teardown() throws Exception {
        this.shutdown();
    }

    @Test
    public void deleteData() throws Exception {
        this.shutdown();
        Thread.sleep(2000);
        deleteDatabase();
    }

    /**
     * Initialize test by inserting dummy record
     *
     * @throws Exception
     */
    @Test
    public void initializeTest() throws Exception {

        SchemaIndexChangedEntity entity = new SchemaIndexChangedEntity();
        entity.otherIndex = 1;
//        entity.longValue = 23l;

        save(entity);

        find(entity);

        Assert.assertTrue(entity.id == 1);
//        Assert.assertTrue(entity.longValue == 23l);
    }

    /**
     * Test change the index type from a long to a String
     *
     * PRE - Change longValue attribute to String within SchemaIndexChangedEntity
     *
     * @throws Exception
     */
    @Test
    public void testChangeIndexType() throws Exception {

        SchemaIndexChangedEntity entity = new SchemaIndexChangedEntity();
        entity.id = 1;
        find(entity);

        // Wait to re-index
        Thread.sleep(2000);

        Query query = new Query(SchemaIndexChangedEntity.class, new QueryCriteria("longValue", QueryCriteriaOperator.EQUAL, "23"));
        List<SchemaIndexChangedEntity> results = manager.executeQuery(query);

        Assert.assertTrue(results.size() == 1);
        Assert.assertTrue(results.get(0).longValue.equals("23"));
    }

    /**
     * Test change the index type from a long to a String
     *
     * PRE - Un Comment index field
     *
     * @throws Exception
     */
    @Test
    public void testAddIndex() throws Exception {

        SchemaIndexChangedEntity entity = new SchemaIndexChangedEntity();
        entity.id = 1;
        find(entity);

        // Wait to re-index
        Thread.sleep(2000);

        Query query = new Query(SchemaIndexChangedEntity.class, new QueryCriteria("otherIndex", QueryCriteriaOperator.EQUAL, 1));
        List<SchemaIndexChangedEntity> results = manager.executeQuery(query);

        Assert.assertTrue(results.size() == 1);
        Assert.assertTrue(results.get(0).otherIndex == 1);
    }
}