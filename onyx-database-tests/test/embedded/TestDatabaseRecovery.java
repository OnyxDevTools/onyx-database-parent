package embedded;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.update.AttributeUpdate;
import com.onyx.transaction.SaveTransaction;
import embedded.base.BaseTest;
import entities.AllAttributeEntity;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by tosborn1 on 3/25/16.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ EmbeddedDatabaseTests.class })
public class TestDatabaseRecovery extends BaseTest
{

    protected static final String DATABASE_LOCATION_RECOVERED = "C:/Sandbox/Onyx/Tests/recovered.oxd";
    protected static final String DATABASE_LOCATION_AMMENDED = "C:/Sandbox/Onyx/Tests/ammended.oxd";
    protected static final String DATABASE_LOCATION_BASE = "C:/Sandbox/Onyx/Tests/base.oxd";

    @BeforeClass
    public static void beforeClass()
    {
        delete(new File(DATABASE_LOCATION_RECOVERED));
        delete(new File(DATABASE_LOCATION_AMMENDED));
        delete(new File(DATABASE_LOCATION_BASE));
    }

    @AfterClass
    public static void afterClass()
    {
        delete(new File(DATABASE_LOCATION_RECOVERED));
        delete(new File(DATABASE_LOCATION_AMMENDED));
        delete(new File(DATABASE_LOCATION_BASE));
    }

    @Before
    public void before() throws InitializationException, InterruptedException
    {
        if (context == null) {
            factory = new EmbeddedPersistenceManagerFactory();
            factory.setDatabaseLocation(DATABASE_LOCATION_BASE);
            ((EmbeddedPersistenceManagerFactory) factory).setEnableJournaling(true);
            factory.initialize();

            context = factory.getSchemaContext();
            manager = factory.getPersistenceManager();
        }
    }

    @Test
    public void atestDatabaseRecovery() throws EntityException, IOException
    {
        this.populateTransactionData();

        EmbeddedPersistenceManagerFactory newFactory = new EmbeddedPersistenceManagerFactory();
        newFactory.setDatabaseLocation(DATABASE_LOCATION_RECOVERED);
        newFactory.initialize();

        SchemaContext newContext = newFactory.getSchemaContext();
        PersistenceManager newManager = newFactory.getPersistenceManager();

        newContext.getTransactionController().recoverDatabase(DATABASE_LOCATION_BASE + File.separator + "wal", transaction -> true);

        Assert.assertTrue(newManager.findById(AllAttributeEntity.class, "ASDFASDF100020") == null);
        Assert.assertTrue(newManager.findById(AllAttributeEntity.class, "ASDFASDF100") == null);

        Query deleteQuery = new Query(AllAttributeEntity.class, new QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 5000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 4000));
        List results = newManager.executeQuery(deleteQuery);
        Assert.assertTrue(results.size() == 0);

        Query updateQuery = new Query(AllAttributeEntity.class, new QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 90000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 80000)
        .and("doubleValue", QueryCriteriaOperator.EQUAL, 99.0d));
        results = newManager.executeQuery(updateQuery);
        Assert.assertTrue(results.size() == 9999);

        Query existsQuery = new Query();
        existsQuery.setEntityType(AllAttributeEntity.class);
        results = manager.executeQuery(existsQuery);

        int res = results.size();

        results = newManager.executeQuery(existsQuery);

        Assert.assertTrue(res == results.size());

        factory.close();
        newFactory.close();

    }

    @Test
    public void btestDatabaseApplyTransactions() throws EntityException, IOException
    {

        EmbeddedPersistenceManagerFactory newFactory = new EmbeddedPersistenceManagerFactory();
        newFactory.setDatabaseLocation(DATABASE_LOCATION_AMMENDED);
        newFactory.initialize();

        SchemaContext newContext = newFactory.getSchemaContext();
        PersistenceManager newManager = newFactory.getPersistenceManager();

        newContext.getTransactionController().applyTransactionLog(DATABASE_LOCATION_BASE + File.separator + "wal" + File.separator + "0.wal", transaction -> {
            if(transaction instanceof SaveTransaction)
                return true;

            return false;
        });

        Query existsQuery = new Query();
        existsQuery.setEntityType(AllAttributeEntity.class);
        List results = newManager.executeQuery(existsQuery);

        Assert.assertTrue(results.size() == 191557);

        newFactory.close();
    }

    protected void populateTransactionData() throws EntityException
    {

        AllAttributeEntity allAttributeEntity = null;

        for(int i = 0; i < 1000000; i++)
        {
            allAttributeEntity = new AllAttributeEntity();
            allAttributeEntity.doubleValue = 23d;
            allAttributeEntity.id = "ASDFASDF" + i;
            allAttributeEntity.dateValue = new Date();
            allAttributeEntity.intValue = i;
            manager.saveEntity(allAttributeEntity);
        }

        allAttributeEntity = new AllAttributeEntity();
        allAttributeEntity.id = "ASDFASDF100";

        manager.deleteEntity(allAttributeEntity);

        for(int i = 1000000; i < 1010000; i++)
        {
            allAttributeEntity = new AllAttributeEntity();
            allAttributeEntity.doubleValue = 23d;
            allAttributeEntity.id = "ASDFASDF" + i;
            allAttributeEntity.dateValue = new Date();
            allAttributeEntity.intValue = i;
            manager.saveEntity(allAttributeEntity);
        }

        allAttributeEntity = new AllAttributeEntity();
        allAttributeEntity.id = "ASDFASDF100020";

        manager.deleteEntity(allAttributeEntity);

        Query deleteQuery = new Query(AllAttributeEntity.class, new QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 5000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 4000));
        manager.executeDelete(deleteQuery);

        Query updateQuery = new Query(AllAttributeEntity.class, new QueryCriteria("intValue", QueryCriteriaOperator.LESS_THAN, 90000).and("intValue", QueryCriteriaOperator.GREATER_THAN, 80000));
        updateQuery.setUpdates(Arrays.asList(new AttributeUpdate("doubleValue", 99.0d)));
        manager.executeUpdate(updateQuery);

    }

}
