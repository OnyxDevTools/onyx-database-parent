package web.partition;

import category.WebServerTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.update.AttributeUpdate;
import entities.partition.FullTablePartitionEntity;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import java.util.Arrays;
import java.util.List;

/**
 * Created by timothy.osborn on 2/12/15.
 */
@Category({ WebServerTests.class })
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FullTableScanPartitionTest extends BasePartitionTest
{
    @Test
    public void aTestSavePartitionEntityWithIndex()
    {
        FullTablePartitionEntity FullTablePartitionEntity = new FullTablePartitionEntity();
        FullTablePartitionEntity.id = 1l;
        FullTablePartitionEntity.partitionId = 3l;
        FullTablePartitionEntity.indexVal = 5l;

        save(FullTablePartitionEntity);
    }

    @Test
    public void bTestQueryPartitionEntityWithIndex() throws OnyxException
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

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));
        query.setPartition(2l);

        List results = manager.executeQuery(query);
        Assert.assertTrue(results.size() == 1);
    }

    @Test
    public void bTestQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() throws OnyxException
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

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));

        List results = manager.executeQuery(query);
        Assert.assertTrue(results.size() == 2);
    }

    @Test
    public void cTestQueryFindQueryPartitionEntityWithIndex() throws OnyxException
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

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l).and("partitionId", QueryCriteriaOperator.EQUAL, 2l));

        List results = manager.executeQuery(query);
        Assert.assertTrue(results.size() == 1);
    }

    @Test
         public void dTestDeleteQueryPartitionEntity() throws OnyxException
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

    Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l).and("partitionId", QueryCriteriaOperator.EQUAL, 2l));

    int results = manager.executeDelete(query);
    Assert.assertTrue(results == 1);
}

    @Test
    public void bTestDeleteQueryPartitionEntityWithIndex() throws OnyxException
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

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));
        query.setPartition(2l);

        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestDeleteQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() throws OnyxException
    {
        FullTablePartitionEntity entity = new FullTablePartitionEntity();
        entity.id = 1l;
        entity.partitionId = 3l;
        entity.indexVal = 5l;

        save(entity);

        FullTablePartitionEntity entity2 = new FullTablePartitionEntity();
        entity2.id = 2l;
        entity2.partitionId = 2l;
        entity2.indexVal = 5l;

        save(entity2);

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));

        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 2);
    }


    @Test
    public void dTestUpdateQueryPartitionEntity() throws OnyxException
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

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l).and("partitionId", QueryCriteriaOperator.EQUAL, 2l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("indexVal", 6l)));

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestUpdateQueryPartitionEntityWithIndex() throws OnyxException
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

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("indexVal", 6l)));
        query.setPartition(2l);

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestUpdateQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() throws OnyxException
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

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("indexVal", 6l)));

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 2);
    }

    @Test
    public void bTestUpdatePartitionField() throws OnyxException
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

        Query query = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("partitionId", 5l), new AttributeUpdate("indexVal", 6l)));

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 2);

        Query query2 = new Query(FullTablePartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 6l));
        query2.setPartition(5l);
        List result = manager.executeQuery(query2);

        Assert.assertTrue(result.size() == 2);
    }


}
