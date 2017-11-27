package com.onyxdevtools.embedded;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.embedded.entities.Person;

import java.io.File;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class Main
{

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws OnyxException
    {

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"embedded-db.oxd";

        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB); //1
        //noinspection SpellCheckingInspection
        factory.setCredentials("onyx-remote", "SavingDataIsFun!"); //2
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

    }
}
