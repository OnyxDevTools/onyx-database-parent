package com.onyx.client;

import com.onyx.client.exception.ConnectionFailedException;
import com.onyx.client.exception.OnyxServerException;

import java.util.function.Consumer;

/**
 * Created by tosborn1 on 6/26/16.
 *
 * This interface is the contract for how the client connects to the server
 */
public interface OnyxClient extends SSLPeer
{

    /**
     * Connect to host with port number
     * @param host Server Host
     * @param port Server Port
     */
    @SuppressWarnings("unused")
    void connect(String host, int port) throws ConnectionFailedException;

    /**
     * Write request object to server
     *
     * @param packet Object to send to server
     * @param consumer Consumer for the results
     */
    @SuppressWarnings("unused")
    void send(Object packet, Consumer<Object> consumer) throws OnyxServerException;

    /**
     * A blocking api for sending a request and waiting on the results
     *
     * @param packet Object to send to server
     * @return Object returned from the server
     */
    @SuppressWarnings("unused")
    Object send(Object packet) throws OnyxServerException;

    /**
     * Close the connection
     */
    @SuppressWarnings("unused")
    void close();

    /**
     * Indicator to see if the client is connected to the server
     * @return true or false
     */
    @SuppressWarnings("unused")
    boolean isConnected();

    /**
     * Set the timeout in seconds
     * @since 1.2.0
     * @param timeout Connection/Request timeout
     */
    @SuppressWarnings("unused")
    void setTimeout(int timeout);

    /**
     * Get the timeout in seconds for a request
     * @since 1.2.0
     * @return timeout
     */
    @SuppressWarnings("unused")
    int getTimeout();

    /**
     * Setter for SSL Keystore Password.  This depends on useSSL being true
     * @param sslKeystorePassword Keystore Password
     */
    @SuppressWarnings("unused")
    void setSslKeystorePassword(String sslKeystorePassword);

    /**
     * Setter for SSL Keystore file path.  This is typically in format "C:\\ssl\\client_keystore.jks")
     * @param sslKeystoreFilePath Keystore File Path
     */
    @SuppressWarnings("unused")
    void setSslKeystoreFilePath(String sslKeystoreFilePath);

    /**
     * Setter for SSL Trust Store file path.  This is typically in format "C:\\ssl\\client_truststore.jks".jks")
     * @param sslTrustStoreFilePath Trust Store File Path
     */
    @SuppressWarnings("unused")
    void setSslTrustStoreFilePath(String sslTrustStoreFilePath);

    /**
     * Trust store password.  This is typically the same as your keystore password
     * @param sslTrustStorePassword Trust Store Password
     */
    @SuppressWarnings("unused")
    void setSslTrustStorePassword(String sslTrustStorePassword);

}
