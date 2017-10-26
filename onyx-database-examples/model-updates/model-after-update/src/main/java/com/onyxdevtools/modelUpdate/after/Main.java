package com.onyxdevtools.modelUpdate.after;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;

import java.io.File;

/**
 * This is a 2 part example.  The first example is used simply to create an existing database.
 *
 * Part 1 - @see com.onyxdevtools.modelUpdate.before.Main
 *
 *   This class' purpose is to fill a test database with a flawed data model so that we can showcase
 *   how we can make changes to the data model and handle migrations.
 *
 * Part 2 - @see com.onyxdevtools.modelUpdate.after.Main
 *
 *   Part 2 will demonstrate how the changes are made to the data model.  Have a look at the entities and notice
 *   the commented changes to those entities.
 *
 * Instruction - First run this main class for Part 1, and then run the main class in Part 2
 */
public class Main
{
    public static void main(String[] args) throws OnyxException
    {
        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"model-update-db.oxd";

        // Create a database and its connection
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB); //1

        //noinspection SpellCheckingInspection
        factory.setCredentials("onyx", "SavingDataisFun!"); //2
        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();  //5

        ManualMigrationDemo.demo(manager); // For all things that are not possible with the lightweight migration, you can use a manual migration using the stream api
        UpdateFieldDemo.demo(manager); // Displays how the model has been changed by adding/removing fields.  This is done with the lightweight migration
        UpdateIdentifierDemo.demo(manager); // Displays how the identifier type has changed.  This has been done by the lightweight migration.
        UpdateIndexDemo.demo(manager); // Displays how indexes have been added/removed.  Again handled by lightweight migration.
        UpdateRelationshipDemo.demo(manager); // Displays how you can change simple things on a relationship that can be handled by the lightweight migration.

        // Close the database cleanly
        factory.close();
    }
}
