package com.onyx.interactors.encryption.impl

import com.onyx.interactors.encryption.EncryptionInteractor
import com.onyx.interactors.encryption.data.Encryption

object DefaultEncryptionInteractorInstance : DefaultEncryptionInteractor()

open class DefaultEncryptionInteractor: EncryptionInteractor {
    override var encryption: Encryption? = null
    override val key: String = "This is some key, I found that it is more secure to use a sentence rather than a key word with special characters. That makes it harder to crack"
    override val iv: ByteArray = byteArrayOf(-12, -19, 17, -32, 86, 106, -31, 48, -5, -111, 61, -75, -127, 95, 120, -53)
}