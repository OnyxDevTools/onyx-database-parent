package remote.save;

import category.RemoteServerTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import entities.identifiers.DateIdentifierEntity;
import entities.identifiers.IntegerIdentifierEntity;
import entities.identifiers.MutableIntegerIdentifierEntity;
import entities.identifiers.StringIdentifierEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import remote.base.RemoteBaseTest;

import java.io.IOException;
import java.util.Date;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({ RemoteServerTests.class })
public class SaveHashIdentifierTest extends RemoteBaseTest
{

    @Before
    public void before() throws InitializationException, InterruptedException
    {
        initialize();
    }

    @After
    public void after() throws IOException
    {
        shutdown();
    }

    @Test
    public void aTestSaveStringHashIndex()
    {
        StringIdentifierEntity entity = new StringIdentifierEntity();
        entity.identifier = "ABSCStringID1";
        entity.correlation = 1;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        StringIdentifierEntity entity2 = new StringIdentifierEntity();
        entity2.identifier = "ABSCStringID1";
        try {
            entity2 = (StringIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("String Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 1);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void bTestUpdateStringHashIndex2()
    {
        StringIdentifierEntity entity = new StringIdentifierEntity();
        entity.identifier = "ABSCStringID1";
        entity.correlation = 2;
        try {
            entity = (StringIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        assertEquals(entity.correlation, 1);

        StringIdentifierEntity entity2 = new StringIdentifierEntity();
        entity2.identifier = "ABSCStringID1";
        entity2.correlation = 3;
        try {
            manager.saveEntity(entity2);
            entity = (StringIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("String Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 3);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void cTestSaveStringHashIndex2()
    {
        StringIdentifierEntity entity = new StringIdentifierEntity();
        entity.identifier = "ASDVF*32234";
        entity.correlation = 2;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        StringIdentifierEntity entity2 = new StringIdentifierEntity();
        entity2.identifier = "ASDVF*32234";
        try {
            entity2 = (StringIdentifierEntity) manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("String Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 2);
        assertEquals(entity.correlation, entity2.correlation);
    }


    @Test
    public void dTestSaveIntegerHashIndex()
    {
        IntegerIdentifierEntity entity = new IntegerIdentifierEntity();
        entity.identifier = 2;
        entity.correlation = 5;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        IntegerIdentifierEntity entity2 = new IntegerIdentifierEntity();
        entity2.identifier = 2;
        try {
            entity2 = (IntegerIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 5);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void eTestSaveIntegerHashIndex()
    {
        IntegerIdentifierEntity entity = new IntegerIdentifierEntity();
        entity.identifier = 4;
        entity.correlation = 6;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        IntegerIdentifierEntity entity2 = new IntegerIdentifierEntity();
        entity2.identifier = 4;
        try {
            entity2 = (IntegerIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 6);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void fTestSaveIntegerHashIndex()
    {
        IntegerIdentifierEntity entity = new IntegerIdentifierEntity();
        entity.identifier = 1;
        entity.correlation = 7;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        IntegerIdentifierEntity entity2 = new IntegerIdentifierEntity();
        entity2.identifier = 1;
        try {
            entity2 = (IntegerIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 7);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void gTestUpdateIntegerHashIndex()
    {
        IntegerIdentifierEntity entity = new IntegerIdentifierEntity();
        entity.identifier = 4;
        entity.correlation = 2;
        try {
            entity = (IntegerIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        assertEquals(entity.correlation, 6);
        IntegerIdentifierEntity entity2 = new IntegerIdentifierEntity();
        entity2.identifier = 4;
        entity2.correlation = 22;
        try {
            manager.saveEntity(entity2);
            entity = (IntegerIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 22);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void hTestSaveIntegerHashIndex()
    {
        MutableIntegerIdentifierEntity entity = new MutableIntegerIdentifierEntity();
        entity.identifier = 2;
        entity.correlation = 5;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        MutableIntegerIdentifierEntity entity2 = new MutableIntegerIdentifierEntity();
        entity2.identifier = 2;
        try {
            entity2 = (MutableIntegerIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 5);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void iTestSaveIntegerHashIndex()
    {
        MutableIntegerIdentifierEntity entity = new MutableIntegerIdentifierEntity();
        entity.identifier = 4;
        entity.correlation = 6;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        MutableIntegerIdentifierEntity entity2 = new MutableIntegerIdentifierEntity();
        entity2.identifier = 4;
        try {
            entity2 = (MutableIntegerIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 6);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void jTestSaveIntegerHashIndex()
    {
        MutableIntegerIdentifierEntity entity = new MutableIntegerIdentifierEntity();
        entity.identifier = 1;
        entity.correlation = 7;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        MutableIntegerIdentifierEntity entity2 = new MutableIntegerIdentifierEntity();
        entity2.identifier = 1;
        try {
            entity2 = (MutableIntegerIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", (int)entity.identifier, (int)entity2.identifier);
        assertEquals(entity.correlation, 7);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void kTestUpdateIntegerHashIndex()
    {
        MutableIntegerIdentifierEntity entity = new MutableIntegerIdentifierEntity();
        entity.identifier = 4;
        entity.correlation = 2;
        try {
            entity = (MutableIntegerIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        assertEquals(entity.correlation, 6);
        MutableIntegerIdentifierEntity entity2 = new MutableIntegerIdentifierEntity();
        entity2.identifier = 4;
        entity2.correlation = 22;
        try {
            manager.saveEntity(entity2);
            entity = (MutableIntegerIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 22);
        assertEquals(entity.correlation, entity2.correlation);
    }


    @Test
    public void lTestSaveIntegerHashIndex()
    {
        DateIdentifierEntity entity = new DateIdentifierEntity();
        entity.identifier = new Date(1483736355234l);
        entity.correlation = 5;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        DateIdentifierEntity entity2 = new DateIdentifierEntity();
        entity2.identifier = new Date(entity.identifier.getTime());
        try {
            entity2 = (DateIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 5);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void mTestSaveIntegerHashIndex()
    {
        DateIdentifierEntity entity = new DateIdentifierEntity();
        entity.identifier = new Date(823482);
        entity.correlation = 6;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        DateIdentifierEntity entity2 = new DateIdentifierEntity();
        entity2.identifier = new Date(entity.identifier.getTime());
        try {
            entity2 = (DateIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 6);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void nTestSaveIntegerHashIndex()
    {
        DateIdentifierEntity entity = new DateIdentifierEntity();
        entity.identifier = new Date(23827);
        entity.correlation = 7;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        DateIdentifierEntity entity2 = new DateIdentifierEntity();
        entity2.identifier = new Date(entity.identifier.getTime());
        try {
            entity2 = (DateIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 7);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void oTestUpdateIntegerHashIndex()
    {
        DateIdentifierEntity entity = new DateIdentifierEntity();
        entity.identifier = new Date(823482);
        entity.correlation = 2;
        try {
            entity = (DateIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        assertEquals(entity.correlation, 6);
        DateIdentifierEntity entity2 = new DateIdentifierEntity();
        entity2.identifier = new Date(823482);
        entity2.correlation = 22;
        try {
            manager.saveEntity(entity2);
            entity = (DateIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertEquals("Integer Identifier Entity Not saved", entity.identifier, entity2.identifier);
        assertEquals(entity.correlation, 22);
        assertEquals(entity.correlation, entity2.correlation);
    }

}
