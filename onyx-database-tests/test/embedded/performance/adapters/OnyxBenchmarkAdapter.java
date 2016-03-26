package embedded.performance.adapters;

import com.onyx.exception.EntityException;
import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import com.onyx.persistence.manager.impl.EmbeddedPersistenceManager;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import embedded.performance.framework.IBenchmarkAdapter;
import embedded.performance.types.OnyxEntity;

import java.io.File;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by cosbor11 on 1/6/2015.
 */
public class OnyxBenchmarkAdapter implements IBenchmarkAdapter<OnyxEntity> {

    protected PersistenceManager manager;
    protected SchemaContext context;
    protected PersistenceManagerFactory factory;
    protected final SecureRandom random = new SecureRandom();

    protected static final String DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/performance/onyx.oxd";


    @Override
    public void initialize() {
        try
        {
            if (context == null)
            {
                factory = new EmbeddedPersistenceManagerFactory();
                factory.setDatabaseLocation(DATABASE_LOCATION);
                factory.initialize();

                context = factory.getSchemaContext();

                manager = new EmbeddedPersistenceManager();
                manager.setContext(factory.getSchemaContext());
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        try {
            if (factory != null) {
                factory.close();
            }
        }catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    private static void delete(File f)
    {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        f.delete();
    }

    @Override
    public void clean() {
        close();
        File database = new File(DATABASE_LOCATION);
        if(database != null && database.exists())
        {
            delete(database);
        }
        database.delete();
    }

    @Override
    public void populateRecord(OnyxEntity record, int i)
    {
        record.id = new BigInteger(130, random).toString(32);
        record.longValue = 4l;
        record.longPrimitive = 3l;
        record.stringValue = new Integer(i).toString() + "_string";
        record.dateValue = new Date(1483736263743l);
        record.doublePrimitive = 342.23;
        record.doubleValue = 232.2;
        record.booleanPrimitive = true;
        record.booleanValue = false;
    }

    @Override
    public long timeToCreateXRecords(int x)
    {

        List<IManagedEntity> entities = new ArrayList<>();


        for (int i = 0; i <= x; i++)
        {
            OnyxEntity entity = new OnyxEntity();
            populateRecord(entity, i);
            entities.add(entity);
        }

        long time = System.currentTimeMillis();
        try
        {
            manager.saveEntities(entities);  //executes a batch insert
        } catch (EntityException e)
        {
            e.printStackTrace();
        }
        long after = System.currentTimeMillis();

        return after - time;
    }

    @Override
    public long timeToFetchXRecords(int x)
    {
        return 0;
    }
}
