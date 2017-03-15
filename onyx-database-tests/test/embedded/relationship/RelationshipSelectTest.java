package embedded.relationship;

import category.InMemoryDatabaseTests;
import com.onyx.exception.EntityException;
import com.onyx.exception.InvalidQueryException;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryPartitionMode;
import entities.Address;
import entities.Person;
import memory.base.BaseTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.List;

/**
 * Created by tosborn1 on 3/13/17.
 */
@Category({InMemoryDatabaseTests.class})
public class RelationshipSelectTest extends BaseTest {
    @Before
    public void before() throws EntityException {
        initialize();
    }

    @After
    public void after() throws IOException {
        shutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvalidQueryException() throws EntityException {
        for (int i = 0; i < 50; i++) {
            Person person = new Person();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new Address();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }

        Query query = new Query(Person.class, new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart"));
        query.setPartition("ASDF");
        List<Person> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
    }

    @Test(expected = InvalidQueryException.class)
    @SuppressWarnings("unchecked")
    public void testInsert() throws EntityException {
        for (int i = 0; i < 50; i++) {
            Person person = new Person();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new Address();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }


        QueryCriteria first = new QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian");
        QueryCriteria second = new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(Person.class, first.and(second));
        query.setPartition(QueryPartitionMode.ALL);
        List<Person> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testQuerySpecificPartition() throws EntityException {
        for (int i = 0; i < 50; i++) {
            Person person = new Person();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new Address();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }


        QueryCriteria first = new QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian");
        QueryCriteria second = new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(Person.class, first.and(second));
        query.setPartition("ASDF");
        List<Person> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
    }
}
