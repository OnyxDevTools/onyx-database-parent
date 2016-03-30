package com.onyxdevtools.quickstart;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.RemotePersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.quickstart.entities.Person;
import java.io.IOException;
import java.util.List;

public class Main
{

    public static void main(String[] args) throws InitializationException, EntityException, IOException
    {

        PersistenceManagerFactory factory = new RemotePersistenceManagerFactory(); //1

        factory.setCredentials("onyx-remote", "SavingDataIsFun!"); //2

        factory.setDatabaseLocation("onx://localhost:8080"); //3

        factory.initialize();  //4

        PersistenceManager manager = factory.getPersistenceManager();  //5

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
        
        System.exit(0); //just for this example, so that the app stops

    }

}
