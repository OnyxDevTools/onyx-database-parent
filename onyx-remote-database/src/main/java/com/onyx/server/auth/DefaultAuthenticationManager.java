package com.onyx.server.auth;

import com.onyx.client.auth.AuthenticationManager;
import com.onyx.entity.SystemUser;
import com.onyx.exception.OnyxException;
import com.onyx.exception.InitializationException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.interactors.encryption.EncryptionInteractor;

/**
 * Created by tosborn1 on 2/13/17.
 *
 * Default implementation of the authentication manager
 */
public class DefaultAuthenticationManager implements AuthenticationManager {

    private final PersistenceManager persistenceManager;
    private final EncryptionInteractor encryption;

    /**
     * Constructor with persistence manager
     * @param persistenceManager Persistence manager
     */
    public DefaultAuthenticationManager(PersistenceManager persistenceManager, EncryptionInteractor encryption)
    {
        this.persistenceManager = persistenceManager;
        this.encryption = encryption;
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
            user = persistenceManager.findById(SystemUser.class, username);
        } catch (OnyxException e) {
            throw new InitializationException(InitializationException.UNKNOWN_EXCEPTION, e);
        }
        if(user == null)
            throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
        if(!user.getPassword().equals(encryption.encrypt(password)))
            throw new InitializationException(InitializationException.INVALID_CREDENTIALS);
    }
}
