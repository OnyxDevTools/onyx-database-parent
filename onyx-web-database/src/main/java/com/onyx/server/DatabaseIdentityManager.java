package com.onyx.server;

import com.onyx.entity.SystemUser;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.manager.PersistenceManager;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;

/**
 * This class is responsible for performing the account validation for http basic auth.
 *
 * This is an undertow implementation
 */
public class DatabaseIdentityManager implements IdentityManager {

    private final PersistenceManager persistenceManager;

    public DatabaseIdentityManager(PersistenceManager persistenceManager)
    {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public Account verify(Account account) {
        // An existing account so for testing assume still valid.
        return account;
    }

    @Override
    public Account verify(String id, Credential credential) {
        Account account = getAccount(id);
        if (account != null && verifyCredential(account, credential)) {
            return account;
        }

        return null;
    }

    @Override
    public Account verify(Credential credential) {
        // TODO Auto-generated method stub
        return null;
    }

    private boolean verifyCredential(Account account, Credential credential)
    {
        if (credential instanceof PasswordCredential) {
            char[] password = ((PasswordCredential) credential).getPassword();
            final String sentPassword = new String(password);

            return account != null
                    && (sentPassword.equals(((DatabaseUserAccount) account).getPassword()));

        }
        return false;
    }

    private Account getAccount(final String id)
    {
        try
        {
            final SystemUser user = persistenceManager.findById(SystemUser.class, id);

            if(user == null)
                return null;

            return new DatabaseUserAccount(user);
        }
        catch (OnyxException e)
        {
            return null;
        }
    }

}