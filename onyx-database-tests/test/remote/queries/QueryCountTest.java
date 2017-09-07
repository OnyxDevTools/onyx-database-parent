package remote.queries;

import category.RemoteServerTests;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryPartitionMode;
import entities.SimpleEntity;
import entities.partition.BasicPartitionEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

import java.io.IOException;

/**
 * This test will address the countForQuery() method
 * within the PersistenceManager API added in 1.3.0
 */
@Category({RemoteServerTests.class})
public class QueryCountTest extends RemoteBaseTest {
    @After
    public void after() throws IOException {
        shutdown();
    }

    @Before
    public void before() throws InitializationException {
        initialize();
    }

    /**
     * Tests an entity without a partition
     */
    @Test
    public void testQueryCountForNonPartitionEntity() throws OnyxException {
        Query query = new Query();
        query.setEntityType(SimpleEntity.class);
        manager.executeDelete(query);

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("ASDF");
        manager.saveEntity(simpleEntity);

        simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("ASDFL");
        manager.saveEntity(simpleEntity);

        simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("ASDF");
        manager.deleteEntity(simpleEntity);

        query = new Query();
        query.setEntityType(SimpleEntity.class);
        assert manager.countForQuery(query) == 1;
    }

    /**
     * Tests an entity with a partition.  The count uses a specific partition
     */
    @Test
    public void testQueryCountForPartitionEntity() throws OnyxException {
        BasicPartitionEntity basicPartitionEntity = new BasicPartitionEntity();
        basicPartitionEntity.partitionId = 3L;
        basicPartitionEntity.id = 1L;

        manager.saveEntity(basicPartitionEntity);

        basicPartitionEntity = new BasicPartitionEntity();
        basicPartitionEntity.partitionId = 4L;
        basicPartitionEntity.id = 2L;

        manager.saveEntity(basicPartitionEntity);

        Query query = new Query();
        query.setEntityType(BasicPartitionEntity.class);
        query.setPartition(3L);
        assert manager.countForQuery(query) == 1;
    }


    /**
     * Tests an entity with a partition whereas the query addresses the entire
     * partition set.
     */
    @Test
    public void testQueryCountForAllPartitions() throws OnyxException {

        Query query = new Query();
        query.setEntityType(BasicPartitionEntity.class);
        query.setPartition(QueryPartitionMode.ALL);
        manager.executeDelete(query);

        BasicPartitionEntity basicPartitionEntity = new BasicPartitionEntity();
        basicPartitionEntity.partitionId = 3L;
        basicPartitionEntity.id = 1L;

        manager.saveEntity(basicPartitionEntity);

        basicPartitionEntity = new BasicPartitionEntity();
        basicPartitionEntity.partitionId = 4L;
        basicPartitionEntity.id = 2L;

        manager.saveEntity(basicPartitionEntity);

        query = new Query();
        query.setEntityType(BasicPartitionEntity.class);
        query.setPartition(QueryPartitionMode.ALL);
        assert manager.countForQuery(query) == 2;
    }

    /**
     * Tests a custom query rather than the entire data set
     */
    @Test
    public void testQueryCountForCustomQuery() throws OnyxException {

        Query query = new Query();
        query.setEntityType(BasicPartitionEntity.class);
        query.setPartition(QueryPartitionMode.ALL);
        manager.executeDelete(query);

        BasicPartitionEntity basicPartitionEntity = new BasicPartitionEntity();
        basicPartitionEntity.partitionId = 3L;
        basicPartitionEntity.id = 1L;

        manager.saveEntity(basicPartitionEntity);

        basicPartitionEntity = new BasicPartitionEntity();
        basicPartitionEntity.partitionId = 4L;
        basicPartitionEntity.id = 2L;

        manager.saveEntity(basicPartitionEntity);

        query = new Query(BasicPartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.GREATER_THAN, 1L));
        assert manager.countForQuery(query) == 1;
    }
}
