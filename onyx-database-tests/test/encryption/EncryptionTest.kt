package encryption

import com.onyx.interactors.encryption.impl.DefaultEncryptionInteractor
import org.junit.Assert
import org.junit.Test

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.security.GeneralSecurityException

/**
 *
 * @author cosbor11
 */
class EncryptionTest {

    @Test
    @Throws(GeneralSecurityException::class, UnsupportedEncodingException::class)
    fun shouldEncrypt() {
        val encryptedText = DefaultEncryptionInteractor.encrypt("adminadmin")
        Assert.assertTrue(encryptedText != null)
    }

    @Test
    @Throws(GeneralSecurityException::class, IOException::class)
    fun shouldDycrypt() {
        val str = "adminadmin"
        val encryptedText = DefaultEncryptionInteractor.encrypt(str)
        val decryptedText = DefaultEncryptionInteractor.decrypt(encryptedText!!)

        Assert.assertTrue(str == decryptedText)

    }

    @Test
    @Throws(GeneralSecurityException::class, IOException::class)
    fun shouldFail() {
        val str = "adminadmin"
        val str2 = "adminpassword"
        val decryptedText = DefaultEncryptionInteractor.decrypt(DefaultEncryptionInteractor.encrypt(str2)!!)

        Assert.assertFalse(str == decryptedText)
    }


    @Test
    @Throws(GeneralSecurityException::class, IOException::class)
    fun shouldWriteToFile() {
        val textToSave = "asdfasdf"

        //Create a temporary file
        val file = File(FILE_LOCATION + "/tmp")
        file.parentFile.mkdirs()
        file.createNewFile()
        //Next, write the encrypted bytes to the file.

        FileOutputStream(file).use { fileStream ->
            fileStream.write(DefaultEncryptionInteractor.encrypt(textToSave)!!.toByteArray(StandardCharsets.UTF_8))
            fileStream.close()
        }

        val savedText = String(Files.readAllBytes(Paths.get(file.absolutePath)))

        //make assertions
        Assert.assertEquals(savedText, DefaultEncryptionInteractor.encrypt(textToSave))

        //cleanup file
        val delete = file.delete()
        //Now lets read from the file

    }

    companion object {

        protected val FILE_LOCATION = "C:/Sandbox/Onyx/Tests/encryption"
    }


}
