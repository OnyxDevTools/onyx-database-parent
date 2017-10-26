package embedded.queries;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.interactors.cache.data.CachedResults;
import com.onyx.persistence.query.QueryListener;
import embedded.base.BaseTest;
import entities.PageAnalytic;
import entities.SimpleEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Tim Osborn on 3/27/17.
 *
 * This unit test covers registered query listeners and the events
 */
@Category(value = EmbeddedDatabaseTests.class)
public class QueryListenerTest extends BaseTest {

    @After
    public void after() throws IOException {
        shutdown();
    }

    @Before
    public void before() throws OnyxException {
        initialize();
        seedData();
    }

    public void seedData() throws OnyxException
    {
        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("1");
        simpleEntity.setName("1");
        manager.saveEntity(simpleEntity);
    }

    /**
     * Test a query is subscribed correctly
     */
    @Test
    public void testSubscribe() throws OnyxException
    {
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity items) {

            }

            @Override
            public void onItemAdded(IManagedEntity items) {

            }

            @Override
            public void onItemRemoved(IManagedEntity items) {

            }
        });

        manager.executeQuery(query);

        CachedResults results = manager.getContext().getQueryCacheInteractor().getCachedQueryResults(query);
        assert results.getListeners().size() == 1;

        // Ensure it is not duplicating listeners
        manager.executeQuery(query);
        assert results.getListeners().size() == 1;
    }

    /**
     * Test a query is un-subscribed correctly
     */
    @Test
    public void testUnsubscribe() throws OnyxException
    {
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity items) {

            }

            @Override
            public void onItemAdded(IManagedEntity items) {

            }

            @Override
            public void onItemRemoved(IManagedEntity items) {

            }
        });

        manager.executeQuery(query);

        CachedResults results = manager.getContext().getQueryCacheInteractor().getCachedQueryResults(query);
        assert results.getListeners().size() == 1;

        manager.removeChangeListener(query);

        assert results.getListeners().size() == 0;

        // Ensure it is not duplicating listeners
        manager.executeQuery(query);
        assert results.getListeners().size() == 1;

    }

    /**
     * Test an entity correctly hits an insert listener
     */
    @Test
    public void testInsert() throws OnyxException, InterruptedException
    {
        final CountDownLatch pass = new CountDownLatch(1);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemAdded(IManagedEntity entity) {
                pass.countDown();
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("22");
                assert ((SimpleEntity) entity).getName().equals("22");
            }

            @Override
            public void onItemRemoved(IManagedEntity entity) {
                assert false;
            }
        });

        manager.executeQuery(query);

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("22");
        simpleEntity.setName("22");
        manager.saveEntity(simpleEntity);

        assert pass.await(1, TimeUnit.SECONDS);

    }

    /**
     * Test an entity correctly hits an update listener
     */
    @Test
    public void testUpdate() throws OnyxException, InterruptedException
    {
        final CountDownLatch pass = new CountDownLatch(1);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                pass.countDown();
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("1");
                assert ((SimpleEntity) entity).getName().equals("2");
            }

            @Override
            public void onItemAdded(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemRemoved(IManagedEntity entity) {
                assert false;
            }
        });

        manager.executeQuery(query);

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("1");
        simpleEntity.setName("2");
        manager.saveEntity(simpleEntity);

        assert pass.await(1, TimeUnit.SECONDS);
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    public void testDelete() throws OnyxException, InterruptedException
    {
        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("55");
        simpleEntity.setName("2");
        manager.saveEntity(simpleEntity);

        final CountDownLatch pass = new CountDownLatch(1);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemAdded(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemRemoved(IManagedEntity entity) {
                pass.countDown();
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("55");
                assert ((SimpleEntity) entity).getName().equals("2");
            }
        });

        manager.executeQuery(query);

        simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("55");
        simpleEntity.setName("2");
        manager.deleteEntity(simpleEntity);

        assert pass.await(1, TimeUnit.SECONDS);
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    public void testExecuteDelete() throws OnyxException, InterruptedException
    {
        PageAnalytic analytic = new PageAnalytic();
        analytic.setAgent("ME");
        analytic.setHttpStatus(200);
        analytic.setIpAddress("2929292.234234");
        analytic.setLoadTime(211);
        analytic.setMonthYear("20173");
        analytic.setPath("/path");

        manager.saveEntity(analytic);

        final CountDownLatch pass = new CountDownLatch(1);

        Query query = new Query();
        query.setEntityType(PageAnalytic.class);
        query.setQueryOrders(Arrays.asList(new QueryOrder("pageLoadId", false)));

        QueryCriteria notAssetCriteria = new QueryCriteria("path", QueryCriteriaOperator.NOT_CONTAINS, ".css")
                .and("path", QueryCriteriaOperator.NOT_CONTAINS, ".js")
                .and("path", QueryCriteriaOperator.NOT_EQUAL, "not-found")
                .and("ipAddress", QueryCriteriaOperator.NOT_STARTS_WITH, "/192.168.86")
                .and("agent", QueryCriteriaOperator.NOT_CONTAINS, "bot")
                .and("monthYear", QueryCriteriaOperator.EQUAL, "20173")
                .and("requestDate", QueryCriteriaOperator.GREATER_THAN, new java.util.Date(1492819200000L));
        query.setCriteria(notAssetCriteria);

        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemAdded(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemRemoved(IManagedEntity entity) {
                pass.countDown();
            }
        });

        manager.executeQuery(query);
        manager.executeDelete(query);
        List results = manager.executeQuery(query);

        assert results.size() == 0;
        assert pass.await(1, TimeUnit.SECONDS);
    }


    /**
     * Test a query is subscribed correctly
     */
    @Test
    public void testSubscribeListen() throws OnyxException
    {
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity items) {

            }

            @Override
            public void onItemAdded(IManagedEntity items) {

            }

            @Override
            public void onItemRemoved(IManagedEntity items) {

            }
        });

        manager.listen(query);

        CachedResults results = manager.getContext().getQueryCacheInteractor().getCachedQueryResults(query);
        assert results.getListeners().size() == 1;

        // Ensure it is not duplicating listeners
        manager.executeQuery(query);
        assert results.getListeners().size() == 1;
    }

    /**
     * Test a query is un-subscribed correctly
     */
    @Test
    public void testUnsubscribeListen() throws OnyxException
    {
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity items) {

            }

            @Override
            public void onItemAdded(IManagedEntity items) {

            }

            @Override
            public void onItemRemoved(IManagedEntity items) {

            }
        });

        manager.listen(query);

        CachedResults results = manager.getContext().getQueryCacheInteractor().getCachedQueryResults(query);
        assert results.getListeners().size() == 1;

        manager.removeChangeListener(query);

        assert results.getListeners().size() == 0;

        // Ensure it is not duplicating listeners
        manager.executeQuery(query);
        assert results.getListeners().size() == 1;

    }

    /**
     * Test an entity correctly hits an insert listener
     */
    @Test
    public void testInsertListen() throws OnyxException, InterruptedException
    {
        final CountDownLatch pass = new CountDownLatch(1);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemAdded(IManagedEntity entity) {
                pass.countDown();
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("220");
                assert ((SimpleEntity) entity).getName().equals("22");
            }

            @Override
            public void onItemRemoved(IManagedEntity entity) {
                assert false;
            }
        });

        manager.listen(query);

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("220");
        simpleEntity.setName("22");
        manager.saveEntity(simpleEntity);

        assert pass.await(1, TimeUnit.SECONDS);

    }

    /**
     * Test an entity correctly hits an update listener
     */
    @Test
    public void testUpdateListen() throws OnyxException, InterruptedException {
        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("11");
        simpleEntity.setName("2");
        manager.saveEntity(simpleEntity);

        final CountDownLatch pass = new CountDownLatch(1);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                pass.countDown();
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("11");
                assert ((SimpleEntity) entity).getName().equals("2");
            }

            @Override
            public void onItemAdded(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemRemoved(IManagedEntity entity) {
                assert false;
            }
        });

        manager.listen(query);

        simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("11");
        simpleEntity.setName("2");
        manager.saveEntity(simpleEntity);

        assert pass.await(1, TimeUnit.SECONDS);
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    public void testDeleteListen() throws OnyxException, InterruptedException {
        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("12");
        simpleEntity.setName("2");
        manager.saveEntity(simpleEntity);

        final CountDownLatch pass = new CountDownLatch(1);

        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemAdded(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemRemoved(IManagedEntity entity) {
                pass.countDown();
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("12");
                assert ((SimpleEntity) entity).getName().equals("2");
            }
        });

        manager.listen(query);

        simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("12");
        simpleEntity.setName("2");
        manager.deleteEntity(simpleEntity);

        assert pass.await(1, TimeUnit.SECONDS);
    }
}
