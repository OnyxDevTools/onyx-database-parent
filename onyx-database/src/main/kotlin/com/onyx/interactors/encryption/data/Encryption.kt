package com.onyx.interactors.encryption.data

import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.io.UnsupportedEncodingException
import java.security.*
import java.security.spec.InvalidKeySpecException

class Encryption private constructor(private val mBuilder: Builder) {

    @Throws(UnsupportedEncodingException::class, NoSuchAlgorithmException::class, NoSuchPaddingException::class, InvalidAlgorithmParameterException::class, InvalidKeyException::class, InvalidKeySpecException::class, BadPaddingException::class, IllegalBlockSizeException::class)
    private fun encrypt(data: String?): String? {
        if (data == null) return null
        val secretKey = getSecretKey(hashTheKey(mBuilder.key))
        val dataBytes = data.toByteArray(charset(mBuilder.charsetName!!))
        val cipher = Cipher.getInstance(mBuilder.algorithm!!)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, mBuilder.ivParameterSpec, mBuilder.secureRandom)
        return Base64.encodeToString(cipher.doFinal(dataBytes), mBuilder.base64Mode)
    }

    fun encryptOrNull(data: String): String? = try {
        encrypt(data)
    } catch (e: Exception) {
        null
    }

    @Throws(UnsupportedEncodingException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class, NoSuchPaddingException::class, InvalidAlgorithmParameterException::class, InvalidKeyException::class, BadPaddingException::class, IllegalBlockSizeException::class)
    private fun decrypt(data: String?): String? {
        if (data == null) return null
        val dataBytes = Base64.decode(data, mBuilder.base64Mode)
        val secretKey = getSecretKey(hashTheKey(mBuilder.key))
        val cipher = Cipher.getInstance(mBuilder.algorithm!!)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, mBuilder.ivParameterSpec, mBuilder.secureRandom)
        val dataBytesDecrypted = cipher.doFinal(dataBytes)
        return String(dataBytesDecrypted)
    }

    fun decryptOrNull(data: String): String? = try {
        decrypt(data)
    } catch (e: Exception) {
        null
    }

    @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class, InvalidKeySpecException::class)
    private fun getSecretKey(key: CharArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(mBuilder.secretKeyType)
        val spec = PBEKeySpec(key, mBuilder.salt!!.toByteArray(charset(mBuilder.charsetName!!)), mBuilder.iterationCount, mBuilder.keyLength)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, mBuilder.keyAlgorithm!!)
    }

    @Throws(UnsupportedEncodingException::class, NoSuchAlgorithmException::class)
    private fun hashTheKey(key: String?): CharArray {
        val messageDigest = MessageDigest.getInstance(mBuilder.digestAlgorithm)
        messageDigest.update(key!!.toByteArray(charset(mBuilder.charsetName!!)))
        return Base64.encodeToString(messageDigest.digest(), Base64.NO_PADDING).toCharArray()
    }

    private class Builder(
            internal var iv: ByteArray? = null,
            internal var keyLength: Int = 0,
            internal var base64Mode: Int = 0,
            internal var iterationCount: Int = 0,
            internal var salt: String? = null,
            internal var key: String? = null,
            internal var algorithm: String? = null,
            internal var keyAlgorithm: String? = null,
            internal var charsetName: String? = null,
            internal var secretKeyType: String? = null,
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

            internal fun getDefaultBuilder(key: String, salt: String, iv: ByteArray): Builder =
                    Builder(iv = iv, key = key, salt = salt, keyLength = 128, keyAlgorithm = "AES", charsetName = "UTF8", iterationCount = 65536, digestAlgorithm = "SHA1", base64Mode = Base64.DEFAULT, algorithm = "AES/CBC/PKCS5Padding", secureRandomAlgorithm = "SHA1PRNG", secretKeyType = "PBKDF2WithHmacSHA1")

        }
    }

    companion object {
        fun getDefault(key: String, salt: String, iv: ByteArray): Encryption? {
            return try {
                Builder.getDefaultBuilder(key, salt, iv).build()
            } catch (e: NoSuchAlgorithmException) {
                null
            }
        }
    }

}