package com.onyx.application

import com.onyx.interactors.encryption.EncryptionInteractor

/**
 * Onyx Server contract.  This is the interface that all servers must implement.
 *
 *
 * OnyxServer server = new DatabaseServer();
 * server.setPort(8080);
 * server.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server.oxd");
 * server.start();
 * server.join();
 *
 *
 * @author Tim Osborn
 * @since 1.0.0
 */
interface OnyxServer {

    /**
     * Flag to indicate whether the database is running or not
     * @since 1.0.0
     * @return Boolean flag running
     */
    val isRunning: Boolean

    /**
     * Get Database port
     *
     * @since 1.0.0
     * @return Port Number
     */
    var port: Int

    /**
     * Database encryption.  In order to make more secure, implement custom encryption interactor.  Use it to override
     * the keys.
     *
     * @see com.onyx.interactors.encryption.impl.DefaultEncryptionInteractorInstance
     */
    var encryption: EncryptionInteractor

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
