import com.onyx.entity.SystemPartitionEntry;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Created by tosborn1 on 2/24/17.
 *
 * Sample test to generate a decent load
 */
@SuppressWarnings("unused")
@Ignore
public class AWSTest {

    @Test
    public void testAWS() throws OnyxException
    {
        PersistenceManagerFactory factory = new RemotePersistenceManagerFactory("onx://localhost:8080");
        factory.initialize();
        PersistenceManager manager = factory.getPersistenceManager();
        for(int i = 0; i < 10000000; i++)
        {
            SystemPartitionEntry entry = new SystemPartitionEntry();
            manager.saveEntity(entry);
        }
    }
}
