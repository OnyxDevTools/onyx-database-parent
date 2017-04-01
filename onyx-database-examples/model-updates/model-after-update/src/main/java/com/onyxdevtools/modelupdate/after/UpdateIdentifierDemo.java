package com.onyxdevtools.modelupdate.after;

import com.onyx.exception.EntityException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.modelupdate.entities.Account;

/**
 * Created by tosborn1 on 6/28/16.
 *
 * // Displays how the identifier type has changed.  This has been done by the lightweight migration.
 * This class demonstrates how an identifier properties can be changed and handled by the lightweight migration.
 *
 * There are some cases that the lightweight migration will not be capable of performing updates.  The edge cases are:
 *
 * 1) Adding a generator to an identifier that does not already contain one.  This will require a manual migration
 *    to determine the largest sequence.
 *
 * 2) Changing the data type of the identifier that may not structure.  So for instance, if you attempt to convert a String to
 *    an integer.  This may not be possible since Onyx cannot assume the identifier is a valid integer.  You will need
 *    a manual migration for this as well.
 */
class UpdateIdentifierDemo {

    /**
     * Main Method to demo the functionality
     * @param persistenceManager Open and valid persistence manager
     */
    static void demo(PersistenceManager persistenceManager)
    {

        try {
            // Fetch an account.  Notice that the id is now a long rather than an integer.
            Account account = persistenceManager.findById(Account.class, 1L);
            assert account.getAccountId() == 1L;

            // This example creates a new account and attempts to save it without an identifier.
            Account account2 = new Account();
            persistenceManager.saveEntity(account2);

            // The account ID was not auto incremented.
            // This is because we removed the generator from the @Identifier annotation.
            assert account2.getAccountId() == 0;
        } catch (EntityException e)
        {
            e.printStackTrace();
        }
    }

}
