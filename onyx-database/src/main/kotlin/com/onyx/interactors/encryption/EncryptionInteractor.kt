package com.onyx.interactors.encryption

import com.onyx.interactors.encryption.data.Encryption


/**
 * Class that is a quick and dirty encryption utility.
 */
interface EncryptionInteractor {

    /**
     * Encryption Key
     */
    val key:String

    /**
     * Encryption IV
     */
    val iv:ByteArray

    /**
     * Holds the generated key
     */
    var encryption:Encryption?

    /**
     * Performs Decryption.
     * @param encryptedBytes ByteArray to Decrypt
     * @return Decrypted Text
     */
    fun decrypt(encryptedBytes: ByteArray): ByteArray = encryption().decrypt(encryptedBytes)

    /**
     * Performs Decryption.
     * @param encryptedText Text to Decrypt
     * @return Decrypted Text
     */
    fun decrypt(encryptedText: String): String? = encryption().decryptOrNull(encryptedText)

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
    fun encrypt(plainText: String): String? = encryption().encryptOrNull(plainText)

    /**
     * Performs Encryption
     *
     * @param bytes Bytes to Encrypt.
     *
     * @return Encrypted String
     */
    fun encrypt(bytes: ByteArray): ByteArray? = encryption().encrypt(bytes)

    /**
     * Instantiates the encryption
     */
    fun encryption():Encryption {
        if(encryption == null) {
            encryption = Encryption.getDefault(key, iv)
        }
        return encryption!!
    }
}


