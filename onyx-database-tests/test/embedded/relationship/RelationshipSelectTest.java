package embedded.relationship;

import category.EmbeddedDatabaseTests;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.query.*;
import embedded.base.BaseTest;
import entities.AddressNoPartition;
import entities.PersonNoPartition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by tosborn1 on 3/17/17.
 */
@Category({EmbeddedDatabaseTests.class})
public class RelationshipSelectTest extends BaseTest {

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
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }

        Query query = new Query(PersonNoPartition.class, new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart"));
        List<PersonNoPartition> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDistinctValues() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }

        Query query = new Query(PersonNoPartition.class, new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart"));
        query.setDistinct(true);
        query.setSelections(Arrays.asList("firstName"));
        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() == 2;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testQuerySpecificPartition() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }


        QueryCriteria first = new QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian");
        QueryCriteria second = new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(PersonNoPartition.class, first.and(second));
        List<PersonNoPartition> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSelectAttribute() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }


        QueryCriteria first = new QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian");
        QueryCriteria second = new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(PersonNoPartition.class, first.and(second));
        query.setSelections(Arrays.asList("firstName", "address.street"));
        List<PersonNoPartition> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSelectRelationship() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }


        QueryCriteria first = new QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian");
        QueryCriteria second = new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(PersonNoPartition.class, first.and(second));
        query.setSelections(Arrays.asList("firstName", "address"));
        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
        assert addresses.get(0).get("address") instanceof Map;
        assert ((Map) addresses.get(0).get("address")).get("street").equals("Sluisvaart");

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToManySelectRelationship() throws OnyxException {
        manager.executeDelete(new Query(PersonNoPartition.class));
        manager.executeDelete(new Query(AddressNoPartition.class));
        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);

            PersonNoPartition person2 = new PersonNoPartition();
            person2.firstName = "Timbob";
            person2.lastName = "Rooski" + i;
            person2.address = new AddressNoPartition();
            person2.address.id = person.address.id;
            person2.address.street = "Sluisvaart";
            person2.address.houseNr = 98;
            manager.saveEntity(person2);

            find(person2.address);
        }


        QueryCriteria first = new QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        QueryCriteria second = new QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob");
        Query query = new Query(AddressNoPartition.class, first.and(second));
        query.setSelections(Arrays.asList("id", "street", "occupants"));

        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
        assert addresses.get(0).get("occupants") instanceof List;
        assert ((List) addresses.get(0).get("occupants")).get(0) instanceof Map;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(0)).get("firstName") != null;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(1)).get("firstName") != null;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToManySelectRelationshipNoRelationshipCriteria() throws OnyxException {
        manager.executeDelete(new Query(PersonNoPartition.class));
        manager.executeDelete(new Query(AddressNoPartition.class));
        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);

            PersonNoPartition person2 = new PersonNoPartition();
            person2.firstName = "Timbob";
            person2.lastName = "Rooski" + i;
            person2.address = new AddressNoPartition();
            person2.address.id = person.address.id;
            person2.address.street = "Sluisvaart";
            person2.address.houseNr = 98;
            manager.saveEntity(person2);

            find(person2.address);
        }


        QueryCriteria first = new QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(AddressNoPartition.class, first);
        query.setSelections(Arrays.asList("id", "street", "occupants"));

        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
        assert addresses.get(0).get("occupants") instanceof List;
        assert ((List) addresses.get(0).get("occupants")).get(0) instanceof Map;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(0)).get("firstName") != null;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(1)).get("firstName") != null;
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testQuerySpecificPartitionOrderBy() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }


        QueryCriteria first = new QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian");
        QueryCriteria second = new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(PersonNoPartition.class, first.and(second));
        query.setQueryOrders(Arrays.asList(new QueryOrder("firstName"), new QueryOrder("address.street")));
        List<PersonNoPartition> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testSelectAttributeOrderBy() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }


        QueryCriteria first = new QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian");
        QueryCriteria second = new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(PersonNoPartition.class, first.and(second));
        query.setSelections(Arrays.asList("firstName", "address.street"));
        query.setQueryOrders(Arrays.asList(new QueryOrder("firstName"), new QueryOrder("address.street")));
        List<PersonNoPartition> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSelectRelationshipOrderBy() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);
        }


        QueryCriteria first = new QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Cristian");
        QueryCriteria second = new QueryCriteria("address.street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(PersonNoPartition.class, first.and(second));
        query.setSelections(Arrays.asList("firstName", "address"));
        query.setQueryOrders(Arrays.asList(new QueryOrder("firstName"), new QueryOrder("address.street")));
        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
        assert addresses.get(0).get("address") instanceof Map;
        assert ((Map) addresses.get(0).get("address")).get("street").equals("Sluisvaart");

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToManySelectRelationshipOrderBy() throws OnyxException {
        manager.executeDelete(new Query(PersonNoPartition.class));
        manager.executeDelete(new Query(AddressNoPartition.class));

        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);

            PersonNoPartition person2 = new PersonNoPartition();
            person2.firstName = "Timbob";
            person2.lastName = "Rooski" + i;
            person2.address = new AddressNoPartition();
            person2.address.id = person.address.id;
            person2.address.street = "Sluisvaart";
            person2.address.houseNr = 98;
            manager.saveEntity(person2);

            find(person2.address);
        }


        QueryCriteria first = new QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        QueryCriteria second = new QueryCriteria("occupants.firstName", QueryCriteriaOperator.NOT_EQUAL, "Ti!mbob");
        Query query = new Query(AddressNoPartition.class, first.and(second));
        query.setSelections(Arrays.asList("id", "street", "occupants"));
        query.setQueryOrders(Arrays.asList(new QueryOrder("occupants.firstName")));

        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
        assert addresses.get(0).get("occupants") instanceof List;
        assert ((List) addresses.get(0).get("occupants")).get(0) instanceof Map;
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(1)).get("firstName").equals("Timbob");
        assert ((Map) ((List) addresses.get(0).get("occupants")).get(0)).get("firstName").equals("Cristian");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToManySelectRelationshipNoRelationshipCriteriaOrderBy() throws OnyxException {
        for (int i = 0; i < 50; i++) {
            PersonNoPartition person = new PersonNoPartition();
            person.firstName = "Cristian";
            person.lastName = "Vogel" + i;
            person.address = new AddressNoPartition();
            person.address.street = "Sluisvaart";
            person.address.houseNr = 98;
            manager.saveEntity(person);

            PersonNoPartition person2 = new PersonNoPartition();
            person2.firstName = "Timbob";
            person2.lastName = "Rooski" + i;
            person2.address = new AddressNoPartition();
            person2.address.id = person.address.id;
            person2.address.street = "Sluisvaart";
            person2.address.houseNr = 98;
            manager.saveEntity(person2);

            find(person2.address);
        }


        QueryCriteria first = new QueryCriteria("street", QueryCriteriaOperator.EQUAL, "Sluisvaart");
        Query query = new Query(AddressNoPartition.class, first);
        query.setSelections(Arrays.asList("id", "street", "occupants"));
        query.setQueryOrders(Arrays.asList(new QueryOrder("street")));

        List<Map> addresses = manager.executeQuery(query);
        assert addresses.size() > 0;
        assert addresses.get(0).get("occupants") instanceof List;
        assert ((List) addresses.get(0).get("occupants")).get(0) instanceof Map;
    }
}
