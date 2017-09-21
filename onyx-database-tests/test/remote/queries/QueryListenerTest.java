package remote.queries;

import category.RemoteServerTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryListener;
import entities.SimpleEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import remote.base.RemoteBaseTest;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by tosborn1 on 3/27/17.
 *
 * This unit test covers registered query listeners and the events
 */
@Category(value = RemoteServerTests.class)
public class QueryListenerTest extends RemoteBaseTest {

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

    }

    /**
     * Test a query is un-subscribed correctly
     */
    @Test
    public void testUnsubscribe() throws OnyxException, InterruptedException {
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity items) {
                assert false;
            }

            @Override
            public void onItemAdded(IManagedEntity items) {
                assert false;
            }

            @Override
            public void onItemRemoved(IManagedEntity items) {
                assert false;
            }
        });

        manager.executeQuery(query);

        manager.removeChangeListener(query);


        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("23");
        simpleEntity.setName("23");
        manager.saveEntity(simpleEntity);

        // Ensure it is not duplicating listeners
        manager.executeQuery(query);

        Thread.sleep(1000);


    }

    /**
     * Test an entity correctly hits an insert listener
     */
    @Test
    public void testInsert() throws OnyxException, InterruptedException {
        final AtomicBoolean pass = new AtomicBoolean(false);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemAdded(IManagedEntity entity) {
                pass.set(true);
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("22");
                assert ((SimpleEntity) entity).getName().equals("22");
                countDownLatch.countDown();
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

        countDownLatch.await(5, TimeUnit.SECONDS);

        assert pass.get();
    }

    /**
     * Test an entity correctly hits an update listener
     */
    @Test
    public void testUpdate() throws OnyxException, InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final AtomicBoolean pass = new AtomicBoolean(false);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                pass.set(true);
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("1");
                assert ((SimpleEntity) entity).getName().equals("2");
                countDownLatch.countDown();
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

        countDownLatch.await(5, TimeUnit.SECONDS);

        assert pass.get();
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    public void testDelete() throws OnyxException, InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("55");
        simpleEntity.setName("2");
        manager.saveEntity(simpleEntity);

        final AtomicBoolean pass = new AtomicBoolean(false);
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
                pass.set(true);
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("55");
                assert ((SimpleEntity) entity).getName().equals("2");
                countDownLatch.countDown();
            }
        });

        manager.executeQuery(query);

        simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("55");
        simpleEntity.setName("2");
        manager.deleteEntity(simpleEntity);

        countDownLatch.await(5, TimeUnit.SECONDS);

        assert pass.get();
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

    }

    /**
     * Test a query is un-subscribed correctly
     */
    @Test
    public void testUnsubscribeListen() throws OnyxException, InterruptedException {
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity items) {
                assert false;
            }

            @Override
            public void onItemAdded(IManagedEntity items) {
                assert false;
            }

            @Override
            public void onItemRemoved(IManagedEntity items) {
                assert false;
            }
        });

        manager.listen(query);

        manager.removeChangeListener(query);

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("23");
        simpleEntity.setName("23");
        manager.saveEntity(simpleEntity);


        Thread.sleep(1000);

    }

    /**
     * Test an entity correctly hits an insert listener
     */
    @Test
    public void testInsertListen() throws OnyxException, InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final AtomicBoolean pass = new AtomicBoolean(false);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                assert false;
            }

            @Override
            public void onItemAdded(IManagedEntity entity) {
                pass.set(true);
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("220");
                assert ((SimpleEntity) entity).getName().equals("22");
                countDownLatch.countDown();
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

        assert countDownLatch.await(1, TimeUnit.SECONDS);

        assert pass.get();

    }

    /**
     * Test an entity correctly hits an update listener
     */
    @Test
    public void testUpdateListen() throws OnyxException, InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("11");
        simpleEntity.setName("2");
        manager.saveEntity(simpleEntity);

        final AtomicBoolean pass = new AtomicBoolean(false);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener<IManagedEntity>() {

            @Override
            public void onItemUpdated(IManagedEntity entity) {
                pass.set(true);
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("11");
                assert ((SimpleEntity) entity).getName().equals("2");
                countDownLatch.countDown();
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

        assert countDownLatch.await(1, TimeUnit.SECONDS);
        assert pass.get();
    }

    /**
     * Test an entity correctly hits an delete listener
     */
    @Test
    public void testDeleteListen() throws OnyxException, InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("12");
        simpleEntity.setName("2");
        manager.saveEntity(simpleEntity);

        final AtomicBoolean pass = new AtomicBoolean(false);
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
                pass.set(true);
                assert entity instanceof SimpleEntity;
                assert ((SimpleEntity) entity).getSimpleId().equals("12");
                assert ((SimpleEntity) entity).getName().equals("2");
                countDownLatch.countDown();
            }
        });

        manager.listen(query);

        simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("12");
        simpleEntity.setName("2");
        manager.deleteEntity(simpleEntity);

        assert countDownLatch.await(1, TimeUnit.SECONDS);

        assert pass.get();
    }
}

