package com.onyx.interactors.encryption

import com.onyx.interactors.encryption.data.Encryption

/**
 * Class that is a quick and dirty encryption utility.
 */
interface EncryptionInteractor {

    /**
     * Performs Decryption.
     * @param encryptedText Text to Decrypt
     * @return Decrypted Text
     */
    fun decrypt(encryptedText: String): String? = DefaultEncryption.encrypt.decryptOrNull(encryptedText)

    /**
     * Performs Encryption
     *
     * @param plainText Text to Encrypt.  Note, if you pass in UTF-8 characters, you should
     * expect to get UTF-8 characters back out.  So, if you expect for encryption
     * and decryption to work on devices with different Character sets you must ensure
     * the user name and password have the same character encoding as what you send in.
     *
     * @return Encrypted String
     */
    fun encrypt(plainText: String): String? = DefaultEncryption.encrypt.encryptOrNull(plainText)

}

object DefaultEncryption {
    private val iv = byteArrayOf(-12, -19, 17, -32, 86, 106, -31, 48, -5, -111, 61, -75, -127, 95, 120, -53)
    var encrypt: Encryption = Encryption.getDefault("M1fancyKey12$", "DeFAul1$&lT", iv)
}

object DefaultEncryptionInteractor: EncryptionInteractor