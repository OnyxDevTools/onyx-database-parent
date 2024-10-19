package com.onyx.extension.common

import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.*

private val uuidLock = Object()
private var lastTimestamp = -1L
private val random = SecureRandom()
private var clockSequence: Long = random.nextLong() and 0x3FFFL
private const val UUID_EPOCH_OFFSET = 0x01b21dd213814000L

fun uuid(): String {
    val currentTime = System.currentTimeMillis() * 10000 + (System.nanoTime() % 10000) + UUID_EPOCH_OFFSET

    synchronized(uuidLock::class.java) {
        if (currentTime > lastTimestamp) {
            lastTimestamp = currentTime
        } else {
            clockSequence =
                clockSequence + 1 and 0x3FFFL // Increment clock sequence to avoid collision
        }
    }

    val timeLow = currentTime and 0xFFFFFFFFL
    val timeMid = (currentTime shr 32) and 0xFFFFL
    val timeHighAndVersion = ((currentTime shr 48) and 0x0FFFL) or (1 shl 12).toLong() // Version 1

    val mostSigBits = (timeLow shl 32) or (timeMid shl 16) or timeHighAndVersion
    val leastSigBits = (clockSequence shl 48) or nodeIdentifier

    return UUID(mostSigBits, leastSigBits).toString()
}

private fun isValidUuid(uuid: Any?): Boolean {
    if (uuid !is String) return false
    return try {
        UUID.fromString(uuid)
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}

fun Any.uuidHash(): Int? = if (this is String && isValidUuid(this)) {
    (UUID.fromString(this).timestamp() / 10000000L).hashCode()
} else null

private val nodeIdentifier: Long by lazy {
    return@lazy try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        var node = 0L
        while (interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            val mac = network.hardwareAddress
            if (mac != null && mac.size == 6) {
                for (i in 0..5) {
                    node = node or ((mac[i].toLong() and 0xFFL) shl ((5 - i) * 8))
                }
                return@lazy node or 0x0000010000000000L // Multicast bit set to 1 to indicate local node
            }
        }
        return@lazy node
    } catch (e: Exception) {
        1L
    }
}
