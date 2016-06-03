package embedded.stream;

import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.stream.QueryStream;
import embedded.base.BaseTest;
import entities.identifiers.ImmutableSequenceIdentifierEntityForDelete;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by tosborn1 on 6/2/16.
 */
public class EntityStreamTest extends BaseTest
{
    @Before
    public void before() throws EntityException
    {
        initialize();
    }

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Test
    public void testBasicQueryStream() throws EntityException
    {
        ImmutableSequenceIdentifierEntityForDelete testEntity = new ImmutableSequenceIdentifierEntityForDelete();
        testEntity.correlation = 1;
        save(testEntity);

        Query query = new Query(ImmutableSequenceIdentifierEntityForDelete.class, new QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0));

        manager.stream(query, (entity, persistenceManager) -> {
            ((ImmutableSequenceIdentifierEntityForDelete)entity).correlation = 2;
        });
    }

    @Test
    public void testBasicQueryStreamAndThen() throws EntityException
    {
        ImmutableSequenceIdentifierEntityForDelete testEntity = new ImmutableSequenceIdentifierEntityForDelete();
        testEntity.correlation = 1;
        save(testEntity);

        Query query = new Query(ImmutableSequenceIdentifierEntityForDelete.class, new QueryCriteria("correlation", QueryCriteriaOperator.GREATER_THAN, 0));

        QueryStream modifyStream = (entity, persistenceManager) -> {
            ((ImmutableSequenceIdentifierEntityForDelete)entity).correlation = 2;
        };

        modifyStream = modifyStream.andThen((entity, persistenceManager) -> {
            try {
                persistenceManager.saveEntity((IManagedEntity)entity);
            } catch (EntityException e) {}
        });

        manager.stream(query, modifyStream);
    }
}
