package com.onyx.application;

import io.undertow.security.idm.IdentityManager;

/**
 * Onyx Server contract.  This is the interface that all servers must implement.
 *
 *
 * <pre>
 * <code>
 *
 *    OnyxServer server = new DatabaseServer();
 *    server.setPort(8080);
 *    server.setDatabaseLocation("C:/Sandbox/Onyx/Tests/server.oxd");
 *    server.start();
 *    server.join();
 *
 * </code>
 * </pre>
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 */
public interface OnyxServer
{
    /**
     * Starts the database server
     * @since 1.0.0
     *
     */
    void start();


    /**
     * Stops the database server
     * @since 1.0.0
     *
     */
    void stop();

    /**
     * Flag to indicate whether the database is running or not
     * @since 1.0.0
     * @return Boolean flag running
     */
    boolean isRunning();

    /**
     * Get Database port
     *
     * @since 1.0.0
     * @return Port Number
     */
    int getPort();

    /**
     * Set Port Number.  By Default this is 8080
     *
     * @since 1.0.0
     * @param port Port Number
     */
    void setPort(int port);

    /**
     * Join Thread, in order to keep the server alive
     * @since 1.0.0
     */
    void join();

    /**
     * Set Credentials
     * @since 1.0.0
     *
     * @param user Username
     * @param password Password
     */
    void setCredentials(String user, String password);

    /**
     * Set authentication filter / Identity Manager
     *
     * @since 1.0.0
     * @param identityManager Security filter
     */
    void setAuthenticationIdentityManager(IdentityManager identityManager);

    /**
     * Setter for socket port.  By setting the port number, this will enable socket communication to run on the specified port.  This is used in order to streamline network communication.
     *
     * @param socketPort 4 digit port.  If not set it will default to 1009
     * @since 1.0.0
     */
    void setSocketPort(int socketPort);

    /**
     * By setting the enabled flag to true, this will enable socket communication.  This is used in order to streamline network communication.  If you specify false, sockets will not be enabled
     *
     * @param enableSocketSupport Enable Socket Support for increased performance
     * @since 1.0.0
     */
    void setEnableSocketSupport(boolean enableSocketSupport);

    /**
     * Setter for SSL Keystore Password.  This depends on useSSL being true
     * @since 1.0.0
     * @param sslKeystorePassword Keystore Password
     */
    void setSslKeystorePassword(String sslKeystorePassword);

    /**
     * Setter for SSL Keystore file path.  This is typically in format "C:\\ssl\\clientkeystore.jks")
     * @since 1.0.0
     * @param sslKeystoreFilePath  Keystore file path
     */
    void setSslKeystoreFilePath(String sslKeystoreFilePath);

    /**
     * Setter for SSL Trust Store file path.  This is typically in format "C:\\ssl\\clienttruststore.jks".jks")
     * @since 1.0.0
     * @param sslKeystoreFilePath Keystore Password
     */
    void setSslTrustStoreFilePath(String sslTrustStoreFilePath);

    /**
     * Trust store password.  This is typically the same as your keystore password
     * @since 1.0.0
     * @param sslTrustStorePassword  Trust Store Password
     */
    void setSslTrustStorePassword(String sslTrustStorePassword);

    /**
     * Defines whether you would like to use regular http or SSL
     * @since 1.0.0
     * @param useSSL Use SSL Security
     */
    void setUseSSL(boolean useSSL);
}
