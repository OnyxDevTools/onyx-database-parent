package com.onyx.client;

/**
 * Created by tosborn1 on 2/13/17.
 *
 * Contract for SSL Peer communication
 */
@SuppressWarnings("unused")
public interface SSLPeer {

    /**
     * Set for SSL Store Password.  Note, this is different than Keystore Password
     * @param sslStorePassword Password for SSL Store
     * @since 1.2.0
     */
    @SuppressWarnings("unused")
    void setSslStorePassword(String sslStorePassword);

    /**
     * Set Keystore file path.  This should contain the location of the JKS Keystore file
     * @param sslKeystoreFilePath Resource location of the JKS keystore
     * @since 1.2.0
     */
    void setSslKeystoreFilePath(String sslKeystoreFilePath);

    /**
     * Set for SSL KeysStore Password.
     * @param sslKeystorePassword Password for SSL KEY Store
     * @since 1.2.0
     */
    void setSslKeystorePassword(String sslKeystorePassword);

    /**
     * Set Trust store file path.  Location of the trust store JKS File.  This should contain
     * a file of the trusted sites that can access your secure endpoint
     * @param sslTrustStoreFilePath File path for JKS trust store
     */
    void setSslTrustStoreFilePath(String sslTrustStoreFilePath);

    /**
     * Trust store password
     * @param sslTrustStorePassword Password used to access your JKS Trust store
     */
    void setSslTrustStorePassword(String sslTrustStorePassword);


}
