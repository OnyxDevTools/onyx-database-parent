package com.onyxdevtools.relationship;

import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.quickstart.entities.CrewMember;
import com.onyxdevtools.quickstart.entities.Sailboat;

import com.onyxdevtools.quickstart.entities.CrewMember;
import com.onyxdevtools.quickstart.entities.Sailboat;
import com.onyxdevtools.relationship.entities.CrewMember;
import com.onyxdevtools.relationship.entities.Sailboat;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;


/**
 This demonstrates how to define and persist a One To Many relationship.
 */
public class OneToManyExample extends AbstractDemo
{
    public OneToManyExample()
    {
    }

    public static void demo() throws  IOException
    {
        final PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();

        factory.setCredentials("onyx-user", "SavingDataisFun!");

        final String pathToOnyxDB = System.getProperty("user.home") + File.separatorChar + ".onyxdb" + File.separatorChar + "sandbox" +
            File.separatorChar + "relationship-cascade-save-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);

        // Delete database so you have a clean slate
        deleteDatabase(pathToOnyxDB);

        factory.initialize();

        final PersistenceManager manager = factory.getPersistenceManager();

        // Create a sailboat named Stars and Stripes
        final Sailboat sailboat = new Sailboat();
        sailboat.setRegistrationCode("USA11");
        sailboat.setName("Stars and Stripes");

        // Create the list of crew members
        final List<CrewMember> crew = new ArrayList<>();

        final CrewMember skipper = new CrewMember();
        skipper.setFirstName("Dennis");
        skipper.setLastName("Connor");

        final CrewMember tactician = new CrewMember();
        tactician.setFirstName("Ben");
        tactician.setLastName("Ainslie");

        crew.add(skipper);
        crew.add(tactician);

        // Associate the crew members to the sailboat
        sailboat.setCrew(crew);

        manager.saveEntity(sailboat);
        System.out.println("Sailboat " + sailboat.getName() + " has " + sailboat.getCrew().size() + " crew members");

        // Find sailboat with Id
        final Sailboat savedSailboat = (Sailboat) manager.findById(Sailboat.class, "USA11");
        System.out.println("Sailboat has " + savedSailboat.getCrew().size() + " crew members");
        System.out.println(savedSailboat.getCrew().get(0).getFirstName() + " is the skipper on boat " +
            savedSailboat.getCrew().get(0).getSailboat().getName());

        factory.close();
    }
}
