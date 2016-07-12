package com.onyxdevtools.modelupdate.after;

import com.onyx.exception.EntityException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.modelupdate.entities.Account;

/**
 * Created by tosborn1 on 6/28/16.
 *
 * The UpdateFieldDemo demonstrates how the model has been changed by adding/removing fields.
 * There should not be any additional work needed as it is handled by the lightweight migration.
 *
 * This demo will show you that the lightweight migration was successful and seamless.
 */
public class UpdateFieldDemo
{
    /**
     * Main Method to demo the functionality
     * @param persistenceManager Open and valid persistence manager
     */
    public static void demo(PersistenceManager persistenceManager)
    {

        try {
            // Fetch an account.  Notice that the id is now a long rather than an integer.
            Account account = (Account)persistenceManager.findById(Account.class, 1l);
            assert account.getAccountId() == 1l;

            // The Account Name is a new field and is now persistable.
            // This demonstrates that we can now take
            // advantage of the new field.
            account.setAccountName("Utility Bill");
            persistenceManager.saveEntity(account);

            // Verify it was indeed persisted
            account = (Account)persistenceManager.findById(Account.class, 1l);
            assert account.getAccountName().equals("Utility Bill");

            // Notice, the one thing that we cannot demonstrate is that we removed a field, "balanceDue"
            // Don't worry, we can add it back and the data will still be there since it has not been orphaned.
        } catch (EntityException e)
        {
            e.printStackTrace();
            assert false;
        }
    }
}
