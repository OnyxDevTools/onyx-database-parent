package com.onyx.util;

/**
 * Class that is a quick and dirty encryption utility.
 */
@SuppressWarnings("unused")
public final class EncryptionUtil
{

    private static final byte[] iv = {-12, -19, 17, -32, 86, 106, -31, 48, -5, -111, 61, -75, -127, 95, 120, -53};

    // Encryption Instance
    private static Encryption encryption;

    static
    {
        try {
            encryption = Encryption.getDefault("M1fancyKey12$", "DeFAul1$&lT", iv);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    /**
     * Performs Decryption.
     * @param encryptedText Text to Decrypt
     * @return Decrypted Text
     */
    @SuppressWarnings("unused")
    public static String decrypt(String encryptedText)
    {
        return encryption.decryptOrNull(encryptedText);
    }

    /**
     * Performs Encryption
     *
     * @param plainText Text to Encrypt.  Note, if you pass in UTF-8 characters, you should
     *                  expect to get UTF-8 characters back out.  So, if you expect for encryption
     *                  and decryption to work on devices with different Character sets you must ensure
     *                  the user name and password have the same character encoding as what you send in.
     *
     * @return Encrypted String
     */
    public static String encrypt(String plainText)
    {
        return encryption.encryptOrNull(plainText);
    }

}
