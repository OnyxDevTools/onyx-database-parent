package com.onyxdevtools.partition;

import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.CacheManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.partition.entities.CallLog;
import com.onyxdevtools.partition.entities.CellPhone;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by tosborn1 on 3/31/16.
 */
public class Main extends AbstractDemo {
    public static void main(String[] args) throws IOException
    {

        //Initialize the database and get a handle on the PersistenceManager
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory();

        factory.setCredentials("onyx-user", "SavingDataisFun!");

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"partitioned-db.oxd";
        factory.setDatabaseLocation(pathToOnyxDB);

        // Delete database so you have a clean slate
        deleteDatabase(pathToOnyxDB);

        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();

        // Create a call log for area code (555)
        CellPhone myPhoneNumber = new CellPhone();
        myPhoneNumber.setCellPhoneNumber("(555)303-2322");
        myPhoneNumber.setAreaCode(555);
        manager.saveEntity(myPhoneNumber);

        CallLog callToMom = new CallLog();
        callToMom.setDestinationNumber("(555)323-2222");
        callToMom.setNSAListening(true);
        callToMom.setCallFrom(myPhoneNumber);
        manager.saveEntity(callToMom);

        CallLog callToEdwardSnowden = new CallLog();
        callToEdwardSnowden.setDestinationNumber("(555)122-2341");
        callToEdwardSnowden.setNSAListening(false);
        callToEdwardSnowden.setCallFrom(myPhoneNumber);
        manager.saveEntity(callToEdwardSnowden);

        // Create a call log for area code (123)
        // Note: Identifiers are not unique among partitions.  Since the entire object graph is saved,
        //       it is possible in this example to have the same identifiers for a CallLog in area code 555 as well as 123

        CellPhone mySecretPhone = new CellPhone();
        mySecretPhone.setCellPhoneNumber("(123)936-3733");
        mySecretPhone.setAreaCode(123);
        manager.saveEntity(mySecretPhone);

        CallLog callToSomeoneShady = new CallLog();
        callToSomeoneShady.setDestinationNumber("(555)322-1143");
        callToSomeoneShady.setNSAListening(false);
        callToSomeoneShady.setCallFrom(mySecretPhone);

        CallLog callToJoe = new CallLog();
        callToJoe.setDestinationNumber("(555)286-9987");
        callToJoe.setNSAListening(true);
        callToJoe.setCallFrom(mySecretPhone);
        manager.saveEntity(callToJoe);


        // Create a query that includes the partition and flag for whether the NSA is listening
        // Area Code is partitioned and isNSAListening is indexed.  This should be an optimized query
        //
        // Note: Partition is the first criteria.  To optimize Onyx queries, try to use the criteria
        //       that will filter the most records followed by less optimal predicates.  This will
        //       reduce the query cost.
        QueryCriteria nsaListeningCriteria = new QueryCriteria(
                  "callFrom.areaCode", QueryCriteriaOperator.EQUAL, 555
            ).and("isNSAListening",    QueryCriteriaOperator.EQUAL, true);

        Query query = new Query(CallLog.class, nsaListeningCriteria);


        List<CallLog> nsaIsWastingThereTimeListeningTo = manager.executeQuery(query);
        assertTrue("NSA is only listening to 1 call in area code 555", nsaIsWastingThereTimeListeningTo.size() == 1);



        factory.close();
    }
}
