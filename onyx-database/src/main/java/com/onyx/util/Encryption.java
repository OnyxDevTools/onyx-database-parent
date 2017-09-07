package com.onyx.util;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/**
 * A class to make more easy and simple the encrypt routines, this is the core of Encryption library
 */
class Encryption {

    /**
     * The Builder used to create the Encryption instance and that contains the information about
     * encryption specifications, this instance need to be private and careful managed
     */
    private final Builder mBuilder;

    /**
     * The private and unique constructor, you should use the Encryption.Builder to build your own
     * instance or get the default proving just the sensible information about encryption
     */
    private Encryption(Builder builder) {
        mBuilder = builder;
    }

    /**
     * @return an default encryption instance or {@code null} if occur some Exception, you can
     * create yur own Encryption instance using the Encryption.Builder
     */
    static Encryption getDefault(@SuppressWarnings("SameParameterValue") String key, @SuppressWarnings("SameParameterValue") String salt, @SuppressWarnings("SameParameterValue") byte[] iv) {
        try {
            return Builder.getDefaultBuilder(key, salt, iv).build();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }


    /**
     * Encrypt a String
     *
     * @param data the String to be encrypted
     *
     * @return the encrypted String or {@code null} if you send the data as {@code null}
     *
     * @throws UnsupportedEncodingException       if the Builder charset name is not supported or if
     *                                            the Builder charset name is not supported
     * @throws NoSuchAlgorithmException           if the Builder digest algorithm is not available
     *                                            or if this has no installed provider that can
     *                                            provide the requested by the Builder secret key
     *                                            type or it is {@code null}, empty or in an invalid
     *                                            format
     * @throws NoSuchPaddingException             if no installed provider can provide the padding
     *                                            scheme in the Builder digest algorithm
     * @throws InvalidAlgorithmParameterException if the specified parameters are inappropriate for
     *                                            the cipher
     * @throws InvalidKeyException                if the specified key can not be used to initialize
     *                                            the cipher instance
     * @throws InvalidKeySpecException            if the specified key specification cannot be used
     *                                            to generate a secret key
     * @throws BadPaddingException                if the padding of the data does not match the
     *                                            padding scheme
     * @throws IllegalBlockSizeException          if the size of the resulting bytes is not a
     *                                            multiple of the cipher block size
     * @throws NullPointerException               if the Builder digest algorithm is {@code null} or
     *                                            if the specified Builder secret key type is
     *                                            {@code null}
     * @throws IllegalStateException              if the cipher instance is not initialized for
     *                                            encryption or decryption
     */
    private String encrypt(String data) throws UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, InvalidKeySpecException, BadPaddingException, IllegalBlockSizeException {
        if (data == null) return null;
        SecretKey secretKey = getSecretKey(hashTheKey(mBuilder.getKey()));
        byte[] dataBytes = data.getBytes(mBuilder.getCharsetName());
        Cipher cipher = Cipher.getInstance(mBuilder.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, mBuilder.getIvParameterSpec(), mBuilder.getSecureRandom());
        return Base64.encodeToString(cipher.doFinal(dataBytes), mBuilder.getBase64Mode());
    }

    /**
     * This is a sugar method that calls encrypt method and catch the exceptions returning
     * {@code null} when it occurs and logging the error
     *
     * @param data the String to be encrypted
     *
     * @return the encrypted String or {@code null} if you send the data as {@code null}
     */
    String encryptOrNull(String data) {
        try {
            return encrypt(data);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decrypt a String
     *
     * @param data the String to be decrypted
     *
     * @return the decrypted String or {@code null} if you send the data as {@code null}
     *
     * @throws UnsupportedEncodingException       if the Builder charset name is not supported or if
     *                                            the Builder charset name is not supported
     * @throws NoSuchAlgorithmException           if the Builder digest algorithm is not available
     *                                            or if this has no installed provider that can
     *                                            provide the requested by the Builder secret key
     *                                            type or it is {@code null}, empty or in an invalid
     *                                            format
     * @throws NoSuchPaddingException             if no installed provider can provide the padding
     *                                            scheme in the Builder digest algorithm
     * @throws InvalidAlgorithmParameterException if the specified parameters are inappropriate for
     *                                            the cipher
     * @throws InvalidKeyException                if the specified key can not be used to initialize
     *                                            the cipher instance
     * @throws InvalidKeySpecException            if the specified key specification cannot be used
     *                                            to generate a secret key
     * @throws BadPaddingException                if the padding of the data does not match the
     *                                            padding scheme
     * @throws IllegalBlockSizeException          if the size of the resulting bytes is not a
     *                                            multiple of the cipher block size
     * @throws NullPointerException               if the Builder digest algorithm is {@code null} or
     *                                            if the specified Builder secret key type is
     *                                            {@code null}
     * @throws IllegalStateException              if the cipher instance is not initialized for
     *                                            encryption or decryption
     */
    private String decrypt(String data) throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        if (data == null) return null;
        byte[] dataBytes = Base64.decode(data, mBuilder.getBase64Mode());
        SecretKey secretKey = getSecretKey(hashTheKey(mBuilder.getKey()));
        Cipher cipher = Cipher.getInstance(mBuilder.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, secretKey, mBuilder.getIvParameterSpec(), mBuilder.getSecureRandom());
        byte[] dataBytesDecrypted = (cipher.doFinal(dataBytes));
        return new String(dataBytesDecrypted);
    }

    /**
     * This is a sugar method that calls decrypt method and catch the exceptions returning
     * {@code null} when it occurs and logging the error
     *
     * @param data the String to be decrypted
     *
     * @return the decrypted String or {@code null} if you send the data as {@code null}
     */
    String decryptOrNull(String data) {
        try {
            return decrypt(data);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * creates a 128bit salted aes key
     *
     * @param key encoded input key
     *
     * @return aes 128 bit salted key
     *
     * @throws NoSuchAlgorithmException     if no installed provider that can provide the requested
     *                                      by the Builder secret key type
     * @throws UnsupportedEncodingException if the Builder charset name is not supported
     * @throws InvalidKeySpecException      if the specified key specification cannot be used to
     *                                      generate a secret key
     * @throws NullPointerException         if the specified Builder secret key type is {@code null}
     */
    private SecretKey getSecretKey(char[] key) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(mBuilder.getSecretKeyType());
        KeySpec spec = new PBEKeySpec(key, mBuilder.getSalt().getBytes(mBuilder.getCharsetName()), mBuilder.getIterationCount(), mBuilder.getKeyLength());
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), mBuilder.getKeyAlgorithm());
    }

    /**
     * takes in a simple string and performs an sha1 hash
     * that is 128 bits long...we then base64 encode it
     * and return the char array
     *
     * @param key simple inputted string
     *
     * @return sha1 base64 encoded representation
     *
     * @throws UnsupportedEncodingException if the Builder charset name is not supported
     * @throws NoSuchAlgorithmException     if the Builder digest algorithm is not available
     * @throws NullPointerException         if the Builder digest algorithm is {@code null}
     */
    private char[] hashTheKey(String key) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance(mBuilder.getDigestAlgorithm());
        messageDigest.update(key.getBytes(mBuilder.getCharsetName()));
        return Base64.encodeToString(messageDigest.digest(), Base64.NO_PADDING).toCharArray();
    }

    /**
     * This class is used to create an Encryption instance, you should provide ALL data or start
     * with the Default Builder provided by the getDefaultBuilder method
     */
    private static class Builder {

        private byte[] mIv;
        private int mKeyLength;
        private int mBase64Mode;
        private int mIterationCount;
        private String mSalt;
        private String mKey;
        private String mAlgorithm;
        private String mKeyAlgorithm;
        private String mCharsetName;
        private String mSecretKeyType;
        private String mDigestAlgorithm;
        private String mSecureRandomAlgorithm;
        private SecureRandom mSecureRandom;
        private IvParameterSpec mIvParameterSpec;

        /**
         * @return an default builder with the follow defaults:
         * the default char set is UTF-8
         * the default base mode is Base64
         * the Secret Key Type is the PBKDF2WithHmacSHA1
         * the default salt is "some_salt" but can be anything
         * the default length of key is 128
         * the default iteration count is 65536
         * the default algorithm is AES in CBC mode and PKCS 5 Padding
         * the default secure random algorithm is SHA1PRNG
         * the default message digest algorithm SHA1
         */
        static Builder getDefaultBuilder(String key, String salt, byte[] iv) {
            return new Builder()
                    .setIv(iv)
                    .setKey(key)
                    .setSalt(salt)
                    .setKeyLength(128)
                    .setKeyAlgorithm("AES")
                    .setCharsetName("UTF8")
                    .setIterationCount(65536)
                    .setDigestAlgorithm("SHA1")
                    .setBase64Mode(Base64.DEFAULT)
                    .setAlgorithm("AES/CBC/PKCS5Padding")
                    .setSecureRandomAlgorithm("SHA1PRNG")
                    .setSecretKeyType("PBKDF2WithHmacSHA1");
        }

        /**
         * Build the Encryption with the provided information
         *
         * @return a new Encryption instance with provided information
         *
         * @throws NoSuchAlgorithmException if the specified SecureRandomAlgorithm is not available
         * @throws NullPointerException     if the SecureRandomAlgorithm is {@code null} or if the
         *                                  IV byte array is null
         */
        public Encryption build() throws NoSuchAlgorithmException {
            setSecureRandom(SecureRandom.getInstance(getSecureRandomAlgorithm()));
            setIvParameterSpec(new IvParameterSpec(getIv()));
            return new Encryption(this);
        }

        //region getters and setters

        /**
         * @return the charset name
         */
        private String getCharsetName() {
            return mCharsetName;
        }

        /**
         * @param charsetName the new charset name
         *
         * @return this instance to follow the Builder patter
         */
        Builder setCharsetName(@SuppressWarnings("SameParameterValue") String charsetName) {
            mCharsetName = charsetName;
            return this;
        }

        /**
         * @return the algorithm
         */
        private String getAlgorithm() {
            return mAlgorithm;
        }

        /**
         * @param algorithm the algorithm to be used
         *
         * @return this instance to follow the Builder patter
         */
        Builder setAlgorithm(@SuppressWarnings("SameParameterValue") String algorithm) {
            mAlgorithm = algorithm;
            return this;
        }

        /**
         * @return the key algorithm
         */
        private String getKeyAlgorithm() {
            return mKeyAlgorithm;
        }

        /**
         * @param keyAlgorithm the keyAlgorithm to be used in keys
         *
         * @return this instance to follow the Builder patter
         */
        Builder setKeyAlgorithm(@SuppressWarnings("SameParameterValue") String keyAlgorithm) {
            mKeyAlgorithm = keyAlgorithm;
            return this;
        }

        /**
         * @return the Base 64 mode
         */
        private int getBase64Mode() {
            return mBase64Mode;
        }

        /**
         * @param base64Mode set the base 64 mode
         *
         * @return this instance to follow the Builder patter
         */
        Builder setBase64Mode(@SuppressWarnings("SameParameterValue") int base64Mode) {
            mBase64Mode = base64Mode;
            return this;
        }

        /**
         * @return the type of aes key that will be created, on KITKAT+ the API has changed, if you
         * are getting problems please @see <a href="http://android-developers.blogspot.com.br/2013/12/changes-to-secretkeyfactory-api-in.html">http://android-developers.blogspot.com.br/2013/12/changes-to-secretkeyfactory-api-in.html</a>
         */
        private String getSecretKeyType() {
            return mSecretKeyType;
        }

        /**
         * @param secretKeyType the type of AES key that will be created, on KITKAT+ the API has
         *                      changed, if you are getting problems please @see <a href="http://android-developers.blogspot.com.br/2013/12/changes-to-secretkeyfactory-api-in.html">http://android-developers.blogspot.com.br/2013/12/changes-to-secretkeyfactory-api-in.html</a>
         *
         * @return this instance to follow the Builder patter
         */
        Builder setSecretKeyType(@SuppressWarnings("SameParameterValue") String secretKeyType) {
            mSecretKeyType = secretKeyType;
            return this;
        }

        /**
         * @return the key used for salting
         */
        private String getSalt() {
            return mSalt;
        }

        /**
         * @param salt the key used for salting
         *
         * @return this instance to follow the Builder patter
         */
        Builder setSalt(String salt) {
            mSalt = salt;
            return this;
        }

        /**
         * @return the key
         */
        private String getKey() {
            return mKey;
        }

        /**
         * @param key the key.
         *
         * @return this instance to follow the Builder patter
         */
        public Builder setKey(String key) {
            mKey = key;
            return this;
        }

        /**
         * @return the length of key
         */
        private int getKeyLength() {
            return mKeyLength;
        }

        /**
         * @param keyLength the length of key
         *
         * @return this instance to follow the Builder patter
         */
        Builder setKeyLength(@SuppressWarnings("SameParameterValue") int keyLength) {
            mKeyLength = keyLength;
            return this;
        }

        /**
         * @return the number of times the password is hashed
         */
        private int getIterationCount() {
            return mIterationCount;
        }

        /**
         * @param iterationCount the number of times the password is hashed
         *
         * @return this instance to follow the Builder patter
         */
        Builder setIterationCount(@SuppressWarnings("SameParameterValue") int iterationCount) {
            mIterationCount = iterationCount;
            return this;
        }

        /**
         * @return the algorithm used to generate the secure random
         */
        private String getSecureRandomAlgorithm() {
            return mSecureRandomAlgorithm;
        }

        /**
         * @param secureRandomAlgorithm the algorithm to generate the secure random
         *
         * @return this instance to follow the Builder patter
         */
        Builder setSecureRandomAlgorithm(@SuppressWarnings("SameParameterValue") String secureRandomAlgorithm) {
            mSecureRandomAlgorithm = secureRandomAlgorithm;
            return this;
        }

        /**
         * @return the IvParameterSpec bytes array
         */
        private byte[] getIv() {
            return mIv;
        }

        /**
         * @param iv the byte array to create a new IvParameterSpec
         *
         * @return this instance to follow the Builder patter
         */
        Builder setIv(byte[] iv) {
            mIv = iv;
            return this;
        }

        /**
         * @return the SecureRandom
         */
        private SecureRandom getSecureRandom() {
            return mSecureRandom;
        }

        /**
         * @param secureRandom the Secure Random
         *
         * @return this instance to follow the Builder patter
         */
        @SuppressWarnings("UnusedReturnValue")
        Builder setSecureRandom(SecureRandom secureRandom) {
            mSecureRandom = secureRandom;
            return this;
        }

        /**
         * @return the IvParameterSpec
         */
        private IvParameterSpec getIvParameterSpec() {
            return mIvParameterSpec;
        }

        /**
         * @param ivParameterSpec the IvParameterSpec
         *
         * @return this instance to follow the Builder patter
         */
        @SuppressWarnings("UnusedReturnValue")
        Builder setIvParameterSpec(IvParameterSpec ivParameterSpec) {
            mIvParameterSpec = ivParameterSpec;
            return this;
        }

        /**
         * @return the message digest algorithm
         */
        private String getDigestAlgorithm() {
            return mDigestAlgorithm;
        }

        /**
         * @param digestAlgorithm the algorithm to be used to get message digest instance
         *
         * @return this instance to follow the Builder patter
         */
        Builder setDigestAlgorithm(@SuppressWarnings("SameParameterValue") String digestAlgorithm) {
            mDigestAlgorithm = digestAlgorithm;
            return this;
        }

        //endregion
    }
}