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
     * Encryption Salt
     */
    val salt:String

    /**
     * Encryption IV
     */
    val iv:ByteArray

    /**
     * Performs Decryption.
     * @param encryptedText Text to Decrypt
     * @return Decrypted Text
     */
    fun decrypt(encryptedText: String): String? = DefaultEncryption.encrypt(this).decryptOrNull(encryptedText)

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
    fun encrypt(plainText: String): String? = DefaultEncryption.encrypt(this).encryptOrNull(plainText)

}

object DefaultEncryption {
    var encryption:Encryption? = null

    fun encrypt(interactor: EncryptionInteractor):Encryption {
        if(encryption == null) encryption = Encryption.getDefault(interactor.key, interactor.salt, interactor.iv)
        return encryption!!
    }
}

