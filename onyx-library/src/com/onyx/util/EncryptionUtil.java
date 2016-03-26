package com.onyx.util;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;

public final class EncryptionUtil
{

    private final static String OS = System.getProperty("os.name");

    private final static String algorithm = "AES";
    private final static byte[] keyValue = new byte[]
    {
        'H', 'O', 'w', 'd', 'Y', 'D', '0', '0', 't', 'y', 'b', 'j', 'X', '8', '5', 'j'
    };

    private static final char[] HEX =
    {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    public static boolean isWindows()
    {
        return OS.startsWith("Windows");
    }

    private static char[] unixEncode(byte[] bytes)
    {
        final int nBytes = bytes.length;
        char[] result = new char[2 * nBytes];

        int j = 0;
        for (int i = 0; i < nBytes; i++)
        {
            // Char for top 4 bits
            result[j++] = HEX[(0xF0 & bytes[i]) >>> 4];
            // Bottom 4
            result[j++] = HEX[(0x0F & bytes[i])];
        }

        return result;
    }

    private static byte[] unixDecode(CharSequence s)
    {
        int nChars = s.length();

        if (nChars % 2 != 0)
        {
            throw new IllegalArgumentException("Hex-encoded string must have an even number of characters");
        }

        byte[] result = new byte[nChars / 2];

        for (int i = 0; i < nChars; i += 2)
        {
            int msb = Character.digit(s.charAt(i), 16);
            int lsb = Character.digit(s.charAt(i + 1), 16);

            if (msb < 0 || lsb < 0)
            {
                throw new IllegalArgumentException("Non-hex character in input: " + s);
            }
            result[i / 2] = (byte) ((msb << 4) | lsb);
        }
        return result;
    }

    // Performs Encryption
    private static String unixEncrypt(String plainText)
    {
        Key key = null;
        String encryptedValue = null;
        try
        {
            key = generateKey();
            Cipher chiper = Cipher.getInstance(algorithm);
            chiper.init(Cipher.ENCRYPT_MODE, key);
            byte[] encVal = chiper.doFinal(plainText.getBytes());
            encryptedValue = new String(unixEncode(encVal));
        }
        catch (Exception e)
        {
        }

        return encryptedValue;
    }

    private static String unixDecrypt(String encryptedText)
    {
        // generate key
        Key key = null;
        String decryptedValue = null;

        try
        {
            key = generateKey();
            Cipher chiper = Cipher.getInstance(algorithm);
            chiper.init(Cipher.DECRYPT_MODE, key);
            byte[] decordedValue = unixDecode(encryptedText);

            byte[] decValue = chiper.doFinal(decordedValue);
            decryptedValue = new String(decValue);
        }
        catch (Exception e)
        {
        }

        return decryptedValue;
    }

    //generateKey() is used to generate a secret key for AES algorithm
    private static Key generateKey() throws Exception
    {
        Key key = new SecretKeySpec(keyValue, algorithm);
        return key;
    }

    //Windows Encryption
    private final static byte[] WIN_RAW_KEY = new byte[]
    {
        'O', 'n', 'y', 'x', 'D', 'e', 'V', 'T', 'o', 'o', 'L', 'z', '.', 'C', '0', 'M'
    };

    private static String windowsEncrypt(String value)
    {
        byte[] encrypted = null;
        try
        {

            Key skeySpec = new SecretKeySpec(WIN_RAW_KEY, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] iv = new byte[cipher.getBlockSize()];

            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivParams);
            encrypted = cipher.doFinal(value.getBytes());

        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException ignore)
        {
        }
        return new String(encrypted);
    }

    private static String windowsDecrypt(String value)
    {
        byte[] encrypted = value.getBytes();
        byte[] original = null;
        Cipher cipher = null;
        try
        {
            Key key = new SecretKeySpec(WIN_RAW_KEY, "AES");
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] ivByte = new byte[cipher.getBlockSize()];
            IvParameterSpec ivParamsSpec = new IvParameterSpec(ivByte);
            cipher.init(Cipher.DECRYPT_MODE, key, ivParamsSpec);
            original = cipher.doFinal(encrypted);
        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException ignore)
        {
        }
        return new String(original);
    }

    // Performs decryption
    public static String decrypt(String encryptedText)
    {
        if (isWindows())
        {
            return windowsDecrypt(encryptedText);
        }
        else
        {
            return unixDecrypt(encryptedText);
        }
    }

    // Performs Encryption
    public static String encrypt(String plainText)
    {
        if (isWindows())
        {
            return windowsEncrypt(plainText);
        }
        else
        {
            return unixEncrypt(plainText);
        }
    }

}
