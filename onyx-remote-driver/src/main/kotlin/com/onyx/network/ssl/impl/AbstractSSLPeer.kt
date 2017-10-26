package com.onyx.network.ssl.impl

import com.onyx.network.auth.impl.AbstractNetworkPeer
import com.onyx.network.ssl.SSLPeer
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore

/**
 * Created by tosborn1 on 2/13/17.
 *
 * The purpose of this class is to abstract out the keystore file settings
 */
abstract class AbstractSSLPeer : SSLPeer {

    override var protocol = "TLSv1.2"
    override var sslStorePassword: String? = null
    override var sslKeystoreFilePath: String? = null
    override var sslKeystorePassword: String? = null
    override var sslTrustStoreFilePath: String? = null
    override var sslTrustStorePassword: String? = null

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
    @Throws(Exception::class)
    protected fun createKeyManagers(filepath: String, keystorePassword: String, keyPassword: String): Array<KeyManager> {
        val keyStore = KeyStore.getInstance("JKS")
        val keyStoreIS = AbstractNetworkPeer::class.java.classLoader.getResourceAsStream(filepath)
        keyStoreIS.use {
            keyStore.load(it, keystorePassword.toCharArray())
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, keyPassword.toCharArray())
        return kmf.keyManagers
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
    @Throws(Exception::class)
    protected fun createTrustManagers(filepath: String, trustStorePassword: String): Array<TrustManager> {
        val trustStore = KeyStore.getInstance("JKS")
        val trustStoreIS = AbstractNetworkPeer::class.java.classLoader.getResourceAsStream(filepath)
        trustStoreIS.use {
            trustStore.load(it, trustStorePassword.toCharArray())
        }
        val trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustFactory.init(trustStore)
        return trustFactory.trustManagers
    }
}
