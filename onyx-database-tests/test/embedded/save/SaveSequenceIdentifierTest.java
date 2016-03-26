package embedded.save;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import embedded.base.BaseTest;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import entities.identifiers.ImmutableIntSequenceIdentifierEntity;
import entities.identifiers.ImmutableSequenceIdentifierEntity;
import entities.identifiers.MutableIntSequenceIdentifierEntity;
import entities.identifiers.MutableSequenceIdentifierEntity;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by timothy.osborn on 11/3/14.
 */
@Category({ EmbeddedDatabaseTests.class })
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SaveSequenceIdentifierTest extends BaseTest {

    @Before
    public void before() throws InitializationException, InterruptedException
    {
        initialize();
    }

    @After
    public void after() throws EntityException, IOException
    {
        shutdown();
    }

    @Test
    public void aTestSaveMutableSequenceIdentifierEntity()
    {

        MutableSequenceIdentifierEntity entity = new MutableSequenceIdentifierEntity();
        entity.correlation = 5;

        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        MutableSequenceIdentifierEntity entity2 = new MutableSequenceIdentifierEntity();
        entity2.identifier = 1l;
        try {
            entity2 = (MutableSequenceIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }
        assertTrue("Sequence Identifier should be greater than 0", entity.identifier > 0);
        assertEquals(entity.correlation, 5);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void bTestSaveImmutableSequenceIdentifierEntity() {

        ImmutableSequenceIdentifierEntity entity = new ImmutableSequenceIdentifierEntity();
        entity.correlation = 6;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        ImmutableSequenceIdentifierEntity entity2 = new ImmutableSequenceIdentifierEntity();
        entity2.identifier = 1l;
        try {
            entity2 = (ImmutableSequenceIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertTrue("Sequence Identifier should be greater than 0", entity.identifier > 0);
        assertEquals(entity.correlation, 6);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void cTestSaveMutableSequenceIdentifierEntityNext() {

        MutableSequenceIdentifierEntity entity = new MutableSequenceIdentifierEntity();
        entity.correlation = 7;

        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        MutableSequenceIdentifierEntity entity2 = new MutableSequenceIdentifierEntity();
        entity2.identifier = 2l;
        try {
            entity2 = (MutableSequenceIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }
        assertTrue("Sequence Identifier should be greater than 1", entity.identifier > 1);
        assertEquals(entity.correlation, 7);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void dTestSaveImmutableSequenceIdentifierEntityNext() {

        ImmutableSequenceIdentifierEntity entity = new ImmutableSequenceIdentifierEntity();
        entity.correlation = 9;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        ImmutableSequenceIdentifierEntity entity2 = new ImmutableSequenceIdentifierEntity();
        entity2.identifier = 2l;
        try {
            entity2 = (ImmutableSequenceIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertTrue("Sequence Identifier should be greater than 1", entity.identifier > 1);
        assertEquals(entity.correlation, 9);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void eTestSaveMutableSequenceIdentifierEntityUserDefined() {

        MutableSequenceIdentifierEntity entity = new MutableSequenceIdentifierEntity();
        entity.correlation = 8;
        entity.identifier = 3l;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        MutableSequenceIdentifierEntity entity2 = new MutableSequenceIdentifierEntity();
        entity2.identifier = 3l;
        try {
            entity2 = (MutableSequenceIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }
        assertTrue("Sequence Identifier should be greater than 1", entity.identifier == 3);
        assertEquals(entity.correlation, 8);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void fTestSaveImmutableSequenceIdentifierEntityUserDefined() {

        ImmutableSequenceIdentifierEntity entity = new ImmutableSequenceIdentifierEntity();
        entity.correlation = 9;
        entity.identifier = 3;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        ImmutableSequenceIdentifierEntity entity2 = new ImmutableSequenceIdentifierEntity();
        entity2.identifier = 3l;
        try {
            entity2 = (ImmutableSequenceIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertTrue("Sequence Identifier should be greater equal to 3", entity.identifier == 3);
        assertEquals(entity.correlation, 9);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void gTestSaveImmutableSequenceIdentifierEntityUserDefinedSkip() {

        ImmutableSequenceIdentifierEntity entity = new ImmutableSequenceIdentifierEntity();
        entity.correlation = 11;
        entity.identifier = 5;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        ImmutableSequenceIdentifierEntity entity2 = new ImmutableSequenceIdentifierEntity();
        entity2.identifier = 5l;
        try {
            entity2 = (ImmutableSequenceIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertTrue("Sequence Identifier should be greater equal to 5", entity2.identifier == 5);
        assertEquals(entity.correlation, 11);
        assertEquals(entity.correlation, entity2.correlation);

        entity = new ImmutableSequenceIdentifierEntity();
        entity.correlation = 12;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        entity2 = new ImmutableSequenceIdentifierEntity();
        entity2.identifier = 6l;
        try {
            entity2 = (ImmutableSequenceIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertTrue("Sequence Identifier should be equal to 6", entity2.identifier == 6);
        assertEquals(entity.correlation, 12);
        assertEquals(entity.correlation, entity2.correlation);

    }

    @Test
    public void hTestSaveMutableIntSequenceIdentifierEntity() {

        MutableIntSequenceIdentifierEntity entity = new MutableIntSequenceIdentifierEntity();
        entity.correlation = 5;

        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        MutableIntSequenceIdentifierEntity entity2 = new MutableIntSequenceIdentifierEntity();
        entity2.identifier = 1;
        try {
            entity2 = (MutableIntSequenceIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }
        assertTrue("Sequence Identifier should be greater than 0", entity.identifier > 0);
        assertEquals(entity.correlation, 5);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void iTestSaveImmutableIntSequenceIdentifierEntity() {

        ImmutableIntSequenceIdentifierEntity entity = new ImmutableIntSequenceIdentifierEntity();
        entity.correlation = 6;
        try {
            manager.saveEntity(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        ImmutableIntSequenceIdentifierEntity entity2 = new ImmutableIntSequenceIdentifierEntity();
        entity2.identifier = 1;
        try {
            entity2 = (ImmutableIntSequenceIdentifierEntity)manager.find(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        assertTrue("Sequence Identifier should be greater than 0", entity.identifier > 0);
        assertEquals(entity.correlation, 6);
        assertEquals(entity.correlation, entity2.correlation);
    }

    @Test
    public void jTestFindLastItem()
    {

        ImmutableSequenceIdentifierEntity entity = new ImmutableSequenceIdentifierEntity();
        entity.identifier = 6;

        try {
            entity = (ImmutableSequenceIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            Assert.fail("Failure to find entity");
        }

        assertEquals(entity.identifier, 6);
        assertEquals(entity.correlation, 12);
    }

    @Test
    public void kTestFindFirstItem()
    {

        ImmutableSequenceIdentifierEntity entity = new ImmutableSequenceIdentifierEntity();
        entity.identifier = 1;

        try {
            entity = (ImmutableSequenceIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            Assert.fail("Failure to find entity");
        }

        assertEquals(entity.identifier, 1);
        assertEquals(entity.correlation, 6);
    }

    @Test
    public void lTestUpdateImmutableSequenceIdentifierEntity() {

        ImmutableSequenceIdentifierEntity entity = new ImmutableSequenceIdentifierEntity();
        // This should be 9
        entity.correlation = 1;
        entity.identifier = 3;

        try {
            entity = (ImmutableSequenceIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        assertEquals(entity.correlation, 9);

        ImmutableSequenceIdentifierEntity entity2 = new ImmutableSequenceIdentifierEntity();
        entity2.identifier = 3l;
        entity2.correlation = 88;
        try {
            manager.saveEntity(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        try {
            entity = (ImmutableSequenceIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        assertEquals(entity.correlation, 88);
    }

    @Test
    public void mTestUpdateMutableSequenceIdentifierEntity() {

        MutableSequenceIdentifierEntity entity = new MutableSequenceIdentifierEntity();
        // This should be 9
        entity.correlation = 1;
        entity.identifier = 3l;

        try {
            entity = (MutableSequenceIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        assertEquals(entity.correlation, 8);

        MutableSequenceIdentifierEntity entity2 = new MutableSequenceIdentifierEntity();
        entity2.identifier = 3l;
        entity2.correlation = 87;
        try {
            manager.saveEntity(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        try {
            entity = (MutableSequenceIdentifierEntity)manager.find(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        assertEquals(entity.correlation, 87);
    }

    @Test
    public void nTestInsertInTheMiddle() {

        MutableSequenceIdentifierEntity entity = new MutableSequenceIdentifierEntity();
        entity.correlation = 1;
        entity.identifier = 100l;

        save(entity);

        try {
            manager.find(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }

        assertEquals(entity.correlation, 1);

        MutableSequenceIdentifierEntity entity2 = new MutableSequenceIdentifierEntity();
        entity2.identifier = 90l;
        entity2.correlation = 87;
        try {
            manager.saveEntity(entity2);
        } catch (EntityException e) {
            e.printStackTrace();
        }

        try {
            manager.find(entity);
        } catch (EntityException e) {
            fail(e.getMessage());
        }


        try {
            manager.find(entity2);
        } catch (EntityException e) {
            fail(e.getMessage());
        }
        assertEquals(entity2.correlation, 87);

    }
}
