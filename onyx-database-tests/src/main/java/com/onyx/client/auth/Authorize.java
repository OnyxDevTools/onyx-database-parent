package com.onyx.client.auth;

/**
 * Contract for socket server authorization
 * @author Tim Osborn
 */
public interface Authorize {

    /**
     * Authorize with User Authorization Data
     *
     * @param authData User Authentication Information
     *
     * @return True true if the user has successfully authenticated false otherwise
     */
    boolean authorize(AuthData authData);
}
