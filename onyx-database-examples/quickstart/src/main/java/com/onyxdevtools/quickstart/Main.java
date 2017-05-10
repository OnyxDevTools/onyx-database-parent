package com.onyxdevtools.quickstart;

import com.onyx.exception.EntityException;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.quickstart.entities.MyEnum;
import com.onyxdevtools.quickstart.entities.Other;
import com.onyxdevtools.quickstart.entities.Person;

import java.util.List;

public class Main
{

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws EntityException
    {
        //Create an instance of an entity
        final Person person1 = new Person();
        person1.setId("1");
        person1.setFirstName("Michael");
        person1.setLastName("Jordan");
        person1.setMyEnum(MyEnum.SECOND);
        person1.setOther(new Other());
        person1.getOther().setHiya("HIYA");

        //Initialize the database and get a handle on the PersistenceManager
        EmbeddedPersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();
        factory.setDatabaseLocation("/Users/tosborn1/Desktop/my.oxd");
        factory.initialize();
        PersistenceManager manager = factory.getPersistenceManager();

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

        factory.close();
    }
}
