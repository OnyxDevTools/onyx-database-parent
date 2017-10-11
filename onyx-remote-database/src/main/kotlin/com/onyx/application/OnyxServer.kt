package com.onyx.application

import com.onyx.client.SSLPeer
import com.onyx.interactors.encryption.EncryptionInteractor

/**
 * Onyx Server contract.  This is the interface that all servers must implement.
 *
 *
 * <pre>
 * `
 *
 * OnyxServer server = new DatabaseServer();
 * server.setPort(8080);
 * server.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server.oxd");
 * server.start();
 * server.join();
 *
` *
</pre> *
 *
 * @author Tim Osborn
 * @since 1.0.0
 */
interface OnyxServer : SSLPeer {
    /**
     * Starts the database server
     * @since 1.0.0
     */
    fun start()


    /**
     * Stops the database server
     * @since 1.0.0
     */
    fun stop()

    /**
     * Flag to indicate whether the database is running or not
     * @since 1.0.0
     * @return Boolean flag running
     */
    val isRunning: Boolean

    /**
     * Set Port Number.  By Default this is 8080
     *
     * @since 1.0.0
     */
    var port: Int

    /**
     * Encryption interactor.  This will hold encryption keys and information to make database more secure
     *
     * @see com.onyx.interactors.encryption.impl.DefaultEncryptionInteractor
     * @since 2.0.0 Effort to make more secure and override encryption keys
     */
    var encryption: EncryptionInteractor

    /**
     * Join Thread, in order to keep the server alive
     * @since 1.0.0
     */
    fun join()

    /**
     * Set Credentials
     * @since 1.0.0
     *
     * @param user Username
     * @param password Password
     */
    fun setCredentials(user: String, password: String)
}
