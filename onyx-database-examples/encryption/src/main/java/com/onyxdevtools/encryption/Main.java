package com.onyxdevtools.encryption;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.encryption.entities.Person;

import java.io.File;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class Main
{

    public static void main(String[] args) throws OnyxException
    {
        //Create an instance of an entity
        final Person person1 = new Person();
        person1.setId("1");
        person1.setFirstName("Michael");
        person1.setLastName("Jordan");

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"encrypted-db.oxd";

        //Initialize the database and get a handle on the PersistenceManager
        EmbeddedPersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB);
        factory.initialize();
        factory.setEncryptDatabase(true);

        // By Default this will encrypt the database.  Alternatively you may
        // define how to encrypt the database using your own methodology.
        // The commented out code below is how you would do that.

        /*
        factory.setEncryption(new DefaultEncryptionInteractor() {

            @Nullable
            @Override
            public byte[] encrypt(@NotNull byte[] bytes) {
                // Implement encryption here
                return new byte[0];
            }

            @NotNull
            @Override
            public byte[] decrypt(@NotNull byte[] encryptedBytes) {
                // Implement decryption here
                return new byte[0];
            }
        });
         */

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
