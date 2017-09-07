package memory.queries;

import category.InMemoryDatabaseTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import entities.SelectIdentifierTestEntity;
import memory.base.BaseTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;

/**
 * Created by tosborn1 on 3/22/17.
 */
@Category({InMemoryDatabaseTests.class})
public class SelectIndexTest extends BaseTest {

    @After
    public void after() throws IOException {
        shutdown();
    }

    @Before
    public void before() throws OnyxException {
        initialize();
        seedData();
    }

    private void seedData() throws OnyxException
    {
        SelectIdentifierTestEntity entity = new SelectIdentifierTestEntity();
        entity.id = 1L;
        entity.index = 1;
        entity.attribute = "1";

        manager.saveEntity(entity);

        entity = new SelectIdentifierTestEntity();
        entity.id = 2L;
        entity.index = 2;
        entity.attribute = "2";

        manager.saveEntity(entity);

        entity = new SelectIdentifierTestEntity();
        entity.id = 3L;
        entity.index = 3;
        entity.attribute = "3";

        manager.saveEntity(entity);

        entity = new SelectIdentifierTestEntity();
        entity.id = 4L;
        entity.index = 4;
        entity.attribute = "4";

        manager.saveEntity(entity);

        entity = new SelectIdentifierTestEntity();
        entity.id = 5L;
        entity.index = 5;
        entity.attribute = "5";

        manager.saveEntity(entity);

        entity = new SelectIdentifierTestEntity();
        entity.id = 6L;
        entity.index = 6;
        entity.attribute = "6";

        manager.saveEntity(entity);

        entity = new SelectIdentifierTestEntity();
        entity.id = 7L;
        entity.index = 7;
        entity.attribute = "7";

        manager.saveEntity(entity);

        entity = new SelectIdentifierTestEntity();
        entity.id = 8L;
        entity.index = 8;
        entity.attribute = "8";

        manager.saveEntity(entity);

        entity = new SelectIdentifierTestEntity();
        entity.id = 9L;
        entity.index = 9;
        entity.attribute = "9";

        manager.saveEntity(entity);

        entity = new SelectIdentifierTestEntity();
        entity.id = 10L;
        entity.index = 10;
        entity.attribute = "10";

        manager.saveEntity(entity);

    }

    @Test
    public void testIdentifierAndCritieria() throws OnyxException
    {
        QueryCriteria first = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria second = new QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L);
        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(first.and(second));

        assert manager.executeQuery(query).size() == 2;
    }

    @Test
    public void testIdentifierOrCritieria() throws OnyxException
    {
        QueryCriteria first = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria second = new QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L);
        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(first.or(second));

        assert manager.executeQuery(query).size() == 6;
    }

    @Test
    public void testIdentifierCompoundCritieria() throws OnyxException
    {
        QueryCriteria first = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria second = new QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 3L);
        QueryCriteria third = new QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L);
        QueryCriteria fourth = new QueryCriteria("index", QueryCriteriaOperator.EQUAL, 2L);

        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(first.or(second.and(third.or(fourth))));

        assert manager.executeQuery(query).size() == 6;
    }

    @Test
    public void testIdentifierAndCritieriaWithNot() throws OnyxException
    {
        QueryCriteria first = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria second = new QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L);
        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(first.and(second.not()));

        assert manager.executeQuery(query).size() == 3;
    }

    @Test
    public void testIdentifierAndCritieriaWithNotGroup() throws OnyxException
    {
        QueryCriteria first = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria second = new QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L);
        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(first.and(second).not());

        assert manager.executeQuery(query).size() == 8;
    }

    @Test
    public void testIdentifierOrCritieriaWithNot() throws OnyxException
    {
        QueryCriteria first = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria second = new QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L);
        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(first.or(second.not()));

        assert manager.executeQuery(query).size() == 3;
    }

    @Test
    public void testIdentifierOrCritieriaWithNotGroup() throws OnyxException
    {
        QueryCriteria first = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria second = new QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 8L);
        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(first.or(second).not());

        assert manager.executeQuery(query).size() == 0;
    }

    @Test
    public void testIdentifierCompoundCritieriaWithNot() throws OnyxException
    {
        QueryCriteria first = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria second = new QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 3L);
        QueryCriteria third = new QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L);
        QueryCriteria fourth = new QueryCriteria("index", QueryCriteriaOperator.EQUAL, 2L);

        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(first.or(second.and(third.or(fourth).not())));

        assert manager.executeQuery(query).size() == 6;
    }

    @Test
    public void testIdentifierCompoundCritieriaWithNotFullScan() throws OnyxException
    {
        QueryCriteria first = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria second = new QueryCriteria("index", QueryCriteriaOperator.LESS_THAN, 3L);
        QueryCriteria third = new QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L);
        QueryCriteria fourth = new QueryCriteria("index", QueryCriteriaOperator.EQUAL, 2L);

        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(first.or(second.and(third.or(fourth))).not());


        assert manager.executeQuery(query).size() == 4;
    }

    @Test
    public void testMixMatchOfCritieria() throws OnyxException
    {
        QueryCriteria first = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria second = new QueryCriteria("id", QueryCriteriaOperator.LESS_THAN, 3L);
        QueryCriteria third = new QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L);
        QueryCriteria fourth = new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L);

        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(first.or(second.and(third.or(fourth).not())));

        assert manager.executeQuery(query).size() == 6;
    }

    @Test
    public void testMixMatchOfCritieriaIncludeFull() throws OnyxException
    {
        QueryCriteria indexGreaterThan5 = new QueryCriteria("index", QueryCriteriaOperator.GREATER_THAN, 5L);
        QueryCriteria idLessThan3 = new QueryCriteria("id", QueryCriteriaOperator.LESS_THAN, 3L);
        QueryCriteria indexEqualTo3 = new QueryCriteria("index", QueryCriteriaOperator.EQUAL, 3L);
        QueryCriteria idEqualTo2 = new QueryCriteria("id", QueryCriteriaOperator.EQUAL, 2L);
        QueryCriteria attributeEq3 = new QueryCriteria("attribute", QueryCriteriaOperator.EQUAL, "3");
        QueryCriteria attributeEq2 = new QueryCriteria("attribute", QueryCriteriaOperator.EQUAL, "2");

        Query query = new Query();
        query.setEntityType(SelectIdentifierTestEntity.class);
        query.setCriteria(
                // Index Gt 5 6,7,8,9,10
                indexGreaterThan5.or(idLessThan3 // Index Gt 5 (6,7,8,9,10) or id less than 3 (1,2)
                    .and(
                        indexEqualTo3.or(idEqualTo2).not() // index eq 3 (3) or id equal 2 (2) // NOT!
                    ) //  Index Gt 5 (6,7,8,9,10) or id less than 3 (1,!2) = (6,7,8,9,10,1)
                    .and(
                        attributeEq3.or(attributeEq2)
                    )
                )
        );

        assert manager.executeQuery(query).size() == 5;
    }
}
