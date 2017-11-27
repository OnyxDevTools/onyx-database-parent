package com.onyxdevtools.persist;


import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.persist.entities.Person;

import java.io.File;
import java.util.Date;


/**
 *
 * @author Chris Osborn
 */
@SuppressWarnings("WeakerAccess")
public class SavingAnEntityExample
{

    public static void main(String[] args) throws OnyxException
    {
        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar + "persisting-data.oxd";
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB);

        factory.setCredentials("username", "password");
        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();
        
        Person person1 = new Person();
        person1.setFirstName("John");
        //noinspection SpellCheckingInspection
        person1.setLastName("Elway");
        person1.setDateCreated(new Date());
        
        Person savedPerson = manager.saveEntity(person1);
        
        Person retrievedPerson = manager.findById(Person.class, savedPerson.getId());

        assert retrievedPerson != null;
        System.out.println("Person " + retrievedPerson.getId() + " saved successfully");
        
        factory.close(); //Close the embedded database after you're done with it
        
    }
}
