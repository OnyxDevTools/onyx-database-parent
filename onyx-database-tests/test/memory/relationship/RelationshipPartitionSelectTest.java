package memory.relationship;

import category.InMemoryDatabaseTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.query.*;
import entities.Address;
import entities.Person;
import memory.base.BaseTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by Tim Osborn on 3/13/17.
 */
@Category({InMemoryDatabaseTests.class})
public class RelationshipPartitionSelectTest extends BaseTest {
    @Before
    public void before() throws OnyxException {
        initialize();
    }

    @After
    public void after() throws IOException {
        shutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInvalidQueryException() throws OnyxException {
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

    @Test
    @SuppressWarnings("unchecked")
    public void testInsert() throws OnyxException {
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
    public void testQuerySpecificPartition() throws OnyxException {
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

    @Test
    @SuppressWarnings("unchecked")
    public void testSelectAttribute() throws OnyxException {
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
        query.setSelections(Arrays.asList("firstName", "address.street"));
        query.setPartition("ASDF");
        List<Person> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSelectRelationship() throws OnyxException {
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
        query.setSelections(Arrays.asList("firstName", "address"));
        query.setPartition("ASDF");
        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
        assert addresses.get(0).get("address") instanceof Map;
        assert ((Map) addresses.get(0).get("address")).get("street").equals("Sluisvaart");

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToManySelectRelationship() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            Person person = new Person();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new Address();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);

            Person person2 = new Person();
            person2.firstName = "Timbob";
            person2.lastName = "Rooski" + i;
            person2.address = new Address();
            person2.address.id = person.address.id;
            person2.address.street = "Sluisvaart";
            person2.address.houseNr = 98;
            manager.saveEntity(person2);

            find(person2.address);
        }


        QueryCriteria first = new QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        QueryCriteria second = new QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob");
        Query query = new Query(Address.class, first.and(second));
        query.setSelections(Arrays.asList("id", "street", "occupants"));

        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
        assert addresses.get(0).get("occupants") instanceof List;
        assert ((List) addresses.get(0).get("occupants")).get(0) instanceof Map;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(0)).get("firstName") != null;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(1)).get("firstName") != null;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToManySelectRelationshipNoRelationshipCriteria() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            Person person = new Person();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new Address();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);

            Person person2 = new Person();
            person2.firstName = "Timbob";
            person2.lastName = "Rooski" + i;
            person2.address = new Address();
            person2.address.id = person.address.id;
            person2.address.street = "Sluisvaart";
            person2.address.houseNr = 98;
            manager.saveEntity(person2);

            find(person2.address);
        }


        QueryCriteria first = new QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(Address.class, first);
        query.setSelections(Arrays.asList("id", "street", "occupants"));

        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
        assert addresses.get(0).get("occupants") instanceof List;
        assert ((List) addresses.get(0).get("occupants")).get(0) instanceof Map;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(0)).get("firstName") != null;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(1)).get("firstName") != null;
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testQuerySpecificPartitionOrderBy() throws OnyxException {
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
        query.setQueryOrders(Arrays.asList(new QueryOrder("firstName"), new QueryOrder("address.street")));
        query.setPartition("ASDF");
        List<Person> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testSelectAttributeOrderBy() throws OnyxException {
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
        query.setSelections(Arrays.asList("firstName", "address.street"));
        query.setPartition("ASDF");
        query.setQueryOrders(Arrays.asList(new QueryOrder("firstName"), new QueryOrder("address.street")));
        List<Person> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSelectRelationshipOrderBy() throws OnyxException {
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
        query.setSelections(Arrays.asList("firstName", "address"));
        query.setPartition("ASDF");
        query.setQueryOrders(Arrays.asList(new QueryOrder("firstName"), new QueryOrder("address.street")));
        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
        assert addresses.get(0).get("address") instanceof Map;
        assert ((Map) addresses.get(0).get("address")).get("street").equals("Sluisvaart");

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToManySelectRelationshipOrderBy() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            Person person = new Person();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new Address();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);

            Person person2 = new Person();
            person2.firstName = "Timbob";
            person2.lastName = "Rooski" + i;
            person2.address = new Address();
            person2.address.id = person.address.id;
            person2.address.street = "Sluisvaart";
            person2.address.houseNr = 98;
            manager.saveEntity(person2);

            find(person2.address);
        }


        QueryCriteria first = new QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        QueryCriteria second = new QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob");
        Query query = new Query(Address.class, first.and(second));
        query.setSelections(Arrays.asList("id", "street", "occupants"));
        query.setQueryOrders(Arrays.asList(new QueryOrder("occupants.firstName")));

        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
        assert addresses.get(0).get("occupants") instanceof List;
        assert ((List) addresses.get(0).get("occupants")).get(0) instanceof Map;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(1)).get("firstName") != null;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(0)).get("firstName") != null;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToManySelectRelationshipNoRelationshipCriteriaOrderBy() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            Person person = new Person();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new Address();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);

            Person person2 = new Person();
            person2.firstName = "Timbob";
            person2.lastName = "Rooski" + i;
            person2.address = new Address();
            person2.address.id = person.address.id;
            person2.address.street = "Sluisvaart";
            person2.address.houseNr = 98;
            manager.saveEntity(person2);

            find(person2.address);
        }


        QueryCriteria first = new QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(Address.class, first);
        query.setSelections(Arrays.asList("id", "street", "occupants"));
        query.setQueryOrders(Arrays.asList(new QueryOrder("street")));

        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() == 50;
        assert addresses.get(0).get("occupants") instanceof List;
        assert ((List) addresses.get(0).get("occupants")).get(0) instanceof Map;
    }
}
