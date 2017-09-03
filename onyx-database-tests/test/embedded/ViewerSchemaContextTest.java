package embedded;

import category.EmbeddedDatabaseTests;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.ViewerSchemaContext;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by timothy.osborn on 3/6/15.
 */

@Category({ EmbeddedDatabaseTests.class })
public class ViewerSchemaContextTest
{
    protected static final String DATABASE_LOCATION = "C:/Sandbox/Onyx/Tests/onyx.oxd";

    @Test
    public void testContextInit()
    {
        SchemaContext context = new ViewerSchemaContext(DATABASE_LOCATION);
        PersistenceManagerFactory persistenceManagerFactory = new EmbeddedPersistenceManagerFactory(DATABASE_LOCATION);
        persistenceManagerFactory.setSchemaContext(context);

        try
        {

            Class clazz = Class.forName("entities.AllAttributeEntity");
            Object obj = clazz.newInstance();

        } catch (ClassNotFoundException e)
        {
            Assert.fail("Class did not load");
        } catch (InstantiationException e)
        {
            Assert.fail("Class did not load");
        } catch (IllegalAccessException e)
        {
            Assert.fail("Class did not load");
        }
    }
}
