package remote.partition;

import category.RemoteServerTests;
import com.onyx.exception.EntityException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.update.AttributeUpdate;
import entities.partition.IndexPartitionEntity;
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
@Category({ RemoteServerTests.class })
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IndexPartitionTest extends BasePartitionTest
{
    @Test
    public void aTestSavePartitionEntityWithIndex()
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);
    }

    @Test
    public void bTestQueryPartitionEntityWithIndex() throws EntityException
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);

        entities.partition.IndexPartitionEntity IndexPartitionEntity2 = new IndexPartitionEntity();
        IndexPartitionEntity2.id = 2l;
        IndexPartitionEntity2.partitionId = 2l;
        IndexPartitionEntity2.indexVal = 5l;

        save(IndexPartitionEntity2);

        Query query = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));
        query.setPartition(2l);

        List results = manager.executeQuery(query);
        Assert.assertTrue(results.size() == 1);
    }

    @Test
    public void bTestQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() throws EntityException
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);

        entities.partition.IndexPartitionEntity IndexPartitionEntity2 = new IndexPartitionEntity();
        IndexPartitionEntity2.id = 2l;
        IndexPartitionEntity2.partitionId = 2l;
        IndexPartitionEntity2.indexVal = 5l;

        save(IndexPartitionEntity2);

        Query query = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));

        List results = manager.executeQuery(query);
        Assert.assertTrue(results.size() == 2);
    }

    @Test
    public void cTestQueryFindQueryPartitionEntityWithIndex() throws EntityException
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);

        entities.partition.IndexPartitionEntity IndexPartitionEntity2 = new IndexPartitionEntity();
        IndexPartitionEntity2.id = 2l;
        IndexPartitionEntity2.partitionId = 2l;
        IndexPartitionEntity2.indexVal = 5l;

        save(IndexPartitionEntity2);

        Query query = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l).and("partitionId", QueryCriteriaOperator.EQUAL, 2l));

        List results = manager.executeQuery(query);
        Assert.assertTrue(results.size() == 1);
    }

    @Test
    public void dTestDeleteQueryPartitionEntity() throws EntityException
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);

        entities.partition.IndexPartitionEntity IndexPartitionEntity2 = new IndexPartitionEntity();
        IndexPartitionEntity2.id = 2l;
        IndexPartitionEntity2.partitionId = 2l;
        IndexPartitionEntity2.indexVal = 5l;

        save(IndexPartitionEntity2);

        Query query = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l).and("partitionId", QueryCriteriaOperator.EQUAL, 2l));

        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestDeleteQueryPartitionEntityWithIndex() throws EntityException
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);

        entities.partition.IndexPartitionEntity IndexPartitionEntity2 = new IndexPartitionEntity();
        IndexPartitionEntity2.id = 2l;
        IndexPartitionEntity2.partitionId = 2l;
        IndexPartitionEntity2.indexVal = 5l;

        save(IndexPartitionEntity2);

        Query query = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));
        query.setPartition(2l);

        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestDeleteQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() throws EntityException
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);

        entities.partition.IndexPartitionEntity IndexPartitionEntity2 = new IndexPartitionEntity();
        IndexPartitionEntity2.id = 2l;
        IndexPartitionEntity2.partitionId = 2l;
        IndexPartitionEntity2.indexVal = 5l;

        save(IndexPartitionEntity2);

        Query query = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));

        int results = manager.executeDelete(query);
        Assert.assertTrue(results == 2);
    }


    @Test
    public void dTestUpdateQueryPartitionEntity() throws EntityException
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);

        entities.partition.IndexPartitionEntity IndexPartitionEntity2 = new IndexPartitionEntity();
        IndexPartitionEntity2.id = 2l;
        IndexPartitionEntity2.partitionId = 2l;
        IndexPartitionEntity2.indexVal = 5l;

        save(IndexPartitionEntity2);

        Query query = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l).and("partitionId", QueryCriteriaOperator.EQUAL, 2l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("indexVal", 6l)));

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestUpdateQueryPartitionEntityWithIndex() throws EntityException
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);

        entities.partition.IndexPartitionEntity IndexPartitionEntity2 = new IndexPartitionEntity();
        IndexPartitionEntity2.id = 2l;
        IndexPartitionEntity2.partitionId = 2l;
        IndexPartitionEntity2.indexVal = 5l;

        save(IndexPartitionEntity2);

        Query query = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("indexVal", 6l)));
        query.setPartition(2l);

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 1);
    }

    @Test
    public void bTestUpdateQueryPartitionEntityWithIndexNoDefinedPartitionInQuery() throws EntityException
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);

        entities.partition.IndexPartitionEntity IndexPartitionEntity2 = new IndexPartitionEntity();
        IndexPartitionEntity2.id = 2l;
        IndexPartitionEntity2.partitionId = 2l;
        IndexPartitionEntity2.indexVal = 5l;

        save(IndexPartitionEntity2);

        Query query = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("indexVal", 6l)));

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 2);
    }

    @Test
    public void bTestUpdatePartitionField() throws EntityException
    {
        IndexPartitionEntity IndexPartitionEntity = new IndexPartitionEntity();
        IndexPartitionEntity.id = 1l;
        IndexPartitionEntity.partitionId = 3l;
        IndexPartitionEntity.indexVal = 5l;

        save(IndexPartitionEntity);

        entities.partition.IndexPartitionEntity IndexPartitionEntity2 = new IndexPartitionEntity();
        IndexPartitionEntity2.id = 2l;
        IndexPartitionEntity2.partitionId = 2l;
        IndexPartitionEntity2.indexVal = 5l;

        save(IndexPartitionEntity2);

        Query query = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 5l));
        query.setUpdates(Arrays.asList(new AttributeUpdate("partitionId", 5l), new AttributeUpdate("indexVal", 6l)));

        int results = manager.executeUpdate(query);
        Assert.assertTrue(results == 2);

        Query query2 = new Query(entities.partition.IndexPartitionEntity.class, new QueryCriteria("indexVal", QueryCriteriaOperator.EQUAL, 6l));
        query2.setPartition(5l);
        List result = manager.executeQuery(query2);

        Assert.assertTrue(result.size() == 2);
    }

}
