package com.onyxdevtools.client;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.factory.impl.RemotePersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.remote.Person;

import java.util.List;

public class Main
{

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws OnyxException
    {

        PersistenceManagerFactory factory = new RemotePersistenceManagerFactory("onx://localhost:8081"); //1

        factory.setCredentials("onyx-remote", "SavingDataIsFun!"); //2
        factory.initialize();  //4

        // The Socket Persistence Manager is an alternative PM used to increase performance and reduce network latency
        PersistenceManager manager = factory.getPersistenceManager();  //5
        // PersistenceManager manager = factory.getPersistenceManager();

        //Create an instance of an entity
        final Person person1 = new Person();
        person1.setId("1");
        person1.setFirstName("Michael");
        person1.setLastName("Jordan");

        //Save the instance
        manager.saveEntity(person1);

        //Execute a query to see your entity in the collection
        QueryCriteria criteria = new QueryCriteria("firstName", QueryCriteriaOperator.EQUAL, "Michael");
        Query query = new Query(Person.class, criteria);
        query.setMaxResults(20);
        List<Person> people = manager.executeQuery(query);

        //There should be 1 person in the list named "Michael Jordan"
        System.out.println("records returned: " + people.size());
        System.out.println("first person in the list: " + people.get(0).getFirstName() + " " + people.get(0).getLastName());

        factory.close(); //Close the embedded database after you're done with it

        System.exit(0);
    }

}
