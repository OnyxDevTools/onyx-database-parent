package memory.partition;

/**
 * Created by timothy.osborn on 3/19/15.
 */

import category.EmbeddedDatabaseTests;
import category.InMemoryDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.update.AttributeUpdate;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import entities.partition.FullTablePartitionEntity;

import java.util.Arrays;
import java.util.List;

/**
 * Created by timothy.osborn on 2/12/15.
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ InMemoryDatabaseTests.class })
public class IdentifierScanPartitionTest extends BasePartitionTest
{

    @Test
    public void bTestQueryPartitionEntityWithIndex() throws EntityException
    {
        FullTablePartitionEntity FullTablePartitionEntity = new FullTablePartitionEntity();
        FullTablePartitionEntity.id = 1l;
        FullTablePartitionEntity.partitionId = 3l;
        FullTablePartitionEntity.indexVal = 5l;

        save(FullTablePartitionEntity);

        FullTablePartitionEntity FullTablePartitionEntity2 = new FullTablePartitionEntity();
        FullTablePartitionEntity2.id = 2l;
        FullTablePartitionEntity2.partitionId = 2l;
        FullTablePartitionEntity2.indexVal = 5l;

        save(FullTablePartitionEntity2);

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1l));
        query.setPartition(3l);

        List results = manager.executeQuery(query);
        Assert.assertTrue(results.size() == 1);
    }

    @Test
    public void bTestQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() throws EntityException
    {
        FullTablePartitionEntity FullTablePartitionEntity = new FullTablePartitionEntity();
        FullTablePartitionEntity.id = 1l;
        FullTablePartitionEntity.partitionId = 3l;
        FullTablePartitionEntity.indexVal = 5l;

        save(FullTablePartitionEntity);

        FullTablePartitionEntity FullTablePartitionEntity2 = new FullTablePartitionEntity();
        FullTablePartitionEntity2.id = 2l;
        FullTablePartitionEntity2.partitionId = 2l;
        FullTablePartitionEntity2.indexVal = 5l;

        save(FullTablePartitionEntity2);

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.IN, Arrays.asList(1l,2l)));

        List results = manager.executeQuery(query);
        Assert.assertTrue(results.size() == 2);
    }

    @Test
    public void cTestQueryFindQueryPartitionEntityWithIndex() throws EntityException
    {
        FullTablePartitionEntity FullTablePartitionEntity = new FullTablePartitionEntity();
        FullTablePartitionEntity.id = 1l;
        FullTablePartitionEntity.partitionId = 3l;
        FullTablePartitionEntity.indexVal = 5l;

        save(FullTablePartitionEntity);

        FullTablePartitionEntity FullTablePartitionEntity2 = new FullTablePartitionEntity();
        FullTablePartitionEntity2.id = 1l;
        FullTablePartitionEntity2.partitionId = 2l;
        FullTablePartitionEntity2.indexVal = 5l;

        save(FullTablePartitionEntity2);

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1l).and("partitionId", QueryCriteriaOperator.EQUAL, 3l));

        List results = manager.executeQuery(query);
        Assert.assertTrue(results.size() == 1);
    }

    @Test
    public void dTestDeleteQueryPartitionEntity() throws EntityException
    {
        FullTablePartitionEntity FullTablePartitionEntity = new FullTablePartitionEntity();
        FullTablePartitionEntity.id = 1l;
        FullTablePartitionEntity.partitionId = 3l;
        FullTablePartitionEntity.indexVal = 5l;

        save(FullTablePartitionEntity);

        FullTablePartitionEntity FullTablePartitionEntity2 = new FullTablePartitionEntity();
        FullTablePartitionEntity2.id = 1l;
        FullTablePartitionEntity2.partitionId = 2l;
        FullTablePartitionEntity2.indexVal = 5l;

        save(FullTablePartitionEntity2);

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1l).and("partitionId", QueryCriteriaOperator.EQUAL, 2l));

        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestDeleteQueryPartitionEntityWithIndex() throws EntityException
    {
        FullTablePartitionEntity FullTablePartitionEntity = new FullTablePartitionEntity();
        FullTablePartitionEntity.id = 1l;
        FullTablePartitionEntity.partitionId = 3l;
        FullTablePartitionEntity.indexVal = 5l;

        save(FullTablePartitionEntity);

        FullTablePartitionEntity FullTablePartitionEntity2 = new FullTablePartitionEntity();
        FullTablePartitionEntity2.id = 1l;
        FullTablePartitionEntity2.partitionId = 2l;
        FullTablePartitionEntity2.indexVal = 5l;

        save(FullTablePartitionEntity2);

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1l));
        query.setPartition(2l);

        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestDeleteQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() throws EntityException
    {
        FullTablePartitionEntity entity = new FullTablePartitionEntity();
        entity.id = 1l;
        entity.partitionId = 3l;
        entity.indexVal = 5l;

        save(entity);

        FullTablePartitionEntity entity2 = new FullTablePartitionEntity();
        entity2.id = 1l;
        entity2.partitionId = 2l;
        entity2.indexVal = 5l;

        save(entity2);

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 1l));

        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 2);
    }


    @Test
    public void dTestUpdateQueryPartitionEntity() throws EntityException
    {
        FullTablePartitionEntity FullTablePartitionEntity = new FullTablePartitionEntity();
        FullTablePartitionEntity.id = 1l;
        FullTablePartitionEntity.partitionId = 3l;
        FullTablePartitionEntity.indexVal = 5l;

        save(FullTablePartitionEntity);

        FullTablePartitionEntity FullTablePartitionEntity2 = new FullTablePartitionEntity();
        FullTablePartitionEntity2.id = 2l;
        FullTablePartitionEntity2.partitionId = 2l;
        FullTablePartitionEntity2.indexVal = 5l;

        save(FullTablePartitionEntity2);

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2l).and("partitionId", QueryCriteriaOperator.EQUAL, 2l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("indexVal", 6l)));

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestUpdateQueryPartitionEntityWithIndex() throws EntityException
    {
        FullTablePartitionEntity FullTablePartitionEntity = new FullTablePartitionEntity();
        FullTablePartitionEntity.id = 1l;
        FullTablePartitionEntity.partitionId = 3l;
        FullTablePartitionEntity.indexVal = 5l;

        save(FullTablePartitionEntity);

        FullTablePartitionEntity FullTablePartitionEntity2 = new FullTablePartitionEntity();
        FullTablePartitionEntity2.id = 2l;
        FullTablePartitionEntity2.partitionId = 2l;
        FullTablePartitionEntity2.indexVal = 5l;

        save(FullTablePartitionEntity2);

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("indexVal", 6l)));
        query.setPartition(2l);

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestUpdatePartitionField() throws EntityException
    {
        FullTablePartitionEntity FullTablePartitionEntity = new FullTablePartitionEntity();
        FullTablePartitionEntity.id = 2l;
        FullTablePartitionEntity.partitionId = 3l;
        FullTablePartitionEntity.indexVal = 5l;

        save(FullTablePartitionEntity);

        FullTablePartitionEntity FullTablePartitionEntity2 = new FullTablePartitionEntity();
        FullTablePartitionEntity2.id = 2l;
        FullTablePartitionEntity2.partitionId = 2l;
        FullTablePartitionEntity2.indexVal = 5l;

        save(FullTablePartitionEntity2);

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("partitionId", 5l), new AttributeUpdate("indexVal", 6l)));

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 2);

        Query query2 = new Query(FullTablePartitionEntity.class, new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2l));
        query2.setPartition(5l);
        List result = manager.executeQuery(query2);

        Assert.assertTrue(result.size() == 1);
    }


}
