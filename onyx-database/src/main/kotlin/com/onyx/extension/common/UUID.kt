package com.onyx.extension.common
import java.util.*

private const val UUID_EPOCH_OFFSET: Long = 122192928000000000L // in 100-nanosecond units

private var lastTimestamp = 0L
private var clockSequence = 0
private val uuidLock = Any()

/**
 * Generates a custom time-based UUID (similar to Version 1).
 */
fun uuid(): String {
    val currentTime = System.currentTimeMillis() * 10_000 + (System.nanoTime() % 10_000) + UUID_EPOCH_OFFSET

    synchronized(uuidLock) {
        if (currentTime > lastTimestamp) {
            lastTimestamp = currentTime
            clockSequence = 0 // Reset the clock sequence if time has progressed
        } else {
            clockSequence += 1 // Increment the clock sequence to ensure uniqueness
            if (clockSequence > 0x3FFF) { // Clock sequence overflow (max value is 16383)
                // In a real-world scenario, you would either:
                // 1. Wait until the next 100-ns interval
                // 2. Use a random clock sequence (risking collisions)
                // 3. Throw an exception (indicating rate limiting)
                // For this example, let's just reset it:
                clockSequence = 0
            }
        }
    }

    // Split the timestamp into components
    val timeLow = currentTime and 0xFFFFFFFFL
    val timeMid = (currentTime shr 32) and 0xFFFFL
    val timeHighAndVersion = ((currentTime shr 48) and 0x0FFFL) or (1L shl 12) // Version 1 UUID, corrected shift

    // Create most significant bits using time components
    val mostSigBits = (timeLow shl 32) or (timeMid shl 16) or timeHighAndVersion

    // Create least significant bits using a simplified semi-static node identifier
    // and the clock sequence.
    // In a production environment, consider using a more robust method to generate the node identifier
    // such as using the MAC address of the machine.
    val nodeIdentifier = 0x800000000000L or (System.nanoTime() and 0x3FFFFFFFFFFFL) // set multicast bit as per the standard
    val leastSigBits = (clockSequence.toLong() shl 48) or nodeIdentifier

    // Return the UUID string
    return UUID(mostSigBits, leastSigBits).toString()
}

/**
 * Extracts the creation date from a UUID generated by the custom UUID generator.
 *
 * @return The creation date as Date, or null if extraction fails.
 */
fun String.getCreationDateFromUUID(): Date? {
    return try {
        val uuid = UUID.fromString(this)
        extractTimestamp(uuid)
    } catch (e: IllegalArgumentException) {
        // Invalid UUID string
        println("Invalid UUID string: $this")
        null
    }
}

/**
 * Extracts the timestamp from the UUID and converts it to Date.
 *
 * @param uuid The UUID object.
 * @return The creation date as Date, or null if extraction fails.
 */
private fun extractTimestamp(uuid: UUID): Date? {
    try {
        // Extract the most significant bits
        val mostSigBits = uuid.mostSignificantBits

        // Extract the time fields from the UUID
        val timeLow = mostSigBits ushr 32 and 0xFFFFFFFFL
        val timeMid = mostSigBits ushr 16 and 0xFFFFL
        val timeHighAndVersion = mostSigBits and 0x0FFFL // Mask to get the lower 12 bits

        // Reconstruct the timestamp (number of 100-ns intervals since UUID epoch)
        val timestamp = (timeHighAndVersion shl 48) or (timeMid shl 32) or timeLow

        // Adjust the timestamp by subtracting the UUID epoch offset and converting to milliseconds
        val unixTimestamp = (timestamp - UUID_EPOCH_OFFSET) / 10_000 // Convert to milliseconds

        // Convert to Date
        return Date(unixTimestamp)
    } catch (e: Exception) {
        println("Failed to extract timestamp from UUID: ${uuid.toString()}")
        return null
    }
}