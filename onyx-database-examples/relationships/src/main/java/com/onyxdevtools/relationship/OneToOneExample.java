package com.onyxdevtools.relationship;

import com.onyx.exception.EntityException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.relationship.entities.Sailboat;
import com.onyxdevtools.relationship.entities.Skipper;

import java.io.File;
import java.io.IOException;

/**
 * This Demo illustrates how to define an entity and a One To One relationship.
 *
 * @see Sailboat
 * @see Skipper
 */
public class OneToOneExample extends AbstractDemo
{

    public static void demo() throws EntityException
    {
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();

        factory.setCredentials("onyx-user", "SavingDataisFun!");

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"relationship-cascade-save-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);

        // Delete database so you have a clean slate
        deleteDatabase(pathToOnyxDB);

        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();

        // Create a new sailboat named Wind Passer and Registration(Primary Key) NCC1701
        Sailboat sailboat = new Sailboat();
        sailboat.setRegistrationCode("NCC1701");
        sailboat.setName("Wind Passer");

        // Create a new Skipper named Martha McFly
        Skipper sailboatSkipper = new Skipper();
        sailboatSkipper.setFirstName("Martha");
        sailboatSkipper.setLastName("McFly");

        // Define the relationship
        sailboat.setSkipper(sailboatSkipper);

        // Save the sailboat
        manager.saveEntity(sailboat);
        System.out.println("Created a new Sailboat " + sailboatSkipper.getSailboat().getName() + " with Skipper " + sailboatSkipper.getFirstName());

        // Find the sailboat by ID
        Sailboat newlyCreatedSailboat = (Sailboat)manager.findById(Sailboat.class, "NCC1701");
        System.out.println("Sailboat " + newlyCreatedSailboat.getName() + " was created with skipper " + newlyCreatedSailboat.getSkipper().getFirstName());

        factory.close();

    }
}
