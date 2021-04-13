package com.onyx.interactors.encryption.data

import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.io.UnsupportedEncodingException
import java.security.*
import java.security.spec.InvalidKeySpecException
import java.util.Arrays
import java.nio.charset.Charset
import java.security.MessageDigest

class Encryption private constructor(private val mBuilder: Builder) {

    private val encryptionCipher: Cipher by lazy {
        val secretKey = getSecretKey(hashTheKey(mBuilder.key))
        val cipher = Cipher.getInstance(mBuilder.algorithm!!)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, mBuilder.ivParameterSpec, mBuilder.secureRandom)
        return@lazy cipher
    }

    private val decryptionCipher: Cipher by lazy {
        val secretKey = getSecretKey(hashTheKey(mBuilder.key))
        val cipher = Cipher.getInstance(mBuilder.algorithm!!)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, mBuilder.ivParameterSpec, mBuilder.secureRandom)
        return@lazy cipher
    }

    @Throws(UnsupportedEncodingException::class, NoSuchAlgorithmException::class, NoSuchPaddingException::class, InvalidAlgorithmParameterException::class, InvalidKeyException::class, InvalidKeySpecException::class, BadPaddingException::class, IllegalBlockSizeException::class)
    private fun encrypt(data: String?): String? {
        if (data == null) return null
        val dataBytes = data.toByteArray(charset(mBuilder.charsetName!!))
        return Base64.encodeToString(encryptionCipher.doFinal(dataBytes), mBuilder.base64Mode)
    }

    @Synchronized
    fun encrypt(bytes: ByteArray): ByteArray =
            encryptionCipher.doFinal(bytes)

    fun encryptOrNull(data: String): String? = try {
        encrypt(data)
    } catch (e: Exception) {
        null
    }

    @Throws(UnsupportedEncodingException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class, NoSuchPaddingException::class, InvalidAlgorithmParameterException::class, InvalidKeyException::class, BadPaddingException::class, IllegalBlockSizeException::class)
    private fun decrypt(data: String?): String? {
        if (data == null) return null
        val dataBytes = Base64.decode(data, mBuilder.base64Mode)
        val dataBytesDecrypted = decryptionCipher.doFinal(dataBytes)
        return String(dataBytesDecrypted, charset(mBuilder.charsetName!!))
    }

    @Synchronized
    @Throws(UnsupportedEncodingException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class, NoSuchPaddingException::class, InvalidAlgorithmParameterException::class, InvalidKeyException::class, BadPaddingException::class, IllegalBlockSizeException::class)
    fun decrypt(data: ByteArray): ByteArray =
            decryptionCipher.doFinal(data)

    fun decryptOrNull(data: String): String? = try {
        decrypt(data)
    } catch (e: Exception) {
        null
    }

    @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class, InvalidKeySpecException::class)
    private fun getSecretKey(key: CharArray): SecretKey {
        var keyBytes:ByteArray = (String(key)).toByteArray(Charset.forName("UTF-8"))
        val digest:MessageDigest = MessageDigest.getInstance("SHA-1")
        keyBytes = digest.digest(keyBytes)
        keyBytes = Arrays.copyOf(keyBytes, 16) // use only first 128 bit

        return SecretKeySpec(keyBytes, "AES")
    }

    @Throws(UnsupportedEncodingException::class, NoSuchAlgorithmException::class)
    private fun hashTheKey(key: String?): CharArray {
        val messageDigest = MessageDigest.getInstance(mBuilder.digestAlgorithm)
        messageDigest.update(key!!.toByteArray(charset(mBuilder.charsetName!!)))
        return Base64.encodeToString(messageDigest.digest(), Base64.NO_PADDING).toCharArray()
    }

    @Suppress("RedundantVisibilityModifier")
    private class Builder(
            internal var iv: ByteArray? = null,
            internal var base64Mode: Int = 0,
            internal var key: String? = null,
            internal var algorithm: String? = null,
            internal var charsetName: String? = null,
            internal var digestAlgorithm: String? = null,
            internal var secureRandomAlgorithm: String? = null,
            internal var secureRandom: SecureRandom? = null,
            internal var ivParameterSpec: IvParameterSpec? = null
    ) {

        @Throws(NoSuchAlgorithmException::class)
        fun build(): Encryption {
            secureRandom = (SecureRandom.getInstance(secureRandomAlgorithm))
            ivParameterSpec = (IvParameterSpec(iv!!))
            return Encryption(this)
        }

        companion object {

            internal fun getDefaultBuilder(key: String, iv: ByteArray): Builder =
                    Builder(iv = iv, key = key, charsetName = "UTF8", digestAlgorithm = "SHA1", base64Mode = Base64.DEFAULT, algorithm = "AES/CBC/PKCS5Padding", secureRandomAlgorithm = "SHA1PRNG")

        }
    }

    companion object {
        fun getDefault(key: String, iv: ByteArray): Encryption? {
            return try {
                Builder.getDefaultBuilder(key, iv).build()
            } catch (e: NoSuchAlgorithmException) {
                null
            }
        }
    }

}