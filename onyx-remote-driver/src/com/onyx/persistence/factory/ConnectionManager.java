package com.onyx.persistence.factory;

import com.onyx.exception.EntityException;
import com.onyx.exception.InitializationException;

/**
 * Created by tosborn1 on 8/25/16.
 */
public interface ConnectionManager
{
    /**
     * The purpose of this is to verify a connection.  This method is to ensure the connection is always open
     *
     * @since 1.1.0
     * @throws EntityException Cannot re-connect if not connected
     */
    void verifyConnection() throws EntityException;

    /**
     * Connect to the remote database server
     *
     * @since 1.1.0
     * @throws InitializationException Exception occurred while connecting
     */
    void connect() throws InitializationException;
}
