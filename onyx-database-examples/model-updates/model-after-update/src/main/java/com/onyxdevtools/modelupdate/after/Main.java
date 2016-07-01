package com.onyxdevtools.modelupdate.after;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;

import java.io.File;

/**
 * Created by tosborn1 on 5/6/16.
 */
public class Main
{
    public static void main(String[] args) throws InitializationException, EntityException
    {
        // Create a database and its connection
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(); //1

        factory.setCredentials("onyx", "SavingDataisFun!"); //2

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"model-update-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB); //3

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
