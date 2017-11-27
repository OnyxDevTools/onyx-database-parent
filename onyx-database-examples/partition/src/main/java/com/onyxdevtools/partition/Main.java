package com.onyxdevtools.partition;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.partition.entities.CallLog;
import com.onyxdevtools.partition.entities.CellPhone;

import java.io.File;
import java.util.List;

/**
 * Created by Tim Osborn on 3/31/16.
 *
 * This demonstrates how to declare and use a partition
 */
@SuppressWarnings("WeakerAccess")
public class Main extends AbstractDemo {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws OnyxException
    {

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"partitioned-db.oxd";

        // Delete database so you have a clean slate
        deleteDatabase(pathToOnyxDB);

        //Initialize the database and get a handle on the PersistenceManager
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(pathToOnyxDB);
        factory.setCredentials("onyx-user", "SavingDataIsFun!");
        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();

        // Insert test Call Log data
        seedData(manager);

        // Create a query that includes the partition and flag for whether the NSA is listening
        // Area Code is partitioned and isNSAListening is indexed.  This should be an optimized query
        QueryCriteria nsaListeningCriteria = new QueryCriteria(
                  "isNSAListening", QueryCriteriaOperator.EQUAL, true);

        Query query = new Query(CallLog.class, nsaListeningCriteria);
        query.setPartition(555);

        List<CallLog> nsaIsWastingThereTimeListeningTo = manager.executeQuery(query);
        assertTrue("NSA is only listening to 1 call in area code 555", nsaIsWastingThereTimeListeningTo.size() == 1);

        // Use Find By ID in Partition to fetch a call log within a partition
        CallLog callLogInAreaCode555 = manager.findByIdInPartition(CallLog.class, 1, 555);
        CallLog callLogInAreaCode123 = manager.findByIdInPartition(CallLog.class, 1, 123);

        // Make sure the CallLog(s) are 2 different entities.
        assert callLogInAreaCode123 != null;
        assert callLogInAreaCode555 != null;
        assertTrue("The Destination Number should be different for each CallLog!", !callLogInAreaCode123.getDestinationNumber().equals(callLogInAreaCode555.getDestinationNumber()));

        // Since we have defined an IdentifierGenerator of SEQUENCE, it is possible the identifiers for call log
        factory.close();
    }

    /**
     * Seed Cell phone log data
     *
     * @param manager Persistence Manager to use to seed data
     * @throws OnyxException Generic exception from persistence manager
     */
    private static void seedData(PersistenceManager manager) throws OnyxException
    {
        // Create a call log for area code (555)
        CellPhone myPhoneNumber = new CellPhone();
        myPhoneNumber.setCellPhoneNumber("(555) 303-2322");
        myPhoneNumber.setAreaCode(555);
        manager.saveEntity(myPhoneNumber);

        CallLog callToMom = new CallLog();
        callToMom.setDestinationNumber("(555) 323-2222");
        callToMom.setNSAListening(true);
        callToMom.setCallFrom(myPhoneNumber);
        callToMom.setCallFromAreaCode(myPhoneNumber.getAreaCode());
        manager.saveEntity(callToMom);

        @SuppressWarnings("SpellCheckingInspection") CallLog callToEdwardSnowden = new CallLog();
        callToEdwardSnowden.setDestinationNumber("(555) 122-2341");
        callToEdwardSnowden.setNSAListening(false);
        callToEdwardSnowden.setCallFrom(myPhoneNumber);
        callToEdwardSnowden.setCallFromAreaCode(myPhoneNumber.getAreaCode());
        manager.saveEntity(callToEdwardSnowden);

        // Create a call log for area code (123)
        // Note: Identifiers are not unique among partitions.  Since the entire object graph is saved,
        //       it is possible in this example to have the same identifiers for a CallLog in area code 555 as well as 123

        CellPhone mySecretPhone = new CellPhone();
        mySecretPhone.setCellPhoneNumber("(123) 936-3733");
        mySecretPhone.setAreaCode(123);
        manager.saveEntity(mySecretPhone);

        CallLog callToSomeoneShady = new CallLog();
        callToSomeoneShady.setDestinationNumber("(555) 322-1143");
        callToSomeoneShady.setNSAListening(false);
        callToSomeoneShady.setCallFrom(mySecretPhone);
        callToSomeoneShady.setCallFromAreaCode(mySecretPhone.getAreaCode());
        manager.saveEntity(callToSomeoneShady);

        CallLog callToJoe = new CallLog();
        callToJoe.setDestinationNumber("(555) 286-9987");
        callToJoe.setNSAListening(true);
        callToJoe.setCallFrom(mySecretPhone);
        callToJoe.setCallFromAreaCode(mySecretPhone.getAreaCode());
        manager.saveEntity(callToJoe);

    }
}
