package com.onyxdevtools.persist;


import com.onyx.exception.EntityException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.persist.entities.Person;

import java.io.File;
import java.util.Date;

/**
 * @author cosborn
 */
public class DeletingAnEntityExample {


    public static void main(String[] args) throws EntityException {
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();

        factory.setCredentials("username", "password");

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar + "persisting-data.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);

        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();

        Person person1 = new Person();
        person1.setFirstName("John");
        person1.setLastName("Elway");
        person1.setDateCreated(new Date());

        Person savedPerson = manager.saveEntity(person1);

        manager.deleteEntity(savedPerson);

        Person deletedPerson = manager.findById(Person.class, savedPerson.getId());

        if (deletedPerson == null) {
            System.out.println("Entity was deleted sucessfully");
        }

        factory.close(); //Close the embedded database after you're done with it

    }
}
