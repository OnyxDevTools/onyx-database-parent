package com.onyxdevtools.modelupdate.before;

import com.onyx.exception.EntityException;
import com.onyx.persistence.factory.PersistenceManagerFactory;
import com.onyx.persistence.factory.impl.EmbeddedPersistenceManagerFactory;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.modelupdate.entities.Account;
import com.onyxdevtools.modelupdate.entities.Invoice;
import com.onyxdevtools.modelupdate.entities.Payment;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This is a 1 part example.  The first example is used simply to create an existing database.
 *
 * Part 1 - @see com.onyxdevtools.modelupdate.before.Main
 *
 *   This class' purpose is to fill a test database with a flawed data model so that we can showcase
 *   how we can make changes to the data model and handle migrations.
 *
 * Part 2 - @see com.onyxdevtools.modelupdate.after.Main
 *
 *   Part 2 will demonstrate how the changes are made to the data model.  Have a look at the entities and notice
 *   the commented changes to those entities.
 *
 * Instruction - First run this main class for Part 1, and then run the main class in Part 2
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class Main
{

    // Date formatter used to convert strings to dates
    private static final SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy");

    public static void main(String[] args) throws EntityException
    {

        // Create a database and its connection
        PersistenceManagerFactory factory = new EmbeddedPersistenceManagerFactory(); //1

        factory.setCredentials("onyx", "SavingDataisFun!"); //2

        String pathToOnyxDB = System.getProperty("user.home")
                + File.separatorChar + ".onyxdb"
                + File.separatorChar + "sandbox"
                + File.separatorChar +"model-update-db.oxd";

        // Cleanup the database before we begin
        deleteDatabase(pathToOnyxDB);

        factory.setDatabaseLocation(pathToOnyxDB); //3

        factory.initialize();

        PersistenceManager manager = factory.getPersistenceManager();  //5

        // Add Some test data
        seedData(manager);

        // Close the database cleanly
        factory.close();
    }

    /**
     * Fill the database with some test data, so that we can show how the data model updates impact the changes to the values.
     *
     * @param manager Persistence Manager used to save the entities
     * @throws EntityException Failure to persist entities
     */
    private static void seedData(PersistenceManager manager) throws EntityException
    {
        Account account = new Account();
        account.setAccountName("Timbob's Lawn Care");
        account.setBalanceDue(55.43f);

        Invoice marchLawnInvoice = new Invoice();
        marchLawnInvoice.setDueDate(parseDate("04-01-2016"));
        marchLawnInvoice.setInvoiceDate(parseDate("03-01-2016"));
        marchLawnInvoice.setNotes("Why did we need to mow your lawn.  Its basically a dirt field.");
        marchLawnInvoice.setInvoiceId(1L);
        marchLawnInvoice.setAmount(44.32);
        marchLawnInvoice.setAccount(account);

        Invoice aprilLawnInvoice = new Invoice();
        aprilLawnInvoice.setDueDate(parseDate("04-01-2016"));
        aprilLawnInvoice.setInvoiceDate(parseDate("03-01-2016"));
        aprilLawnInvoice.setNotes("Its April, your lawn should be growing by now.");
        aprilLawnInvoice.setInvoiceId(2L);
        aprilLawnInvoice.setAmount(44.32);
        aprilLawnInvoice.setAccount(account);

        manager.saveEntity(account);
        manager.saveEntity(marchLawnInvoice);
        manager.saveEntity(aprilLawnInvoice);

        Payment marchLawnCarePayment = new Payment();
        marchLawnCarePayment.setPaymentId(1L);
        marchLawnCarePayment.setInvoice(marchLawnInvoice);
        marchLawnCarePayment.setAmount(44.32);

        manager.saveEntity(marchLawnCarePayment);

        Account account1 = manager.findById(Account.class, 1);
        assert account1.getAccountId() == 1L;
    }

    /**
     * Helper method used to parse a date in string format.  Meant to encapsulate the error handling.
     *
     * @param stringDate  String in format of MM-dd-yyyy
     * @return Date key
     */
    private static Date parseDate(String stringDate)
    {
        try {
            return formatter.parse(stringDate);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Delete a database so you have a clean slate prior to testing
     *
     * @param pathToDb Path to onyx database
     */
    private static void deleteDatabase(String pathToDb)
    {
        File database = new File(pathToDb);
        if (database != null && database.exists()) {
            delete(database);
        }
        database.delete();
    }

    /**
     * Delete files within a directory
     * @param f directory to delete
     */
    private static void delete(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        f.delete();
    }
}
