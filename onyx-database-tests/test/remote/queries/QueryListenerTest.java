package remote.queries;

import category.RemoteServerTests;
import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.query.Query;
import com.onyx.query.QueryListener;
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
    public void before() throws EntityException {
        initialize();
        seedData();
    }

    public void seedData() throws EntityException
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
    public void testSubscribe() throws EntityException
    {
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener() {

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
    public void testUnsubscribe() throws EntityException, InterruptedException {
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener() {

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
    public void testInsert() throws EntityException, InterruptedException {
        final AtomicBoolean pass = new AtomicBoolean(false);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener() {

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
    public void testUpdate() throws EntityException, InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final AtomicBoolean pass = new AtomicBoolean(false);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener() {

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
    public void testDelete() throws EntityException, InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        final AtomicBoolean pass = new AtomicBoolean(false);
        Query query = new Query(SimpleEntity.class);
        query.setChangeListener(new QueryListener() {

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
                assert ((SimpleEntity) entity).getSimpleId().equals("1");
                assert ((SimpleEntity) entity).getName().equals("2");
                countDownLatch.countDown();
            }
        });

        manager.executeQuery(query);

        SimpleEntity simpleEntity = new SimpleEntity();
        simpleEntity.setSimpleId("1");
        simpleEntity.setName("2");
        manager.deleteEntity(simpleEntity);

        countDownLatch.await(5, TimeUnit.SECONDS);

        assert pass.get();
    }
}

