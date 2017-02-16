package com.onyx.server.auth;

import com.onyx.client.auth.AuthenticationManager;
import com.onyx.entity.SystemUser;
import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.util.EncryptionUtil;

/**
 * Created by tosborn1 on 2/13/17.
 *
 * Default implementation of the authentication manager
 */
public class DefaultAuthenticationManager implements AuthenticationManager {

    private final PersistenceManager persistenceManager;

    /**
     * Constructor with persistence manager
     * @param persistenceManager Persistence manager
     */
    public DefaultAuthenticationManager(PersistenceManager persistenceManager)
    {
        this.persistenceManager = persistenceManager;
    }

    /**
     * Verify Account exists and has correct password
     * @param username User id
     * @param password Password
     * @throws InitializationException Thrown if account is not verified
     * @since 1.2.0
     */
    @Override
    public void verify(String username, String password) throws InitializationException {
        SystemUser user;
        try {
            user = (SystemUser)persistenceManager.findById(SystemUser.class, username);
        } catch (EntityException e) {
            throw new InitializationException(InitializationException.UNKNOWN_EXCEPTION, e);
        }
        if(user == null)
            throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
        if(!user.getPassword().equals(EncryptionUtil.encrypt(password)))
            throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
    }
}
