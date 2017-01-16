package com.onyx.server.auth;

import com.onyx.client.auth.AuthData;
import com.onyx.client.auth.Authorize;
import com.onyx.entity.SystemUser;
import com.onyx.exception.EntityException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.util.EncryptionUtil;

/**
 * Implementation of Database Authorization for A socket connection
 */
public class SocketDatabaseAuthrorizeImpl implements Authorize {

    private PersistenceManager systemPersistenceManager;

    /**
     * Default Constructor with persistence manger
     */
    public SocketDatabaseAuthrorizeImpl()
    {
    }

    /**
     * Perform Authorization
     *
     * @param authData User Authentication Information
     *
     * @return Whether the user is authorized or not
     */
    @Override
    public boolean authorize(AuthData authData) {
        final SystemUser user;
        try {
            user = (SystemUser)systemPersistenceManager.findById(SystemUser.class, authData.username);
        } catch (EntityException e) {
            return false;
        }
        if(user == null)
            return false;
        return user.getPassword().equals(EncryptionUtil.encrypt(authData.password));
    }

    /**
     * Set System Persistence Manager
     * @param systemPersistenceManager
     */
    public void setSystemPersistenceManager(PersistenceManager systemPersistenceManager) {
        this.systemPersistenceManager = systemPersistenceManager;
    }
}
