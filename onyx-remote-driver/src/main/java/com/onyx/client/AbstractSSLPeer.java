package com.onyx.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * Created by tosborn1 on 2/13/17.
 *
 * The purpose of this class is to abstract out the keystore file settings
 */
public abstract class AbstractSSLPeer implements SSLPeer {

    // SSL Protocol
    protected String protocol = "TLSv1.2";

    // Keystore Password
    protected String sslStorePassword;

    // Keystore file path
    protected String sslKeystoreFilePath;

    // Keystore Password
    protected String sslKeystorePassword;

    // Trust Store file path
    protected String sslTrustStoreFilePath;

    // Trust store password.  This is typically the same as keystore Password
    protected String sslTrustStorePassword;

    /**
     * Set for SSL Store Password.  Note, this is different than Keystore Password
     * @param sslStorePassword Password for SSL Store
     * @since 1.2.0
     */
    public void setSslStorePassword(String sslStorePassword) {
        this.sslStorePassword = sslStorePassword;
    }

    /**
     * Set Keystore file path.  This should contain the location of the JKS Keystore file
     * @param sslKeystoreFilePath Resource location of the JKS keystore
     * @since 1.2.0
     */
    public void setSslKeystoreFilePath(String sslKeystoreFilePath) {
        this.sslKeystoreFilePath = sslKeystoreFilePath;
    }

    /**
     * Set for SSL KeysStore Password.
     * @param sslKeystorePassword Password for SSL KEY Store
     * @since 1.2.0
     */
    public void setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
    }

    /**
     * Set Trust store file path.  Location of the trust store JKS File.  This should contain
     * a file of the trusted sites that can access your secure endpoint
     * @param sslTrustStoreFilePath File path for JKS trust store
     */
    public void setSslTrustStoreFilePath(String sslTrustStoreFilePath) {
        this.sslTrustStoreFilePath = sslTrustStoreFilePath;
    }

    /**
     * Trust store password
     * @param sslTrustStorePassword Password used to access your JKS Trust store
     */
    public void setSslTrustStorePassword(String sslTrustStorePassword) {
        this.sslTrustStorePassword = sslTrustStorePassword;
    }

    @Nullable
    @Override
    public String getSslStorePassword() {
        return sslStorePassword;
    }

    @Nullable
    @Override
    public String getSslKeystoreFilePath() {
        return sslKeystoreFilePath;
    }

    @NotNull
    @Override
    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }

    @NotNull
    @Override
    public String getSslTrustStoreFilePath() {
        return sslTrustStoreFilePath;
    }

    @NotNull
    @Override
    public String getSslTrustStorePassword() {
        return sslTrustStorePassword;
    }

    /**
     * Getter for SSL Protocol.  By default this is TLSv1.2
     * @return Protocol used for SSL
     */
    @SuppressWarnings("unused")
    public String getProtocol() {
        return protocol;
    }

    /**
     * Set Protocol for SSL
     * @param protocol Protocol used
     */
    @SuppressWarnings("unused")
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Create Key Managers
     *
     * @param filepath File path of JKS file
     * @param keystorePassword JSK file password
     * @param keyPassword Store password
     * @return Array of Key managers
     * @since 1.2.0
     * @throws Exception Invalid SSL Settings and or JKS files
     */
    protected KeyManager[] createKeyManagers(String filepath, String keystorePassword, String keyPassword) throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("JKS");
        InputStream keyStoreIS = AbstractCommunicationPeer.class.getClassLoader().getResourceAsStream(filepath);
        try {
            keyStore.load(keyStoreIS, keystorePassword.toCharArray());
        } finally {
            if (keyStoreIS != null) {
                keyStoreIS.close();
            }
        }
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword.toCharArray());
        return kmf.getKeyManagers();
    }

    /**
     * Create Trust managers
     * @param filepath Trust store JKS file path
     * @param trustStorePassword Password for the JKS File
     * @return Array of trust managers
     * @throws Exception Invalid SSK Settings or JKS file
     *
     * @since 1.2.0
     */
    protected TrustManager[] createTrustManagers(String filepath, String trustStorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        InputStream trustStoreIS = AbstractCommunicationPeer.class.getClassLoader().getResourceAsStream(filepath);
        try {
            trustStore.load(trustStoreIS, trustStorePassword.toCharArray());
        } finally {
            if (trustStoreIS != null) {
                trustStoreIS.close();
            }
        }
        TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(trustStore);
        return trustFactory.getTrustManagers();
    }
}
