package com.onyx.extension.common

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
            clockSequence = 0 // Reset the clock sequence if time has progressed
        } else {
            clockSequence += 1 // Increment the clock sequence to ensure uniqueness
        }
    }

    // Split the timestamp into components
    val timeLow = currentTime and 0xFFFFFFFFL
    val timeMid = (currentTime shr 32) and 0xFFFFL
    val timeHighAndVersion = ((currentTime shr 48) and 0x0FFFL) or (1 shl 12).toLong() // Version 1 UUID

    // Create most significant bits using time components
    val mostSigBits = (timeLow shl 32) or (timeMid shl 16) or timeHighAndVersion

    // Generate a semi-static nodeIdentifier, with low variability
    val semiStaticNodeIdentifier = (0x0000010002A1BL or (System.nanoTime() % 0xFF)) // Introduces minimal randomness
    val leastSigBits = ((clockSequence.toLong() and 0x3FFFL) shl 48) or semiStaticNodeIdentifier

    // Return the UUID string
    return UUID(mostSigBits, leastSigBits).toString()
}
