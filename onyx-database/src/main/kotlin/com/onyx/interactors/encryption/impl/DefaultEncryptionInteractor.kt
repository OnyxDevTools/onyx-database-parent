package com.onyx.interactors.encryption.impl

import com.onyx.interactors.encryption.EncryptionInteractor

object DefaultEncryptionInteractor: EncryptionInteractor {
    override val key: String = "This is some key, I found that it is more secure to use a sentence rather than a key word with special characters. That makes it harder to crack"
    override val salt: String = "DeFAul1\$&lT"
    override val iv: ByteArray = byteArrayOf(-12, -19, 17, -32, 86, 106, -31, 48, -5, -111, 61, -75, -127, 95, 120, -53)
}